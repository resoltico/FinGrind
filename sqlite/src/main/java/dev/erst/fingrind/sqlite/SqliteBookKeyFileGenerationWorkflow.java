package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionService;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Owns early key-file admission and the in-memory lifecycle of generated secret material. */
final class SqliteBookKeyFileGenerationWorkflow {
  private SqliteBookKeyFileGenerationWorkflow() {}

  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteBookKeyFileGenerator.PublicationTransactionServiceFactory transactionServiceFactory) {
    Objects.requireNonNull(transactionServiceFactory, "transactionServiceFactory");
    Path normalizedPath = SqliteBookKeyFileMaterial.normalize(bookKeyFilePath);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedPath);
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    }
    ContractDecision<GeneratedBookKeyFile> admission = validatePublicationAdmission(normalizedPath);
    if (admission != null) {
      return admission;
    }
    byte[] encodedPassphrase = SqliteBookKeyFileMaterial.encodedPassphraseBytes();
    try {
      return publishDecision(normalizedPath, encodedPassphrase, transactionServiceFactory);
    } finally {
      Arrays.fill(encodedPassphrase, (byte) 0);
    }
  }

  private static ContractDecision<GeneratedBookKeyFile> publishDecision(
      Path normalizedPath,
      byte[] encodedPassphrase,
      SqliteBookKeyFileGenerator.PublicationTransactionServiceFactory transactionServiceFactory) {
    try {
      PublicationTransactionService transactions = transactionServiceFactory.open();
      PublicationTransactionArtifact publication =
          new PublicationTransactionArtifact(
              normalizedPath,
              transactions.publish(
                  new PublicationTransactionRequest(
                      List.of(
                          new PublicationTransactionMemberRequest(
                              "book-key",
                              PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                              normalizedPath,
                              PublicationMode.NO_REPLACE_LINK,
                              encodedPassphrase)))));
      return ContractDecision.accepted(
          new GeneratedBookKeyFile(
              publication,
              SqliteBookKeyFileGenerator.GENERATED_ENCODING,
              SqliteBookKeyFileGenerator.GENERATED_ENTROPY_BITS,
              SqliteBookKeyFileSecurity.generatedPermissionsDescriptor(normalizedPath)));
    } catch (PublicationTransactionExecutionException exception) {
      return ContractDecision.rejected(
          ContractErrors.publicationTransactionIncompleteFailure(
              normalizedPath, exception.result(), "--new-book-key-file"));
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(exception.targetPath()));
    } catch (FileAlreadyExistsException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(normalizedPath));
    } catch (PrivateOutputDirectory.Violation exception) {
      return ContractDecision.rejected(invalidPublicationDirectory(normalizedPath));
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to open the publication transaction owner for the FinGrind book key file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    }
  }

  /**
   * Refuses a caller-selected target before creating secret material, without weakening PTx races.
   */
  private static @org.jspecify.annotations.Nullable ContractDecision<GeneratedBookKeyFile>
      validatePublicationAdmission(Path normalizedPath) {
    try {
      SqliteGeneratedSecretTarget.requireAbsent(normalizedPath);
      SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(normalizedPath);
      return null;
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      return ContractDecision.rejected(secretTargetOccupiedFailure(exception.targetPath()));
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to validate the private parent directory for the FinGrind book key file: "
              + SqliteMachinePaths.absoluteValue(normalizedPath),
          exception);
    }
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure secretTargetOccupiedFailure(
      Path normalizedPath) {
    return ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.failureAt(
        normalizedPath,
        "Generated secret target already exists and will not be overwritten.",
        "Choose an absent --new-book-key-file path or remove the existing file yourself before"
            + " retrying.",
        "--new-book-key-file");
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure invalidPublicationDirectory(
      Path normalizedPath) {
    return ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
        normalizedPath,
        "The FinGrind book key file parent must remain an existing real private directory whose"
            + " resolved ancestry resists non-owner substitution.",
        SqliteBookKeyFileSecuritySupport.generalKeyFileHint(),
        "--new-book-key-file");
  }
}
