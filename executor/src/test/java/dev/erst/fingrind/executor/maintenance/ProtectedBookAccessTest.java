package dev.erst.fingrind.executor.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies canonical protected-book access preserves each declared passphrase transport. */
class ProtectedBookAccessTest {
  private static final Path BOOK = Path.of("access", "book.sqlite");
  private static final Path KEY = Path.of("access", "book.key");

  @Test
  void canonicalizesEveryInspectionTransportWithoutIntroducingSourceArtifacts() {
    List<String> calls = new ArrayList<>();
    ProtectedBookMaintenanceStore store = canonicalizingStore(calls);

    ProtectedBookAccess keyFile =
        ProtectedBookAccess.canonicalizeLiveBookAccess(
            store, new ProtectedBookAccess(BOOK, new ProtectedBookPassphraseSource.KeyFile(KEY)));
    ProtectedBookAccess standardInput =
        ProtectedBookAccess.canonicalizeLiveBookAccess(
            store,
            new ProtectedBookAccess(BOOK, ProtectedBookPassphraseSource.StandardInput.INSTANCE));
    ProtectedBookAccess interactivePrompt =
        ProtectedBookAccess.canonicalizeLiveBookAccess(
            store,
            new ProtectedBookAccess(
                BOOK, ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE));

    assertEquals(BOOK.toAbsolutePath().normalize(), keyFile.bookFilePath());
    assertEquals(
        KEY.toAbsolutePath().normalize(),
        ((ProtectedBookPassphraseSource.KeyFile) keyFile.passphraseSource()).bookKeyFilePath());
    assertEquals(
        ProtectedBookPassphraseSource.StandardInput.INSTANCE, standardInput.passphraseSource());
    assertEquals(
        ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE,
        interactivePrompt.passphraseSource());
    assertEquals(
        List.of(
            "normalizeOptionalInspectionArtifact:LIVE_BOOK",
            "normalizeOptionalInspectionArtifact:LIVE_BOOK_KEY_SOURCE",
            "normalizeOptionalInspectionArtifact:LIVE_BOOK",
            "normalizeOptionalInspectionArtifact:LIVE_BOOK"),
        calls);
  }

  @Test
  void canonicalizesEveryLifecycleTransportAndRetainsTheExactWorkflowMembers() {
    List<String> calls = new ArrayList<>();
    ProtectedBookMaintenanceStore store = canonicalizingStore(calls);

    ProtectedBookAccess keyFile =
        ProtectedBookAccess.canonicalizeExistingLiveBookAccess(
            store, new ProtectedBookAccess(BOOK, new ProtectedBookPassphraseSource.KeyFile(KEY)));
    ProtectedBookAccess standardInput =
        ProtectedBookAccess.canonicalizeExistingLiveBookAccess(
            store,
            new ProtectedBookAccess(BOOK, ProtectedBookPassphraseSource.StandardInput.INSTANCE));
    ProtectedBookAccess interactivePrompt =
        ProtectedBookAccess.canonicalizeExistingLiveBookAccess(
            store,
            new ProtectedBookAccess(
                BOOK, ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE));

    assertEquals(2, keyFile.workflowSourceMembers().members().size());
    assertEquals(1, standardInput.workflowSourceMembers().members().size());
    assertEquals(1, interactivePrompt.workflowSourceMembers().members().size());
    assertEquals(
        List.of(
            "normalizeExistingSource:LIVE_BOOK",
            "normalizeExistingSource:LIVE_BOOK_KEY_SOURCE",
            "normalizeExistingSource:LIVE_BOOK",
            "normalizeExistingSource:LIVE_BOOK"),
        calls);
  }

  @Test
  void projectsThePublishedAccessWithoutChangingTheDeclaredKeyTransport() {
    ProtectedBookAccess access =
        new ProtectedBookAccess(BOOK, new ProtectedBookPassphraseSource.KeyFile(KEY));

    BookAccess published = access.toPublished();
    assertEquals(BOOK, published.bookFilePath());
    assertEquals(
        KEY,
        ((BookAccess.PassphraseSource.KeyFile) published.passphraseSource()).bookKeyFilePath());
    assertEquals(access, ProtectedBookAccess.fromPublished(published));
  }

  private static ProtectedBookMaintenanceStore canonicalizingStore(List<String> calls) {
    return (ProtectedBookMaintenanceStore)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {ProtectedBookMaintenanceStore.class},
            (proxy, method, arguments) -> {
              if ("normalizeOptionalInspectionArtifact".equals(method.getName())
                  || "normalizeExistingSource".equals(method.getName())) {
                Path path = (Path) arguments[0];
                ProtectedBookMaintenanceArtifactRole role =
                    (ProtectedBookMaintenanceArtifactRole) arguments[2];
                calls.add(method.getName() + ":" + role);
                return path.toAbsolutePath().normalize();
              }
              throw new AssertionError("Unexpected storage operation: " + method.getName());
            });
  }
}
