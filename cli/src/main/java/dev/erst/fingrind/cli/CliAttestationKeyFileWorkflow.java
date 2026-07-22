package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Executes standalone attestation-credential generation and public identity inspection. */
final class CliAttestationKeyFileWorkflow {
  private static final String CREDENTIAL_FAILURE_HINT =
      "Confirm the encrypted credential and passphrase files are regular readable files and the passphrase is valid UTF-8 and nonempty.";

  private final CliMutationResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;

  CliAttestationKeyFileWorkflow(
      CliMutationResponseWriter responseWriter, CliFailureResponseWriter failureWriter) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
  }

  int generate(
      AttestationCustodian custodian,
      Path keyFilePath,
      Path passphraseFilePath,
      OutputMode outputMode) {
    Path normalizedKeyPath = normalized(keyFilePath);
    if (!hasExistingCredentialParent(normalizedKeyPath)) {
      return invalidCredentialTarget(
          normalizedKeyPath, ProtocolOptions.Attestation.NEW_KEY_FILE, outputMode);
    }
    try {
      AttestationPublicCredential credential =
          switch (Objects.requireNonNull(custodian, "custodian")) {
            case FILE_PKCS8 -> AttestationKeyFiles.create(normalizedKeyPath, passphraseFilePath);
          };
      responseWriter.writeGeneratedAttestationKeyFileResult(
          metadata(normalizedKeyPath, credential), outputMode);
      return 0;
    } catch (FileAlreadyExistsException exception) {
      return occupied(normalizedKeyPath, ProtocolOptions.Attestation.NEW_KEY_FILE, outputMode);
    } catch (IOException | IllegalArgumentException exception) {
      return invalidCredential(
          normalizedKeyPath, ProtocolOptions.Attestation.NEW_KEY_FILE, outputMode);
    }
  }

  int inspect(AttestationCustodian custodian, Path keyFilePath, OutputMode outputMode) {
    Path normalizedKeyPath = normalized(keyFilePath);
    try {
      responseWriter.writeAttestationKeyFileMetadata(
          metadata(
              normalizedKeyPath,
              switch (Objects.requireNonNull(custodian, "custodian")) {
                case FILE_PKCS8 -> AttestationKeyFiles.loadPublicCredential(normalizedKeyPath);
              }),
          outputMode);
      return 0;
    } catch (IOException | IllegalArgumentException exception) {
      return invalidCredential(normalizedKeyPath, ProtocolOptions.Attestation.KEY_FILE, outputMode);
    }
  }

  private int occupied(Path path, String option, OutputMode outputMode) {
    return writeFailure(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
            path,
            "Generated attestation key target already exists and will not be overwritten.",
            "Choose an absent " + option + " path before rerunning the command.",
            option),
        outputMode);
  }

  private int invalidCredential(Path path, String option, OutputMode outputMode) {
    return writeFailure(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
            path,
            "FinGrind could not read or create the selected attestation credential.",
            CREDENTIAL_FAILURE_HINT,
            option),
        outputMode);
  }

  private int invalidCredentialTarget(Path path, String option, OutputMode outputMode) {
    return writeFailure(
        ContractErrors.Descriptor.INVALID_ATTESTATION_KEY_FILE.failureAt(
            path,
            "The new attestation key file requires an existing non-symlink parent directory.",
            "Create and secure the target parent directory yourself, then choose an absent "
                + option
                + " path beneath it.",
            option),
        outputMode);
  }

  private int writeFailure(
      dev.erst.fingrind.contract.runtime.ContractFailure failure, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter, outputMode);
  }

  private static AttestationKeyFileMetadata metadata(
      Path keyFilePath, AttestationPublicCredential credential) {
    return new AttestationKeyFileMetadata(
        keyFilePath,
        Base64.getUrlEncoder().withoutPadding().encodeToString(credential.spki()),
        HexFormat.of().formatHex(credential.keyId()));
  }

  private static Path normalized(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }

  private static boolean hasExistingCredentialParent(Path keyFilePath) {
    Path parent = keyFilePath.getParent();
    return parent != null
        && !Files.isSymbolicLink(parent)
        && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS);
  }
}
