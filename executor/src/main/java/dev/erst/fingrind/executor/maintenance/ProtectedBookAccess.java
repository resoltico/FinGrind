package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Local maintenance access tuple for one protected book path and local passphrase source. */
public record ProtectedBookAccess(
    Path bookFilePath, ProtectedBookPassphraseSource passphraseSource) {
  public ProtectedBookAccess {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
  }

  /** Projects one published book access tuple into the local maintenance access shape. */
  public static ProtectedBookAccess fromPublished(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return new ProtectedBookAccess(
        bookAccess.bookFilePath(),
        ProtectedBookPassphraseSource.fromPublished(bookAccess.passphraseSource()));
  }

  /**
   * Canonicalizes both filesystem-bearing members of a live-book access tuple before it is used.
   *
   * <p>Only a key-file source carries a second filesystem path. Prompt and standard-input sources
   * remain unchanged because they do not name an artifact to admit.
   */
  public static ProtectedBookAccess canonicalizeLiveBookAccess(
      ProtectedBookMaintenanceStore store, ProtectedBookAccess bookAccess) {
    ProtectedBookMaintenanceStore checkedStore = Objects.requireNonNull(store, "store");
    ProtectedBookAccess checkedAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path canonicalBookPath =
        checkedStore.normalizeOptionalInspectionArtifact(
            checkedAccess.bookFilePath(),
            "bookFilePath",
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    ProtectedBookPassphraseSource canonicalPassphraseSource =
        switch (checkedAccess.passphraseSource()) {
          case ProtectedBookPassphraseSource.KeyFile keyFile ->
              new ProtectedBookPassphraseSource.KeyFile(
                  checkedStore.normalizeOptionalInspectionArtifact(
                      keyFile.bookKeyFilePath(),
                      "bookKeyFilePath",
                      ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE));
          case ProtectedBookPassphraseSource.StandardInput standardInput -> standardInput;
          case ProtectedBookPassphraseSource.InteractivePrompt interactivePrompt ->
              interactivePrompt;
        };
    return new ProtectedBookAccess(canonicalBookPath, canonicalPassphraseSource);
  }

  /**
   * Canonicalizes one live-book tuple for a lifecycle mutation that requires every selected source
   * artifact to exist before output-target preparation begins.
   */
  public static ProtectedBookAccess canonicalizeExistingLiveBookAccess(
      ProtectedBookMaintenanceStore store, ProtectedBookAccess bookAccess) {
    ProtectedBookMaintenanceStore checkedStore = Objects.requireNonNull(store, "store");
    ProtectedBookAccess checkedAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path canonicalBookPath =
        checkedStore.normalizeExistingSource(
            checkedAccess.bookFilePath(),
            "bookFilePath",
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    ProtectedBookPassphraseSource canonicalPassphraseSource =
        switch (checkedAccess.passphraseSource()) {
          case ProtectedBookPassphraseSource.KeyFile keyFile ->
              new ProtectedBookPassphraseSource.KeyFile(
                  checkedStore.normalizeExistingSource(
                      keyFile.bookKeyFilePath(),
                      "bookKeyFilePath",
                      ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE));
          case ProtectedBookPassphraseSource.StandardInput standardInput -> standardInput;
          case ProtectedBookPassphraseSource.InteractivePrompt interactivePrompt ->
              interactivePrompt;
        };
    return new ProtectedBookAccess(canonicalBookPath, canonicalPassphraseSource);
  }

  /** Projects this local maintenance access back into the published contract shape. */
  public BookAccess toPublished() {
    return new BookAccess(bookFilePath, passphraseSource.toPublished(), java.util.List.of());
  }

  /**
   * Returns every file-backed source selected for one live-book maintenance workflow.
   *
   * <p>Prompt and standard-input transports contribute no filesystem member. A selected key file is
   * nevertheless a source artifact: it must remain admitted through verification and staging, just
   * like the selected live book.
   */
  public WorkflowSourceMembers workflowSourceMembers() {
    List<WorkflowSourceMember> members = new ArrayList<>();
    members.add(
        new WorkflowSourceMember(bookFilePath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK));
    if (passphraseSource instanceof ProtectedBookPassphraseSource.KeyFile keyFile) {
      members.add(
          new WorkflowSourceMember(
              keyFile.bookKeyFilePath(),
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE));
    }
    return new WorkflowSourceMembers(members);
  }
}
