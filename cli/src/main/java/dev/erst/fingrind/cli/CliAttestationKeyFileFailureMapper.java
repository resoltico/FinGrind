package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Maps attestation-key-file creation failures onto deterministic public failure contracts. */
final class CliAttestationKeyFileFailureMapper {
  private static final String CREDENTIAL_FAILURE_HINT =
      "Confirm the encrypted credential and passphrase files are regular readable files and the"
          + " passphrase is valid UTF-8 and nonempty.";

  private CliAttestationKeyFileFailureMapper() {}

  static ContractFailure invalidArtifactOutputDirectory(Path path, String option) {
    return ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.failureAt(
        Objects.requireNonNull(path, "path"),
        "The attestation key output parent must be an existing real private directory whose"
            + " resolved ancestry resists non-owner substitution.",
        "Choose an existing private output directory with secure resolved ancestry for "
            + Objects.requireNonNull(option, "option")
            + ", then rerun the command.",
        option);
  }

  /**
   * Maps the checked failure set emitted while creating an already-admitted key file.
   *
   * <p>Unknown runtime failures deliberately propagate: only credential and publication facts have
   * a deterministic public contract at this boundary.
   */
  static ContractFailure creationFailure(
      Exception exception, Path canonicalKeyPath, String option) {
    Exception checkedException = Objects.requireNonNull(exception, "exception");
    Path checkedCanonicalKeyPath = Objects.requireNonNull(canonicalKeyPath, "canonicalKeyPath");
    String checkedOption = Objects.requireNonNull(option, "option");
    if (checkedException instanceof PublicationTransactionExecutionException transaction) {
      return ContractErrors.publicationTransactionIncompleteFailure(
          checkedCanonicalKeyPath, transaction.result(), checkedOption);
    }
    if (checkedException instanceof FileAlreadyExistsException) {
      return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
          checkedCanonicalKeyPath,
          "Generated attestation key target already exists and will not be overwritten.",
          "Choose an absent " + checkedOption + " path, then rerun the command.",
          checkedOption);
    }
    if (checkedException instanceof PrivateOutputDirectory.Violation) {
      return invalidArtifactOutputDirectory(checkedCanonicalKeyPath, checkedOption);
    }
    if (checkedException instanceof IOException
        || checkedException instanceof IllegalArgumentException) {
      return invalidCredential(checkedCanonicalKeyPath, checkedOption);
    }
    if (checkedException instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new AssertionError("Key-file creation must fail with IOException or RuntimeException.");
  }

  static ContractFailure invalidCredential(Path path, String option) {
    return ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
        path,
        "FinGrind could not read or create the selected attestation credential.",
        CREDENTIAL_FAILURE_HINT,
        option);
  }
}
