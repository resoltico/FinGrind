package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves receipt export preserves every uncertain publication outcome for caller inspection. */
class AttestationReceiptPublicationFailureTest extends AttestationInspectionServiceTestSupport {
  @Test
  void classifiesEveryReceiptStageAndFinalPublicationFailure() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path outputDirectory = privateOutputDirectory("receipts");
    AttestationVerification verification =
        AttestationVerifier.verifyBook(List.of(genesis(credential)));

    ContractDecision<ExportAttestationReceiptResult> inaccessibleParentDecision =
        publish(
            outputDirectory.resolve("inaccessible-parent.fgar"),
            verification,
            pathAccessFailingCanonicalization(),
            (parent, receipt) -> {
              throw new AssertionError("A path-admission failure must not create a stage.");
            },
            (finalPath, stagedPath) -> {
              throw new AssertionError("A path-admission failure must not create a final link.");
            },
            ignored -> {
              throw new AssertionError("A path-admission failure must not force a directory.");
            });
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
        inaccessibleParentDecision.requireRejected().descriptor());

    Path nonPrivateDirectory = Files.createDirectories(temporaryDirectory.resolve("non-private"));
    Files.setPosixFilePermissions(
        nonPrivateDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ));
    ContractDecision<ExportAttestationReceiptResult> nonPrivateParentDecision =
        publish(
            outputDirectory.resolve("non-private-parent.fgar"),
            verification,
            pathAccessWithCanonicalParent(nonPrivateDirectory),
            (parent, receipt) -> {
              throw new AssertionError("An invalid directory must not create a stage.");
            },
            (finalPath, stagedPath) -> {
              throw new AssertionError("An invalid directory must not create a final link.");
            },
            ignored -> {
              throw new AssertionError("An invalid directory must not force a directory.");
            });
    assertEquals(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY,
        nonPrivateParentDecision.requireRejected().descriptor());

    Path retainedStage = outputDirectory.resolve(".retained-stage.fgar");
    ContractDecision<ExportAttestationReceiptResult> retainedStageDecision =
        publish(
            outputDirectory.resolve("retained-stage.fgar"),
            verification,
            (parent, receipt) -> {
              throw new ArtifactPublicationRetainedStageException(
                  new ArtifactPublicationRetention(retainedStage),
                  new IOException("simulated staged receipt write failure"));
            },
            (finalPath, stagedPath) -> {
              throw new AssertionError("The final link must not run after a staged write failure.");
            },
            ignored -> {
              throw new AssertionError(
                  "The directory force must not run after a staged write failure.");
            });
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
        retainedStageDecision.requireRejected().descriptor());

    ContractDecision<ExportAttestationReceiptResult> stageFailureDecision =
        publish(
            outputDirectory.resolve("stage-failure.fgar"),
            verification,
            (parent, receipt) -> {
              throw new IOException("simulated staged receipt creation failure");
            },
            (finalPath, stagedPath) -> {
              throw new AssertionError("The final link must not run after a staged write failure.");
            },
            ignored -> {
              throw new AssertionError(
                  "The directory force must not run after a staged write failure.");
            });
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
        stageFailureDecision.requireRejected().descriptor());

    ContractDecision<ExportAttestationReceiptResult> occupiedOutputDecision =
        publish(
            outputDirectory.resolve("occupied.fgar"),
            verification,
            (parent, receipt) -> parent.resolve(".occupied-stage.fgar"),
            (finalPath, stagedPath) -> {
              throw new FileAlreadyExistsException(finalPath.toString());
            },
            ignored -> {
              throw new AssertionError("The directory force must not run after a rejected link.");
            });
    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS,
        occupiedOutputDecision.requireRejected().descriptor());

    ContractDecision<ExportAttestationReceiptResult> uncertainLinkDecision =
        publish(
            outputDirectory.resolve("uncertain-link.fgar"),
            verification,
            (parent, receipt) -> parent.resolve(".uncertain-link-stage.fgar"),
            (finalPath, stagedPath) -> {
              throw new IOException("simulated no-replace-link uncertainty");
            },
            ignored -> {
              throw new AssertionError("The directory force must not run after an uncertain link.");
            });
    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
        uncertainLinkDecision.requireRejected().descriptor());

    ContractDecision<ExportAttestationReceiptResult> durabilityFailureDecision =
        publish(
            outputDirectory.resolve("durability-failure.fgar"),
            verification,
            (parent, receipt) -> parent.resolve(".durability-stage.fgar"),
            (finalPath, stagedPath) -> {},
            ignored -> {
              throw new IOException("simulated directory force failure");
            });
    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
        durabilityFailureDecision.requireRejected().descriptor());
  }

  private ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath,
      AttestationVerification verification,
      AttestationReceiptPublicationOperations.ReceiptStageFileOperations stageFileOperations,
      AttestationReceiptPublicationOperations.ReceiptNoReplaceLinkCreator noReplaceLinkCreator,
      AttestationReceiptPublicationOperations.ReceiptDirectoryDurabilityForcer
          directoryDurabilityForcer) {
    return publish(
        receiptPath,
        verification,
        ReceiptArtifactPathAccess.FILE_SYSTEM,
        stageFileOperations,
        noReplaceLinkCreator,
        directoryDurabilityForcer);
  }

  private ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath,
      AttestationVerification verification,
      ReceiptArtifactPathAccess pathAccess,
      AttestationReceiptPublicationOperations.ReceiptStageFileOperations stageFileOperations,
      AttestationReceiptPublicationOperations.ReceiptNoReplaceLinkCreator noReplaceLinkCreator,
      AttestationReceiptPublicationOperations.ReceiptDirectoryDurabilityForcer
          directoryDurabilityForcer) {
    return AttestationReceiptPublicationOperations.publish(
        receiptPath,
        new byte[] {1, 2, 3},
        temporaryDirectory.resolve("book/live.sqlite"),
        verification,
        noReplaceLinkCreator,
        directoryDurabilityForcer,
        pathAccess,
        stageFileOperations);
  }

  private static ReceiptArtifactPathAccess pathAccessWithCanonicalParent(Path canonicalParent) {
    return new ReceiptArtifactPathAccess() {
      @Override
      public boolean isDirectoryNoFollow(Path path) {
        return true;
      }

      @Override
      public BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException {
        throw new AssertionError("Receipt publication does not read final-path attributes.");
      }

      @Override
      public Path toRealPath(Path path) throws IOException {
        return canonicalParent.toRealPath();
      }
    };
  }

  private static ReceiptArtifactPathAccess pathAccessFailingCanonicalization() {
    return new ReceiptArtifactPathAccess() {
      @Override
      public boolean isDirectoryNoFollow(Path path) {
        return true;
      }

      @Override
      public BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException {
        throw new AssertionError("Receipt publication does not read final-path attributes.");
      }

      @Override
      public Path toRealPath(Path path) throws IOException {
        throw new IOException("simulated parent canonicalization failure");
      }
    };
  }
}
