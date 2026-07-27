package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationKeyFileCreation;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Executes standalone attestation-credential generation and public identity inspection. */
final class CliAttestationKeyFileWorkflow {
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
    Path canonicalKeyPath;
    try {
      canonicalKeyPath = canonicalPrivateCredentialPath(normalizedKeyPath);
    } catch (IOException
        | IllegalArgumentException
        | UnsupportedOperationException
        | SecurityException exception) {
      return writeFailure(
          CliAttestationKeyFileFailureMapper.invalidArtifactOutputDirectory(
              normalizedKeyPath, ProtocolOptions.Attestation.NEW_KEY_FILE),
          outputMode);
    }
    try {
      AttestationKeyFileCreation created =
          switch (Objects.requireNonNull(custodian, "custodian")) {
            case FILE_PKCS8 -> AttestationKeyFiles.create(canonicalKeyPath, passphraseFilePath);
          };
      responseWriter.writeGeneratedAttestationKeyFileResult(created, outputMode);
      return 0;
    } catch (IOException | RuntimeException exception) {
      return writeFailure(
          CliAttestationKeyFileFailureMapper.creationFailure(
              exception, canonicalKeyPath, ProtocolOptions.Attestation.NEW_KEY_FILE),
          outputMode);
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
      return writeFailure(
          CliAttestationKeyFileFailureMapper.invalidCredential(
              normalizedKeyPath, ProtocolOptions.Attestation.KEY_FILE),
          outputMode);
    }
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

  private static Path canonicalPrivateCredentialPath(Path keyFilePath) throws IOException {
    Path parent = keyFilePath.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Attestation key file must have a parent directory.");
    }
    Path fileName = keyFilePath.getFileName();
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      PrivateOutputDirectory.requireExistingOwnerOnly(parent);
    }
    Path canonicalParent = parent.toRealPath();
    PrivateOutputDirectory.requireExistingOwnerOnly(canonicalParent);
    return canonicalParent.resolve(fileName);
  }
}
