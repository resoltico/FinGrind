package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationTransactionStoreTest {
  @Test
  void resolvesPlatformSpecificCanonicalRoots() {
    assertEquals(
        Path.of("/state/fingrind/publication-transactions"),
        PublicationTransactionStore.canonicalStoreRoot(
            "Linux", Map.of("XDG_STATE_HOME", "/state"), "/home/ignored"));
    assertEquals(
        Path.of("/home/alice/.local/state/fingrind/publication-transactions"),
        PublicationTransactionStore.canonicalStoreRoot("Linux", Map.of(), "/home/alice"));
    assertEquals(
        Path.of("C:/Users/alice/AppData/Local/FinGrind/publication-transactions"),
        PublicationTransactionStore.canonicalStoreRoot(
            "Windows 11", Map.of("LOCALAPPDATA", "C:/Users/alice/AppData/Local"), "C:/ignored"));
  }

  @Test
  void refusesMissingWindowsStateRoot() {
    assertThrows(
        IllegalStateException.class,
        () -> PublicationTransactionStore.canonicalStoreRoot("Windows", Map.of(), "C:/ignored"));
  }

  @Test
  void createsAndReadmitsOneOwnerOnlyStore(@TempDir Path temporaryDirectory) throws Exception {
    Path plannedStore = temporaryDirectory.resolve("state/fingrind/publication-transactions");

    Path createdStore = PublicationTransactionStore.open(plannedStore);

    assertEquals(createdStore, PublicationTransactionStore.open(plannedStore));
    PrivateOutputDirectory.requireExistingOwnerOnly(createdStore);
  }
}
