package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Owns operator guidance for each deterministic protected-book path-contract rejection. */
final class CliMaintenancePathFailureHint {
  private static final Map<PublicationPathFailure, String> HINTS = hints();

  private CliMaintenancePathFailureHint() {}

  static String forFailure(PublicationPathFailure failure) {
    PublicationPathFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    return Objects.requireNonNull(HINTS.get(checkedFailure), "hint for " + checkedFailure);
  }

  private static Map<PublicationPathFailure, String> hints() {
    Map<PublicationPathFailure, String> hints =
        Map.ofEntries(
            Map.entry(
                PublicationPathFailure.MISSING_PARENT_DIRECTORY,
                "Create and secure the selected parent directory yourself, then choose a path beneath it and rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.PARENT_PATH_COLLISION,
                "Choose a path whose parent chain is made only of real directories, not existing files or symlinks, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
                "Choose a path beneath a parent directory that the owner can traverse and write, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.PARENT_OWNER_ONLY_REQUIRED,
                "Choose a path beneath an owner-only parent directory, or tighten the existing parent directory first, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
                "Choose a regular non-symlink artifact path for this maintenance workflow, then rerun the command."),
            Map.entry(
                PublicationPathFailure.TARGET_OWNER_ONLY_REQUIRED,
                "Tighten the selected artifact to owner-only permissions, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.TARGET_IDENTITY_UNESTABLISHED,
                "Choose protected-book and generated-secret target paths whose distinct filesystem identities can be established, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
                "Choose distinct source artifacts: two selected source roles resolve to the same physical file, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
                "Keep every selected source stable, restore the trustworthy intended source if it changed, then rerun the complete maintenance command."),
            Map.entry(
                PublicationPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
                "Choose a path on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
                "Choose a path on a filesystem that supports atomically creating owner-only FinGrind protocol files, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
                "Choose a path on a filesystem that supports atomic no-replace secret publication, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED,
                "Choose a path on a filesystem that supports atomic no-replace protected-book publication, then rerun the maintenance command."),
            Map.entry(
                PublicationPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED,
                "Choose a path on a filesystem that supports atomic protected-book replacement, then rerun the maintenance command."));
    return requireCompleteHints(hints);
  }

  static Map<PublicationPathFailure, String> requireCompleteHints(
      Map<PublicationPathFailure, String> hints) {
    Map<PublicationPathFailure, String> checkedHints = Objects.requireNonNull(hints, "hints");
    if (!checkedHints.keySet().equals(EnumSet.allOf(PublicationPathFailure.class))) {
      throw new IllegalArgumentException("Every maintenance path failure requires CLI guidance.");
    }
    return checkedHints;
  }
}
