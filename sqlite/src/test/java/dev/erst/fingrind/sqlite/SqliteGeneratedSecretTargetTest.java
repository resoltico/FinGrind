package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the no-replace boundary for newly generated protected-book secrets. */
class SqliteGeneratedSecretTargetTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void requireAbsent_refusesAnOccupiedTargetWithoutChangingIt() throws Exception {
    Path targetPath = Files.writeString(tempDirectory.resolve("occupied.key"), "occupied-secret");

    SqliteGeneratedSecretTargetOccupiedException exception =
        assertThrows(
            SqliteGeneratedSecretTargetOccupiedException.class,
            () -> SqliteGeneratedSecretTarget.requireAbsent(targetPath));

    assertEquals(targetPath, exception.targetPath());
    assertEquals("occupied-secret", Files.readString(targetPath));
  }

  @Test
  void publishRetainingStage_keepsTheOwnedStageUntilItsCallerCompletesThePair() throws Exception {
    Path targetPath = tempDirectory.resolve("retained.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("retained.stage"), "staged-secret");

    publishWithRetainedWitness(targetPath, stagedPath);

    assertTrue(Files.isSameFile(targetPath, stagedPath));
    assertEquals("staged-secret", Files.readString(targetPath));
  }

  @Test
  void publishRetainingStage_translatesAConcurrentTargetClaimAndPreservesTheStagedSecret()
      throws Exception {
    Path targetPath = tempDirectory.resolve("raced.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("raced.stage"), "staged-secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);
    SqliteGeneratedSecretTargetOccupiedException exception;
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Files.writeString(targetPath, "concurrent-secret");
      exception =
          assertThrows(
              SqliteGeneratedSecretTargetOccupiedException.class,
              () -> target.publishRetainingStage(stagedPath, guardedLinkCreator(witnesses)));
    }

    assertEquals(targetPath, exception.targetPath());
    assertInstanceOf(FileAlreadyExistsException.class, exception.getCause());
    assertEquals("concurrent-secret", Files.readString(targetPath));
    assertTrue(Files.exists(stagedPath));
    Files.delete(stagedPath);
  }

  @Test
  void retainedWitness_mapsUnsupportedNoReplaceStorageToTheTypedSecretFailure() throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported.key");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                    (target, staged) -> {
                      throw new FileSystemException(
                          target.toString(), staged.toString(), "Operation not supported");
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    SqliteCallerPathContractException exception =
        Objects.requireNonNull(
            SqlitePublicationCapabilityWitness.callerPathFailure(
                failure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertInstanceOf(FileSystemException.class, failure.getCause());
    assertFalse(Files.exists(targetPath));
    try (var paths = Files.list(tempDirectory)) {
      assertTrue(paths.findAny().isPresent());
    }
  }

  @Test
  void retainedWitnessClassifiesOnlySupportedCapabilityFailureSignals() throws Exception {
    Path atomicTarget = tempDirectory.resolve("atomic.sqlite");
    AtomicMoveNotSupportedException atomicMoveFailure =
        new AtomicMoveNotSupportedException(
            atomicTarget.toString(), atomicTarget.toString(), "injected atomic move refusal");
    SqlitePublicationCapabilityWitness.AcquisitionFailure atomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicTarget)),
                    Files::createLink,
                    (source, target) -> {
                      throw atomicMoveFailure;
                    }));
    SqliteCallerPathContractException typedFailure =
        Objects.requireNonNull(
            SqlitePublicationCapabilityWitness.callerPathFailure(
                atomicFailure, SqliteCallerPathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED));
    assertEquals(
        SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED, typedFailure.pathFailure());
    assertSame(atomicMoveFailure, typedFailure.getCause());

    Path ordinaryTarget = tempDirectory.resolve("ordinary.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure ordinaryFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(ordinaryTarget)),
                    (target, staged) -> {
                      throw new FileSystemException(target.toString(), staged.toString(), null);
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertEquals(
        null,
        SqlitePublicationCapabilityWitness.callerPathFailure(
            ordinaryFailure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));
  }

  @Test
  void retainedWitnessReleasesPreviouslyAcquiredWitnessesWhenALaterWitnessFails() throws Exception {
    Path firstParent = Files.createDirectory(tempDirectory.resolve("first"));
    Path secondParent = Files.createDirectory(tempDirectory.resolve("second"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(firstParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(secondParent);
    java.util.concurrent.atomic.AtomicInteger linkAttempts =
        new java.util.concurrent.atomic.AtomicInteger();

    assertThrows(
        SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
        () ->
            SqlitePublicationCapabilityWitness.acquire(
                java.util.List.of(
                    SqlitePublicationCapabilityWitness.Requirement.noReplace(
                        firstParent.resolve("first.key")),
                    SqlitePublicationCapabilityWitness.Requirement.noReplace(
                        secondParent.resolve("second.key"))),
                (target, staged) -> {
                  if (linkAttempts.incrementAndGet() == 2) {
                    throw new IOException("injected later witness failure");
                  }
                  Files.createLink(target, staged);
                },
                SqliteProtectedBookPublicationSupport::moveReplacing));

    assertEquals(2, linkAttempts.get());
  }

  @Test
  void retainedWitnessReusesCompletePrimitiveEvidenceAfterTheEarlierScopeCloses() throws Exception {
    Path noReplaceTarget = tempDirectory.resolve("reused.key");
    Path replacementTarget = tempDirectory.resolve("reused.sqlite");

    try (SqlitePublicationCapabilityWitness.Set ignored =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget),
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(replacementTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      // Closing the scope releases only its locks; its exact immutable capability facts remain.
    }

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget),
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(replacementTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      witnesses.requireCurrent(
          noReplaceTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      witnesses.requireCurrent(
          replacementTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
    }
  }

  @Test
  void retainedWitnessAcceptsAConcurrentExactNoReplaceCompletion() throws Exception {
    Path targetPath = tempDirectory.resolve("concurrent-exact.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            (completion, source) -> {
              Files.createLink(completion, source);
              throw new FileAlreadyExistsException(completion.toString());
            },
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      witnesses.requireCurrent(
          targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    }
  }

  @Test
  void retainedWitnessFailsClosedOnRetiredRandomProbeResidue() throws Exception {
    Path targetPath = tempDirectory.resolve("legacy-residue.key");
    Files.writeString(
        tempDirectory.resolve(".fingrind-book-no-replace-probe-abandoned"), "retired probe");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));

    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(failure.getCause(), "capability failure cause").getMessage(),
                "capability failure message")
            .contains("Retired random publication-capability probe residue"));
    assertFalse(Files.exists(targetPath));
  }

  @Test
  void retainedWitnessRejectsFinalPublicationChecksAfterItCloses() throws Exception {
    Path targetPath = tempDirectory.resolve("closed.key");
    SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing);
    witnesses.close();
    witnesses.close();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                witnesses.requireCurrent(
                    targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "closed witness message")
            .contains("closed"));
  }

  @Test
  void retainedWitnessPreservesAnUpstreamCallerPathFailure() throws Exception {
    Path targetPath = tempDirectory.resolve("path-failure.key");
    SqliteCallerPathContractException upstream =
        new SqliteCallerPathContractException(
            targetPath,
            SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
            "injected caller path failure");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                    (completion, source) -> {
                      throw upstream;
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    SqliteCallerPathContractException translated =
        Objects.requireNonNull(
            SqlitePublicationCapabilityWitness.callerPathFailure(
                failure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));

    assertEquals(targetPath.toAbsolutePath().normalize(), translated.requestedPath());
    assertEquals(SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED, translated.pathFailure());
    assertSame(upstream, translated.getCause());
  }

  @Test
  void retainedWitnessRefusesOneConcurrentScopeAndReleasesTheCapabilityForLaterUse()
      throws Exception {
    Path targetPath = tempDirectory.resolve("exclusive.key");
    SqlitePublicationCapabilityWitness.Set first =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing);
    try {
      assertThrows(
          SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
          () ->
              SqlitePublicationCapabilityWitness.acquire(
                  java.util.List.of(
                      SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                  Files::createLink,
                  SqliteProtectedBookPublicationSupport::moveReplacing));
    } finally {
      first.close();
    }

    try (SqlitePublicationCapabilityWitness.Set afterRelease =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      afterRelease.requireCurrent(
          targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    }
  }

  @Test
  void retainedWitness_refusesAnUnadmittedSiblingEvenWhenItSharesTheWitnessParent()
      throws Exception {
    Path admittedTarget = tempDirectory.resolve("admitted.key");
    Path siblingTarget = tempDirectory.resolve("sibling.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(admittedTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      IOException exception =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      siblingTarget,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));

      assertTrue(
          Objects.requireNonNull(exception.getMessage(), "exception message")
              .contains("exact target"));
    }
  }

  @Test
  void publishRetainingStage_translatesAnUnsupportedAtomicPrimitiveAndPreservesTheStagedSecret()
      throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported-publish.key");
    Path stagedPath =
        Files.writeString(tempDirectory.resolve("unsupported-publish.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw new FileSystemException(
                          finalPath.toString(), staged.toString(), "Operation not supported");
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertInstanceOf(FileSystemException.class, exception.getCause());
    assertFalse(Files.exists(targetPath));
    assertEquals("secret", Files.readString(stagedPath));
  }

  @Test
  void publication_preservesNonCapabilityFilesystemFailures() throws Exception {
    Path publishTarget = tempDirectory.resolve("publish-failure.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("publish-failure.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(publishTarget);
    FileSystemException publishFailure =
        new FileSystemException(publishTarget.toString(), stagedPath.toString(), null);

    FileSystemException exception =
        assertThrows(
            FileSystemException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw publishFailure;
                    }));

    assertSame(publishFailure, exception);
    assertFalse(Files.exists(publishTarget));
    assertEquals("secret", Files.readString(stagedPath));
  }

  @Test
  void publishRetainingStage_translatesUnsupportedOperations() throws Exception {
    Path targetPath = tempDirectory.resolve("unsupported-operation-publish.key");
    Path stagedPath =
        Files.writeString(tempDirectory.resolve("unsupported-operation.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);
    UnsupportedOperationException unsupported = new UnsupportedOperationException("no hard links");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                target.publishRetainingStage(
                    stagedPath,
                    (finalPath, staged) -> {
                      throw unsupported;
                    }));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED, exception.pathFailure());
    assertSame(unsupported, exception.getCause());
    assertFalse(Files.exists(targetPath));
    assertEquals("secret", Files.readString(stagedPath));
  }

  private static void publishWithRetainedWitness(Path targetPath, Path stagedPath)
      throws java.io.IOException {
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      SqliteGeneratedSecretTarget.requireAbsent(targetPath)
          .publishRetainingStage(stagedPath, guardedLinkCreator(witnesses));
    }
  }

  private static SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator guardedLinkCreator(
      SqlitePublicationCapabilityWitness.Set witnesses) {
    return (finalPath, stagedPath) -> {
      witnesses.requireCurrent(
          finalPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      Files.createLink(finalPath, stagedPath);
    };
  }
}
