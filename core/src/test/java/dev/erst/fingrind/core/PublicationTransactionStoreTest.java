package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies canonical owner-only publication-journal store resolution and admission. */
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
  void refusesBlankStateRootDeclarations() {
    assertThrows(
        IllegalStateException.class,
        () ->
            PublicationTransactionStore.canonicalStoreRoot(
                "Windows", Map.of("LOCALAPPDATA", " "), "C:/ignored"));
    assertEquals(
        Path.of("/home/alice/.local/state/fingrind/publication-transactions"),
        PublicationTransactionStore.canonicalStoreRoot(
            "Linux", Map.of("XDG_STATE_HOME", " "), "/home/alice"));
  }

  @Test
  void createsAndReadmitsOneOwnerOnlyStore(@TempDir Path temporaryDirectory) throws Exception {
    Path plannedStore = temporaryDirectory.resolve("state/fingrind/publication-transactions");

    Path createdStore = PublicationTransactionStore.open(plannedStore);

    assertEquals(createdStore, PublicationTransactionStore.open(plannedStore));
    PrivateOutputDirectory.requireExistingOwnerOnly(createdStore);
  }

  @Test
  void forcesEveryNewStoreEntryThroughItsImmediateParentInCreationOrder(
      @TempDir Path temporaryDirectory) throws Exception {
    Path plannedStore = temporaryDirectory.resolve("state/fingrind/publication-transactions");
    List<Path> forcedDirectories = new ArrayList<>();

    PublicationTransactionStore.open(plannedStore, Path::toRealPath, forcedDirectories::add);

    assertEquals(
        List.of(
            temporaryDirectory,
            temporaryDirectory.resolve("state"),
            temporaryDirectory.resolve("state/fingrind")),
        forcedDirectories);
  }

  @Test
  void failsClosedWhenAnyNewStoreEntryCannotBeMadeDurable(@TempDir Path temporaryDirectory) {
    IOException directoryFailure = new IOException("directory force failed");

    PrivateOutputDirectory.Violation violation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PublicationTransactionStore.open(
                    temporaryDirectory.resolve("state/fingrind/publication-transactions"),
                    Path::toRealPath,
                    ignored -> {
                      throw directoryFailure;
                    }));

    assertSame(directoryFailure, violation.getCause());
  }

  @Test
  void opensTheProcessCanonicalStoreUnderTheTestPrivateHome() throws Exception {
    Path canonicalStore = PublicationTransactionStore.openCanonicalStore();

    assertEquals(canonicalStore, PublicationTransactionStore.openCanonicalStore());
    PrivateOutputDirectory.requireExistingOwnerOnly(canonicalStore);
  }

  @Test
  void failsClosedWhenAnAdmittedStoreCannotBeResolved(@TempDir Path temporaryDirectory)
      throws Exception {
    Path plannedStore = temporaryDirectory.resolve("state/fingrind/publication-transactions");

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            PublicationTransactionStore.open(
                plannedStore,
                ignored -> {
                  throw new java.io.IOException("resolution failed");
                },
                ignored -> {}));
  }

  @Test
  void preservesOnePrivateOutputAdmissionRefusal(@TempDir Path temporaryDirectory)
      throws Exception {
    Path collidingEntry = temporaryDirectory.resolve("publication-transactions");
    Files.writeString(collidingEntry, "not a directory");

    PrivateOutputDirectory.Violation violation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PublicationTransactionStore.open(collidingEntry, Path::toRealPath, ignored -> {}));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, violation.kind());
  }
}
