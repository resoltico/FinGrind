package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.ArtifactPublicationStages;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Covers no-clobber receipt publication and immutable retained-stage evidence. */
class AttestationReceiptPublicationOperationsTest extends AttestationInspectionServiceTestSupport {
  @Test
  void copiesReceiptBytesBeforePathAdmissionCanReenterCallerCode() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("retained");
    Path receiptPath = retainedDirectory.resolve("copied-receipt.fgar");
    byte[] requestedReceipt = new byte[] {1, 2, 3};
    ReceiptArtifactPathAccess mutatingPathAccess =
        new ReceiptArtifactPathAccess() {
          @Override
          public boolean isDirectoryNoFollow(Path path) {
            requestedReceipt[0] = 9;
            return ReceiptArtifactPathAccess.FILE_SYSTEM.isDirectoryNoFollow(path);
          }

          @Override
          public BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException {
            return ReceiptArtifactPathAccess.FILE_SYSTEM.readBasicAttributesNoFollow(path);
          }

          @Override
          public Path toRealPath(Path path) throws IOException {
            return ReceiptArtifactPathAccess.FILE_SYSTEM.toRealPath(path);
          }
        };

    ExportAttestationReceiptResult.Exported result =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            publish(
                    receiptPath,
                    requestedReceipt,
                    bookPath,
                    credential,
                    Files::createLink,
                    ignored -> {},
                    mutatingPathAccess,
                    this::createStage)
                .requireAccepted());

    assertEquals(receiptPath.toRealPath(), result.receiptFilePath());
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(receiptPath));
    assertTrue(Files.isRegularFile(result.retainedStage().retainedStagePath()));
  }

  @Test
  void stageWriteFailureRetainsTheStageAlongsideItsOrdinaryPrimaryFailure() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("stage-failure");
    Path receiptPath = retainedDirectory.resolve("receipt.fgar");

    ContractFailure failure =
        publish(
                receiptPath,
                new byte[] {1, 2, 3},
                bookPath,
                credential,
                Files::createLink,
                ignored -> {},
                ReceiptArtifactPathAccess.FILE_SYSTEM,
                (parent, receipt) -> {
                  Path stage = createStage(parent, receipt);
                  throw new ArtifactPublicationRetainedStageException(
                      new ArtifactPublicationRetention(stage),
                      new IOException("simulated stage write failure"));
                })
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    ArtifactPublicationRetention retention =
        java.util.Objects.requireNonNull(failure.retainedStage());
    assertTrue(Files.isRegularFile(retention.retainedStagePath()));
    assertFalse(Files.exists(receiptPath));
  }

  @Test
  void occupiedReceiptDestinationRetainsTheFreshStageWithoutChangingTheExistingReceipt()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("occupied");
    Path receiptPath = retainedDirectory.resolve("receipt.fgar");
    byte[] existing = new byte[] {9, 8};
    Files.write(receiptPath, existing);

    ContractFailure failure =
        publish(
                receiptPath,
                new byte[] {1, 2, 3},
                bookPath,
                credential,
                Files::createLink,
                ignored -> {},
                ReceiptArtifactPathAccess.FILE_SYSTEM,
                this::createStage)
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS, failure.descriptor());
    ArtifactPublicationRetention retention =
        java.util.Objects.requireNonNull(failure.retainedStage());
    assertTrue(Files.isRegularFile(retention.retainedStagePath()));
    assertArrayEquals(existing, Files.readAllBytes(receiptPath));
  }

  @Test
  void indeterminateLinkRetainsTheStageAndReportsTheCandidateFinalPath() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("link-uncertain");
    Path receiptPath = retainedDirectory.resolve("receipt.fgar");

    ContractFailure failure =
        publish(
                receiptPath,
                new byte[] {1, 2, 3},
                bookPath,
                credential,
                (finalPath, stagedPath) -> {
                  throw new IOException("simulated link failure");
                },
                ignored -> {},
                ReceiptArtifactPathAccess.FILE_SYSTEM,
                this::createStage)
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN, failure.descriptor());
    ContractFailureDetails.ArtifactPublicationOutcomeUncertain details =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class, failure.details());
    assertEquals(canonicalReceiptPath(receiptPath), details.candidateArtifactPath());
    ArtifactPublicationRetention retention =
        java.util.Objects.requireNonNull(details.retainedStage());
    assertEquals(retention, failure.retainedStage());
    assertTrue(Files.isRegularFile(retention.retainedStagePath()));
    assertFalse(Files.exists(receiptPath));
  }

  @Test
  void directoryDurabilityFailureRetainsThePublishedReceiptAndItsStage() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("durability-uncertain");
    Path receiptPath = retainedDirectory.resolve("receipt.fgar");

    ContractFailure failure =
        publish(
                receiptPath,
                new byte[] {1, 2, 3},
                bookPath,
                credential,
                Files::createLink,
                ignored -> {
                  throw new IOException("simulated directory-force failure");
                },
                ReceiptArtifactPathAccess.FILE_SYSTEM,
                this::createStage)
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN, failure.descriptor());
    ContractFailureDetails.ArtifactPublicationDurabilityUncertain details =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class, failure.details());
    assertEquals(receiptPath.toRealPath(), details.publication().publishedArtifactPath());
    assertEquals(details.publication().retention(), failure.retainedStage());
    assertTrue(Files.isRegularFile(receiptPath));
    assertTrue(Files.isRegularFile(details.publication().retention().retainedStagePath()));
  }

  @Test
  void successfulPublicationRetainsTheStageAsTheExactReceiptEvidence() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Path retainedDirectory = privateOutputDirectory("success");
    Path receiptPath = retainedDirectory.resolve("receipt.fgar");
    byte[] receipt = new byte[] {1, 2, 3};

    ExportAttestationReceiptResult.Exported result =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            publish(
                    receiptPath,
                    receipt,
                    bookPath,
                    credential,
                    Files::createLink,
                    ignored -> {},
                    ReceiptArtifactPathAccess.FILE_SYSTEM,
                    this::createStage)
                .requireAccepted());

    assertArrayEquals(receipt, Files.readAllBytes(result.receiptFilePath()));
    assertArrayEquals(receipt, Files.readAllBytes(result.retainedStage().retainedStagePath()));
    assertEquals(receiptPath.toRealPath(), result.receiptFilePath());
  }

  private dev.erst.fingrind.contract.runtime.ContractDecision<ExportAttestationReceiptResult>
      publish(
          Path receiptPath,
          byte[] receipt,
          Path bookPath,
          AttestationMaintenanceTestSupport.CredentialFixture credential,
          AttestationReceiptPublicationOperations.ReceiptNoReplaceLinkCreator linkCreator,
          AttestationReceiptPublicationOperations.ReceiptDirectoryDurabilityForcer directoryForcer,
          ReceiptArtifactPathAccess pathAccess,
          AttestationReceiptPublicationOperations.ReceiptStageFileOperations stageOperations) {
    return AttestationReceiptPublicationOperations.publish(
        receiptPath,
        receipt,
        bookPath,
        AttestationVerifier.verifyBook(List.of(genesis(credential))),
        linkCreator,
        directoryForcer,
        pathAccess,
        stageOperations);
  }

  private Path createStage(Path parent, byte[] bytes) throws IOException {
    return ArtifactPublicationStages.createAndWrite(parent, ".test-receipt-", ".fgar", bytes);
  }

  private static Path canonicalReceiptPath(Path receiptPath) throws IOException {
    Path normalized = receiptPath.toAbsolutePath().normalize();
    return Objects.requireNonNull(normalized.getParent(), "receipt parent")
        .toRealPath()
        .resolve(normalized.getFileName());
  }
}
