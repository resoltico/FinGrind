package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionState;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Covers receipt delegation to the journal transaction owner. */
class AttestationReceiptPublicationOperationsTest extends AttestationInspectionServiceTestSupport {
  @Test
  void publishesOneReceiptMemberThroughTheTransactionOwner() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path receiptPath = privateOutputDirectory("receipt-output").resolve("receipt.fgar");
    RecordingTransactions transactions = new RecordingTransactions();

    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            publish(receiptPath, credential, transactions).requireAccepted());

    PublicationTransactionRequest request =
        Objects.requireNonNull(transactions.request, "publication request");
    assertEquals(canonicalFinalPath(receiptPath), exported.receiptFilePath());
    assertEquals(transactions.successfulResult, exported.publicationTransaction());
    assertEquals(1, request.members().size());
    assertEquals("attestation-receipt", request.members().getFirst().memberId());
    assertEquals(
        PublicationTransactionMemberRole.ATTESTATION_RECEIPT, request.members().getFirst().role());
    assertEquals(PublicationMode.NO_REPLACE_LINK, request.members().getFirst().publicationMode());
    assertEquals(canonicalFinalPath(receiptPath), request.members().getFirst().finalPath());
    assertTrue(request.members().getFirst().toString().contains("secretPayload=<redacted>"));
  }

  @Test
  void reportsAnIncompleteTransactionWithoutRetainedStageEvidence() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path receiptPath = privateOutputDirectory("receipt-incomplete").resolve("receipt.fgar");
    RecordingTransactions transactions = new RecordingTransactions();
    PublicationTransactionResult incomplete =
        new PublicationTransactionResult(
            new PublicationTransactionId("fedcba9876543210fedcba9876543210"),
            PublicationTransactionState.COMMIT_UNCERTAIN,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.INCOMPLETE));
    transactions.failure =
        new PublicationTransactionExecutionException(incomplete, new IOException("commit unknown"));

    var failure = publish(receiptPath, credential, transactions).requireRejected();

    assertEquals(
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE, failure.descriptor());
    assertNull(failure.retainedStage());
    ContractFailureDetails.PublicationTransactionIncomplete details =
        assertInstanceOf(
            ContractFailureDetails.PublicationTransactionIncomplete.class, failure.details());
    assertEquals(canonicalFinalPath(receiptPath), details.candidateArtifactPath());
    assertEquals(incomplete, details.transactionResult());
  }

  @Test
  void reportsNoReplaceFinalCollisionAsOutputAlreadyExists() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path receiptPath = privateOutputDirectory("receipt-collision").resolve("receipt.fgar");
    RecordingTransactions transactions = new RecordingTransactions();
    transactions.failure = new FileAlreadyExistsException(receiptPath.toString());

    var failure = publish(receiptPath, credential, transactions).requireRejected();

    assertEquals(ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS, failure.descriptor());
    assertEquals(canonicalFinalPath(receiptPath), Objects.requireNonNull(failure.paths()).path());
    assertNull(failure.retainedStage());
  }

  @Test
  void rejectsAnOutputAliasBeforeOpeningTheTransactionOwner() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path physicalRoot = privateOutputDirectory("physical-receipts");
    Path alias = temporaryDirectory.resolve("receipt-output-alias");
    java.nio.file.Files.createSymbolicLink(alias, physicalRoot);
    RecordingTransactions transactions = new RecordingTransactions();

    var failure =
        publish(alias.resolve("receipt.fgar"), credential, transactions).requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY, failure.descriptor());
    assertEquals(null, transactions.request);
  }

  @Test
  void rejectsAnExistingOutputDirectoryThatIsNotOwnerOnly() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path insecureOutputDirectory = privateOutputDirectory("insecure-receipt-output");
    Files.setPosixFilePermissions(
        insecureOutputDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE));
    RecordingTransactions transactions = new RecordingTransactions();

    var failure =
        publish(insecureOutputDirectory.resolve("receipt.fgar"), credential, transactions)
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY, failure.descriptor());
    assertNull(transactions.request);
  }

  @Test
  void reportsFilesystemAdmissionAndTransactionStartupFailuresWithoutPublishing()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path receiptPath = privateOutputDirectory("receipt-admission-output").resolve("receipt.fgar");
    ReceiptArtifactPathAccess unreadableCanonicalPath =
        new ReceiptArtifactPathAccess() {
          @Override
          public boolean isDirectoryNoFollow(Path path) {
            return true;
          }

          @Override
          public java.nio.file.attribute.BasicFileAttributes readBasicAttributesNoFollow(
              Path path) {
            throw new AssertionError("Receipt publication does not inspect attributes directly.");
          }

          @Override
          public Path toRealPath(Path path) throws IOException {
            throw new IOException("canonical parent unavailable");
          }
        };

    var admissionFailure =
        publish(
                receiptPath,
                credential,
                () -> {
                  throw new AssertionError(
                      "Transaction owner must not open after admission failure.");
                },
                unreadableCanonicalPath)
            .requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, admissionFailure.descriptor());

    var transactionStartupFailure =
        publish(
                receiptPath,
                credential,
                () -> {
                  throw new IOException("transaction journal unavailable");
                },
                ReceiptArtifactPathAccess.FILE_SYSTEM)
            .requireRejected();
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, transactionStartupFailure.descriptor());
  }

  private dev.erst.fingrind.contract.runtime.ContractDecision<ExportAttestationReceiptResult>
      publish(
          Path receiptPath,
          AttestationMaintenanceTestSupport.CredentialFixture credential,
          RecordingTransactions transactions) {
    return publish(
        receiptPath, credential, () -> transactions, ReceiptArtifactPathAccess.FILE_SYSTEM);
  }

  private dev.erst.fingrind.contract.runtime.ContractDecision<ExportAttestationReceiptResult>
      publish(
          Path receiptPath,
          AttestationMaintenanceTestSupport.CredentialFixture credential,
          AttestationReceiptPublicationOperations.ReceiptTransactionServiceFactory
              transactionServiceFactory,
          ReceiptArtifactPathAccess pathAccess) {
    return AttestationReceiptPublicationOperations.publish(
        receiptPath,
        new byte[] {1, 2, 3},
        temporaryDirectory.resolve("book/live.sqlite"),
        AttestationVerifier.verifyBook(List.of(genesis(credential))),
        transactionServiceFactory,
        pathAccess);
  }

  private static Path canonicalFinalPath(Path requestedFinalPath) throws IOException {
    Path normalized = requestedFinalPath.toAbsolutePath().normalize();
    return Objects.requireNonNull(normalized.getParent(), "final parent")
        .toRealPath()
        .resolve(normalized.getFileName());
  }

  /** Transaction service double that cannot observe or manipulate a secret stage pathname. */
  private static final class RecordingTransactions implements PublicationTransactionService {
    final PublicationTransactionResult successfulResult =
        new PublicationTransactionResult(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE));
    @Nullable PublicationTransactionRequest request;
    @Nullable IOException failure;

    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request)
        throws IOException {
      this.request = Objects.requireNonNull(request, "request");
      if (failure != null) {
        throw failure;
      }
      return successfulResult;
    }

    @Override
    public dev.erst.fingrind.core.PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) {
      throw new AssertionError("Receipt export must not reserve an external stage.");
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        dev.erst.fingrind.core.PublicationTransactionStageReservation reservation) {
      throw new AssertionError("Receipt export must not admit an external stage.");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
      throw new AssertionError("Receipt export must not recover a transaction.");
    }

    @Override
    public dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      throw new AssertionError("Receipt export must not recover a transaction receipt.");
    }
  }
}
