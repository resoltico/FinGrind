package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies exact staged-artifact evidence and fresh proof before transaction-owned residue removal.
 */
class PublicationTransactionArtifactFilesTest {
  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void stagesLinksRevalidatesAndRemovesOnlyTheTransactionOwnedStage(
      @TempDir Path temporaryDirectory) throws Exception {
    Path directory = privateDirectory(temporaryDirectory);
    byte[] bytes = "transaction-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Path stage = directory.resolve(".stage");
    Path finalPath = directory.resolve("final.fg");

    PublicationTransactionStagedArtifact staged =
        PublicationTransactionArtifactFiles.createStage(stage, bytes);
    PublicationTransactionArtifactFiles.createNoReplaceHardLink(finalPath, stage);
    PublicationTransactionFinalizedArtifact finalized =
        PublicationTransactionArtifactFiles.finalEvidence(finalPath);
    PublicationTransactionMember member = member(finalPath, staged, finalized);

    assertEquals(CryptographicPrimitives.sha256Hex(bytes), staged.sha256Hex());
    assertEquals(staged.physicalIdentity(), finalized.physicalIdentity());
    PrivateOutputFile.requireExistingOwnerOnly(stage, PrivateOutputFile.Access.READ_ONLY);
    PublicationTransactionArtifactFiles.requireSafeResidueRemoval(member);
    PublicationTransactionArtifactFiles.deleteStageAfterFreshValidation(member);

    assertTrue(Files.notExists(stage));
    assertEquals("transaction-secret", Files.readString(finalPath));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void refusesToRemoveAStageWhenTheFinalIsNotTheAuthenticatedHardLink(
      @TempDir Path temporaryDirectory) throws Exception {
    Path directory = privateDirectory(temporaryDirectory);
    PublicationTransactionStagedArtifact staged =
        PublicationTransactionArtifactFiles.createStage(
            directory.resolve(".stage"), "first".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Path unrelatedFinal = directory.resolve("final.fg");
    PublicationTransactionArtifactFiles.createStage(
        unrelatedFinal, "second".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    PublicationTransactionMember member =
        member(
            unrelatedFinal,
            staged,
            PublicationTransactionArtifactFiles.finalEvidence(unrelatedFinal));

    assertThrows(
        IOException.class,
        () -> PublicationTransactionArtifactFiles.requireSafeResidueRemoval(member));
    assertTrue(Files.exists(staged.stagePath()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void refusesToRemoveAStageWhenAnyRecordedArtifactEvidenceDiffers(@TempDir Path temporaryDirectory)
      throws Exception {
    Path directory = privateDirectory(temporaryDirectory);
    byte[] bytes = "transaction-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Path finalPath = directory.resolve("final.fg");
    PublicationTransactionStagedArtifact staged =
        PublicationTransactionArtifactFiles.createStage(directory.resolve(".stage"), bytes);
    PublicationTransactionArtifactFiles.createNoReplaceHardLink(finalPath, staged.stagePath());
    PublicationTransactionFinalizedArtifact finalized =
        PublicationTransactionArtifactFiles.finalEvidence(finalPath);

    assertResidueRejected(
        member(
            finalPath,
            new PublicationTransactionStagedArtifact(
                staged.stagePath(), "unexpected-stage-identity", staged.sha256Hex()),
            finalized));
    assertResidueRejected(
        member(
            finalPath,
            new PublicationTransactionStagedArtifact(
                staged.stagePath(), staged.physicalIdentity(), "0".repeat(64)),
            finalized));
    assertResidueRejected(
        member(
            finalPath,
            staged,
            new PublicationTransactionFinalizedArtifact(
                "unexpected-final-identity", finalized.sha256Hex())));
    assertResidueRejected(
        member(
            finalPath,
            staged,
            new PublicationTransactionFinalizedArtifact(
                finalized.physicalIdentity(), "0".repeat(64))));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsChangedCurrentStageAndFinalEvidence(@TempDir Path temporaryDirectory)
      throws Exception {
    Path directory = privateDirectory(temporaryDirectory);
    Path finalPath = directory.resolve("final.fg");
    PublicationTransactionStagedArtifact staged =
        PublicationTransactionArtifactFiles.createStage(
            directory.resolve(".stage"),
            "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    PublicationTransactionArtifactFiles.createNoReplaceHardLink(finalPath, staged.stagePath());
    PublicationTransactionFinalizedArtifact finalized =
        PublicationTransactionArtifactFiles.finalEvidence(finalPath);

    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionArtifactFiles.requireCurrentStageEvidence(
                member(
                    finalPath,
                    new PublicationTransactionStagedArtifact(
                        staged.stagePath(), "unexpected", staged.sha256Hex()),
                    finalized)));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionArtifactFiles.requireCurrentFinalEvidence(
                member(
                    finalPath,
                    staged,
                    new PublicationTransactionFinalizedArtifact(
                        "unexpected", finalized.sha256Hex()))));

    PublicationTransactionArtifactFiles.deleteStageAfterFreshValidation(
        member(finalPath, staged, finalized));
    assertEquals(
        Optional.empty(),
        PublicationTransactionArtifactFiles.evidenceIfPresent(staged.stagePath()));
  }

  @Test
  void rejectsStageWritesThatCannotMakeProgress() throws IOException {
    IOException exception;
    try (WritableByteChannel stalledChannel =
        new WritableByteChannel() {
          @Override
          public int write(ByteBuffer source) {
            return 0;
          }

          @Override
          public boolean isOpen() {
            return true;
          }

          @Override
          public void close() {}
        }) {
      exception =
          assertThrows(
              IOException.class,
              () ->
                  PublicationTransactionArtifactFiles.writeExactly(stalledChannel, new byte[] {1}));
    }

    assertEquals(
        "FinGrind could not write the complete transaction-owned stage.", exception.getMessage());
  }

  private static void assertResidueRejected(PublicationTransactionMember member) {
    IOException exception =
        assertThrows(
            IOException.class,
            () -> PublicationTransactionArtifactFiles.requireSafeResidueRemoval(member));
    assertEquals(
        "Publication transaction residue no longer matches its authenticated staged and final evidence.",
        exception.getMessage());
  }

  private static PublicationTransactionMember member(
      Path finalPath,
      PublicationTransactionStagedArtifact staged,
      PublicationTransactionFinalizedArtifact finalized)
      throws IOException {
    return new PublicationTransactionMember(
        "pdf-report",
        PublicationTransactionMemberRole.PDF_REPORT,
        finalPath,
        staged.stagePath(),
        PrivateOutputDirectory.physicalObjectIdentity(
            java.util.Objects.requireNonNull(finalPath.getParent(), "final parent")),
        PublicationMode.NO_REPLACE_LINK,
        PublicationTransactionMemberProgress.COMMITTED,
        Optional.of(staged),
        Optional.of(finalized));
  }

  private static Path privateDirectory(Path temporaryDirectory) throws IOException {
    Set<PosixFilePermission> ownerOnly =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    Files.setPosixFilePermissions(temporaryDirectory, ownerOnly);
    Path directory = temporaryDirectory.resolve("publication");
    Files.createDirectory(
        directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(ownerOnly));
    return directory;
  }
}
