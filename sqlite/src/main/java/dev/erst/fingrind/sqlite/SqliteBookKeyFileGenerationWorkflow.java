package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Owns early key-file admission and the in-memory lifecycle of generated secret material. */
final class SqliteBookKeyFileGenerationWorkflow {
  private SqliteBookKeyFileGenerationWorkflow() {}

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      SqliteBookKeyFileGenerator.ParentDirectoryForcer parentDirectoryForcer,
      SqliteBookKeyFileGenerator.RetainedStageCreator retainedStageCreator,
      SqliteBookKeyFileGenerator.PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer) {
    Objects.requireNonNull(finalLinkCreator, "finalLinkCreator");
    Objects.requireNonNull(parentDirectoryForcer, "parentDirectoryForcer");
    Objects.requireNonNull(retainedStageCreator, "retainedStageCreator");
    Objects.requireNonNull(capabilityWitnessAcquirer, "capabilityWitnessAcquirer");
    Path normalizedPath = SqliteBookKeyFileMaterial.normalize(bookKeyFilePath);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    }
    byte[] encodedPassphrase = SqliteBookKeyFileMaterial.encodedPassphraseBytes();
    try {
      return SqliteBookKeyFilePublicationWorkflow.publishDecision(
          normalizedPath,
          encodedPassphrase,
          finalLinkCreator,
          parentDirectoryForcer,
          retainedStageCreator,
          capabilityWitnessAcquirer);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }
}
