package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns public failure presentation for caller-controlled live-book and live-key paths. */
public final class ProtectedBookLiveAccessPathFailures {
  private static final String BOOK_FILE_HINT =
      "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target a missing private directory so FinGrind can create it securely, then rerun the command.";
  private static final String BOOK_KEY_FILE_HINT =
      "Create a private owner-only parent directory yourself, then choose a regular non-symlink key file path beneath it and rerun the command.";

  private ProtectedBookLiveAccessPathFailures() {}

  /** Creates the canonical public refusal for one selected protected-book path. */
  public static ContractFailure bookFile(
      Path artifactPath, ProtectedPublicationPathFailure pathFailure, @Nullable String argument) {
    return ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH.failureAt(
        Objects.requireNonNull(artifactPath, "artifactPath"),
        Objects.requireNonNull(pathFailure, "pathFailure").bookFileMessage(),
        BOOK_FILE_HINT,
        argument);
  }

  /** Creates the canonical public refusal for one selected protected-book key-file path. */
  public static ContractFailure bookKeyFile(
      Path artifactPath, ProtectedPublicationPathFailure pathFailure, @Nullable String argument) {
    return ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
        Objects.requireNonNull(artifactPath, "artifactPath"),
        Objects.requireNonNull(pathFailure, "pathFailure").bookKeyFileMessage(),
        BOOK_KEY_FILE_HINT,
        argument);
  }
}
