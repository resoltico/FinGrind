package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests deterministic rollback when no-replace secret publication cannot discard its stage. */
class SqliteNoReplaceSecretPublicationTest {
  @TempDir Path tempDirectory;

  @Test
  void publishRetainingStage_keepsBothNamesLinkedUntilThePairOwnerDiscardsItsStage()
      throws IOException {
    Path stagedPath = Files.writeString(tempDirectory.resolve("staged.key"), "staged-secret");
    Path finalPath = tempDirectory.resolve("final.key");

    SqliteProtectedBookPublicationSupport.publishRetainingStage(stagedPath, finalPath);

    assertEquals("staged-secret", Files.readString(finalPath));
    assertTrue(Files.isSameFile(finalPath, stagedPath));
  }

  @Test
  void publishAbsent_discardsTheStageAfterACompletedNoReplacePublication() throws IOException {
    Path stagedPath = tempDirectory.resolve("staged.key");
    Path finalPath = tempDirectory.resolve("final.key");
    List<Path> deletedPaths = new ArrayList<>();

    SqliteProtectedBookPublicationSupport.publishAbsent(
        stagedPath,
        finalPath,
        (target, staged) -> assertEquals(finalPath, target),
        deletedPaths::add,
        path -> {
          throw new AssertionError("The final target must not be rolled back after a success.");
        });

    assertEquals(List.of(stagedPath), deletedPaths);
  }

  @Test
  void
      removePublishedSecretIfOwned_distinguishesAbsentOwnedForeignAndFailsClosedOnIncompletePublications()
          throws IOException {
    Path absentFinalPath = tempDirectory.resolve("absent.key");
    Path absentStagePath = Files.writeString(tempDirectory.resolve("absent.stage"), "secret");
    SqliteOwnedStagedArtifact absentStage =
        SqliteOwnedStagedArtifact.recordExisting(absentFinalPath, absentStagePath);
    assertTrue(
        SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
            absentFinalPath, absentStage, "test absent publication"));
    absentStage.discard();

    Path ownedFinalPath = tempDirectory.resolve("owned.key");
    Path ownedStagePath = Files.writeString(tempDirectory.resolve("owned.stage"), "secret");
    SqliteOwnedStagedArtifact ownedStage =
        SqliteOwnedStagedArtifact.recordExisting(ownedFinalPath, ownedStagePath);
    Files.createLink(ownedFinalPath, ownedStagePath);
    assertTrue(
        SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
            ownedFinalPath, ownedStage, "test owned publication"));
    assertFalse(Files.exists(ownedFinalPath));
    ownedStage.discard();

    Path foreignFinalPath = Files.writeString(tempDirectory.resolve("foreign.key"), "foreign");
    Path foreignStagePath = Files.writeString(tempDirectory.resolve("foreign.stage"), "secret");
    SqliteOwnedStagedArtifact foreignStage =
        SqliteOwnedStagedArtifact.recordExisting(foreignFinalPath, foreignStagePath);
    assertFalse(
        SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
            foreignFinalPath, foreignStage, "test foreign publication"));
    assertEquals("foreign", Files.readString(foreignFinalPath));
    foreignStage.discard();

    Path incompleteFinalPath = Files.writeString(tempDirectory.resolve("incomplete.key"), "secret");
    Path incompleteStagePath =
        Files.writeString(tempDirectory.resolve("incomplete.stage"), "secret");
    SqliteOwnedStagedArtifact incompleteStage =
        SqliteOwnedStagedArtifact.recordExisting(incompleteFinalPath, incompleteStagePath);
    IOException comparisonFailure = new IOException("same-file comparison failed");
    IllegalStateException incompletePublicationFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
                    incompleteFinalPath,
                    incompleteStage,
                    "test incomplete publication",
                    (firstPath, secondPath) -> {
                      throw comparisonFailure;
                    },
                    Files::delete));
    assertSame(comparisonFailure, incompletePublicationFailure.getCause());
    assertEquals("secret", Files.readString(incompleteFinalPath));
    incompleteStage.discard();
  }

  @Test
  void recoveryFilesystemOperations_translateSameFileAndDeletionFailures() throws IOException {
    Path finalPath = Files.writeString(tempDirectory.resolve("recovery.key"), "secret");
    Path stagedPath = Files.writeString(tempDirectory.resolve("recovery.stage"), "secret");
    IOException comparisonFailure = new IOException("same-file comparison failed");

    IllegalStateException sameFileFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookPublicationRecovery.isSameOwnedStage(
                    finalPath,
                    stagedPath,
                    (firstPath, secondPath) -> {
                      throw comparisonFailure;
                    }));
    assertSame(comparisonFailure, sameFileFailure.getCause());
    assertFalse(
        SqliteProtectedBookPublicationRecovery.isSameOwnedStage(
            finalPath, tempDirectory.resolve("missing-recovery.stage")));

    IOException deletionFailure = new IOException("generated-secret deletion failed");
    IllegalStateException deleteFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookPublicationRecovery.removeRecoveredSecret(
                    finalPath,
                    path -> {
                      throw deletionFailure;
                    }));
    assertSame(deletionFailure, deleteFailure.getCause());
  }

  @Test
  void discardStage_surfacesAnAlteredOwnedRecordInsteadOfSuppressingTheCleanupFailure()
      throws IOException {
    Path finalPath = tempDirectory.resolve("altered.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("altered.stage"), "secret");
    SqliteOwnedStagedArtifact stagedArtifact =
        SqliteOwnedStagedArtifact.recordExisting(finalPath, stagedPath);
    Path recordPath = ownedRecordPath(tempDirectory);
    Files.delete(recordPath);
    Files.createDirectory(recordPath);
    Files.writeString(recordPath.resolve("blocker"), "altered");

    assertThrows(IllegalStateException.class, stagedArtifact::discard);

    assertFalse(Files.exists(stagedPath));
    assertTrue(Files.isDirectory(recordPath));
  }

  @Test
  void publishAbsent_removesThePublishedTargetWhenStageCleanupFails() {
    Path stagedPath = Path.of("staged.key");
    Path finalPath = Path.of("final.key");
    IOException cleanupFailure = new IOException("staged cleanup failed");
    List<Path> deletedPaths = new ArrayList<>();

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                SqliteProtectedBookPublicationSupport.publishAbsent(
                    stagedPath,
                    finalPath,
                    (target, staged) -> assertEquals(finalPath, target),
                    path -> {
                      throw cleanupFailure;
                    },
                    deletedPaths::add));

    assertSame(cleanupFailure, exception);
    assertEquals(List.of(finalPath), deletedPaths);
  }

  @Test
  void publishAbsent_preservesBothFailuresWhenTargetRollbackAlsoFails() {
    Path stagedPath = Path.of("staged.key");
    Path finalPath = Path.of("final.key");
    IOException cleanupFailure = new IOException("staged cleanup failed");
    IOException rollbackFailure = new IOException("target rollback failed");

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                SqliteProtectedBookPublicationSupport.publishAbsent(
                    stagedPath,
                    finalPath,
                    (target, staged) -> {},
                    path -> {
                      throw cleanupFailure;
                    },
                    path -> {
                      throw rollbackFailure;
                    }));

    assertSame(cleanupFailure, exception);
    assertArrayEquals(new Throwable[] {rollbackFailure}, exception.getSuppressed());
  }

  @Test
  void resetStagedSecretFile_wrapsFilesystemFailureWithTheStagingContext() {
    IOException failure = new IOException("stage reset failed");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookStagingFiles.resetStagedSecretFile(
                    Path.of("staged.key"),
                    path -> {
                      throw failure;
                    }));

    assertSame(failure, exception.getCause());
  }

  private static Path ownedRecordPath(Path parent) throws IOException {
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(path -> path.getFileName().toString().endsWith(".owner"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Expected one owned-stage record fixture."));
    }
  }
}
