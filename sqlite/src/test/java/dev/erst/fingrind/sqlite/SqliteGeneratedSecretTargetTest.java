package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
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
  void witnessKeyEqualityUsesOnlyTheCanonicalParentFingerprintAndPrimitive() {
    Path parent = tempDirectory.resolve("witness-key-parent");
    SqlitePublicationCapabilityWitness.WitnessKey first =
        new SqlitePublicationCapabilityWitness.WitnessKey(
            parent, "canonical-parent", SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);

    assertEquals(
        first,
        new SqlitePublicationCapabilityWitness.WitnessKey(
            parent.resolve("alternate-spelling"),
            "canonical-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
    assertNotEquals(first, "not a witness key");
    assertNotEquals(
        first,
        new SqlitePublicationCapabilityWitness.WitnessKey(
            parent, "other-parent", SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
    assertNotEquals(
        first,
        new SqlitePublicationCapabilityWitness.WitnessKey(
            parent,
            "canonical-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
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
  void publishRetainingStage_preservesAnUnclassifiedFilesystemFailure() throws Exception {
    Path targetPath = tempDirectory.resolve("unclassified-filesystem-failure.key");
    Path stagedPath = Files.writeString(tempDirectory.resolve("unclassified.stage"), "secret");
    SqliteGeneratedSecretTarget target = SqliteGeneratedSecretTarget.requireAbsent(targetPath);
    FileSystemException failure =
        new FileSystemException(targetPath.toString(), stagedPath.toString(), null);

    FileSystemException thrown =
        assertThrows(
            FileSystemException.class,
            () -> target.publishRetainingStage(stagedPath, (ignoredTarget, ignoredStage) -> {
              throw failure;
            }));

    assertSame(failure, thrown);
    assertTrue(Files.exists(stagedPath));
    assertFalse(Files.exists(targetPath));
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

    Path deniedTarget = tempDirectory.resolve("denied.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure deniedFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(deniedTarget)),
                    (target, staged) -> {
                      throw new FileSystemException(target.toString(), staged.toString(), "Permission denied");
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertEquals(
        null,
        SqlitePublicationCapabilityWitness.callerPathFailure(
            deniedFailure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));

    Path ioFailureTarget = tempDirectory.resolve("io-failure.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure ioFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(ioFailureTarget)),
                    (target, staged) -> {
                      throw new IOException("injected ordinary I/O failure");
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertEquals(
        null,
        SqlitePublicationCapabilityWitness.callerPathFailure(
            ioFailure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));

    Path ordinaryAtomicTarget = tempDirectory.resolve("ordinary-atomic.sqlite");
    SqlitePublicationCapabilityWitness.AcquisitionFailure ordinaryAtomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                            ordinaryAtomicTarget)),
                    Files::createLink,
                    (source, target) -> {
                      throw new FileSystemException(
                          source.toString(), target.toString(), "Permission denied");
                    }));
    assertEquals(
        null,
        SqlitePublicationCapabilityWitness.callerPathFailure(
            ordinaryAtomicFailure,
            SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED));
  }

  @Test
  void retainedWitnessLeavesUnsupportedPrimitiveSignalsOpaqueUnlessTheirExactPrimitiveSupportsThem()
      throws Exception {
    Path targetPath = tempDirectory.resolve("opaque-atomic-move.key");
    AtomicMoveNotSupportedException atomicMoveFailure =
        new AtomicMoveNotSupportedException(
            targetPath.toString(), targetPath.toString(), "injected primitive refusal");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                    (completion, source) -> {
                      throw atomicMoveFailure;
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));

    assertEquals(
        null,
        SqlitePublicationCapabilityWitness.callerPathFailure(
            failure, SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED));
  }

  @Test
  void retainedWitnessReportsAnInvalidRootTargetAsTheExactAcquisitionRequirement() {
    SqlitePublicationCapabilityWitness.Requirement requirement =
        SqlitePublicationCapabilityWitness.Requirement.noReplace(Path.of("/"));

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(requirement),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));

    assertEquals(requirement, failure.requirement());
    assertInstanceOf(NullPointerException.class, failure.getCause());
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
    for (String retiredPrefix :
        java.util.List.of(
            ".fingrind-book-no-replace-probe-",
            ".fingrind-no-replace-probe-",
            ".fingrind-book-replace-probe-")) {
      Path parent = Files.createDirectory(tempDirectory.resolve("legacy-" + retiredPrefix.hashCode()));
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
      Path targetPath = parent.resolve("legacy-residue.key");
      Files.writeString(parent.resolve(retiredPrefix + "abandoned"), "retired probe");

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
  }

  @Test
  void retainedWitnessReportsAContendedParentBeforeItWritesProtocolEvidence() throws Exception {
    Path holderTarget = tempDirectory.resolve("busy-holder.sqlite");
    Path witnessTarget = tempDirectory.resolve("busy-witness.key");
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    Thread holder =
        new Thread(
            () -> {
              try (SqliteHeldLease ignored =
                  (SqliteHeldLease)
                      SqliteBookMaintenanceLease.acquire(
                          holderTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET)) {
                acquired.countDown();
                release.await();
              } catch (Throwable failure) {
                holderFailure.set(failure);
              }
            });
    holder.start();
    acquired.await();
    try {
      SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
          assertThrows(
              SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
              () ->
                  SqlitePublicationCapabilityWitness.acquire(
                      java.util.List.of(
                          SqlitePublicationCapabilityWitness.Requirement.noReplace(witnessTarget)),
                      Files::createLink,
                      SqliteProtectedBookPublicationSupport::moveReplacing));
      assertTrue(
          Objects.requireNonNull(
                  Objects.requireNonNull(failure.getCause(), "busy capability cause").getMessage(),
                  "busy capability message")
              .contains("parent directory is busy"));
      assertFalse(Files.exists(witnessTarget));
    } finally {
      release.countDown();
      holder.join();
    }
    assertEquals(null, holderFailure.get());
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
  void retainedWitnessRejectsPrimitiveMismatchesAndInvalidCapabilityFailureVocabulary()
      throws Exception {
    Path admittedTarget = tempDirectory.resolve("primitive-mismatch.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(admittedTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      IOException mismatch =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      admittedTarget,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));

      assertTrue(
          Objects.requireNonNull(mismatch.getMessage(), "primitive mismatch message")
              .contains("not admitted"));
    }

    Path unsupportedParent = Files.createDirectory(tempDirectory.resolve("invalid-vocabulary"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(unsupportedParent);
    Path unsupportedTarget = unsupportedParent.resolve("invalid-vocabulary.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure unsupported =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(
                            unsupportedTarget)),
                    (completion, source) -> {
                      throw new UnsupportedOperationException("injected no-replace refusal");
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePublicationCapabilityWitness.callerPathFailure(
                unsupported, SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY));
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
  void retainedWitnessRejectsNoReplaceEvidenceWhoseCompletionWasReplaced() throws Exception {
    Path targetPath = tempDirectory.resolve("replaced-completion.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      byte[] exactRecord = Files.readAllBytes(completion);
      Files.delete(completion);
      SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, exactRecord);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer share one identity"));
    }
  }

  @Test
  void retainedWitnessRejectsAtomicReplacementEvidenceThatRetainsItsSource() throws Exception {
    Path targetPath = tempDirectory.resolve("replacement-residue.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      Path replacement =
          completion.resolveSibling(
              completion.getFileName().toString().replaceFirst("\\.complete$", ".replacement"));
      Files.writeString(replacement, "unexpected atomic replacement residue");

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessRejectsAnAtomicReplacementWhoseCompletionDisappearsBeforeConfirmation()
      throws Exception {
    Path targetPath = tempDirectory.resolve("missing-atomic-completion.sqlite");

    SqlitePublicationCapabilityWitness.AcquisitionFailure failure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
                    Files::createLink,
                    (replacement, completion) -> {
                      SqliteProtectedBookPublicationSupport.moveReplacing(replacement, completion);
                      Files.delete(completion);
                    }));

    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(failure.getCause(), "atomic completion failure cause")
                    .getMessage(),
                "atomic completion failure message")
            .contains("did not retain the replacement state"));
  }

  @Test
  void retainedWitnessRejectsAnAtomicCompletionThatDisappearsAfterAcquisition() throws Exception {
    Path targetPath = tempDirectory.resolve("deleted-atomic-completion.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Files.delete(publicationCapabilityState(".complete"));

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));

      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessRefusesPrimitivesWhoseProbeDoesNotEstablishTheRequiredIdentity()
      throws Exception {
    Path noReplaceTarget = tempDirectory.resolve("non-atomic-no-replace.key");
    SqlitePublicationCapabilityWitness.AcquisitionFailure noReplaceFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget)),
                    (completion, source) -> Files.copy(source, completion),
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(noReplaceFailure.getCause(), "no-replace failure cause")
                    .getMessage(),
                "no-replace failure message")
            .contains("does not retain the source file identity"));

    Path atomicTarget = tempDirectory.resolve("non-atomic-replacement.sqlite");
    SqlitePublicationCapabilityWitness.AcquisitionFailure atomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicTarget)),
                    Files::createLink,
                    (source, target) ->
                        Files.copy(
                            source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(atomicFailure.getCause(), "atomic failure cause")
                    .getMessage(),
                "atomic failure message")
            .contains("did not retain the replacement state"));

    Path incompleteAtomicTarget = tempDirectory.resolve("incomplete-atomic-replacement.sqlite");
    SqlitePublicationCapabilityWitness.AcquisitionFailure incompleteAtomicFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                            incompleteAtomicTarget)),
                    Files::createLink,
                    (source, target) -> Files.delete(source)));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(
                        incompleteAtomicFailure.getCause(), "incomplete atomic failure cause")
                    .getMessage(),
                "incomplete atomic failure message")
            .contains("witness"));
  }

  @Test
  void retainedWitnessRejectsAtomicCompletionThatRevertedToItsPriorState() throws Exception {
    Path targetPath = tempDirectory.resolve("reverted-atomic-completion.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Path completion = publicationCapabilityState(".complete");
      Path prior = publicationCapabilityState(".prior");
      byte[] priorRecord = Files.readAllBytes(prior);
      Files.delete(completion);
      SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, priorRecord);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "witness failure message")
              .contains("no longer complete"));
    }
  }

  @Test
  void retainedWitnessRejectsTamperedDurableEvidenceWhenItIsReacquired() throws Exception {
    Path noReplaceParent = Files.createDirectory(tempDirectory.resolve("reacquire-no-replace"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(noReplaceParent);
    Path noReplaceTarget = noReplaceParent.resolve("book.key");
    establishWitness(
        noReplaceTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    Path source = publicationCapabilityState(noReplaceParent, ".source");
    Path completion = publicationCapabilityState(noReplaceParent, ".complete");
    byte[] sourceRecord = Files.readAllBytes(source);
    Files.delete(completion);
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(completion, sourceRecord);

    SqlitePublicationCapabilityWitness.AcquisitionFailure noReplaceFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(noReplaceFailure.getCause(), "no-replace failure cause")
                    .getMessage(),
                "no-replace failure message")
            .contains("does not retain the source file identity"));

    Path atomicResidueParent = Files.createDirectory(tempDirectory.resolve("reacquire-atomic"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(atomicResidueParent);
    Path atomicTarget = atomicResidueParent.resolve("book.sqlite");
    establishWitness(atomicTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
    Path atomicCompletion = publicationCapabilityState(atomicResidueParent, ".complete");
    Path replacement =
        atomicCompletion.resolveSibling(
            atomicCompletion.getFileName().toString().replaceFirst("\\.complete$", ".replacement"));
    Files.writeString(replacement, "unexpected replacement source");

    SqlitePublicationCapabilityWitness.AcquisitionFailure atomicResidueFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(atomicResidueFailure.getCause(), "atomic residue cause")
                    .getMessage(),
                "atomic residue message")
            .contains("impossible replacement source"));

    Path malformedParent = Files.createDirectory(tempDirectory.resolve("reacquire-malformed"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(malformedParent);
    Path malformedTarget = malformedParent.resolve("book.sqlite");
    establishWitness(
        malformedTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
    Path malformedCompletion = publicationCapabilityState(malformedParent, ".complete");
    Files.delete(malformedCompletion);
    SqliteCoordinationControlFiles.createAtomicallySecureRecord(
        malformedCompletion,
        new byte[Files.readAllBytes(publicationCapabilityState(malformedParent, ".prior")).length]);

    SqlitePublicationCapabilityWitness.AcquisitionFailure malformedFailure =
        assertThrows(
            SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
            () ->
                SqlitePublicationCapabilityWitness.acquire(
                    java.util.List.of(
                        SqlitePublicationCapabilityWitness.Requirement.atomicReplace(
                            malformedTarget)),
                    Files::createLink,
                    SqliteProtectedBookPublicationSupport::moveReplacing));
    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(malformedFailure.getCause(), "malformed failure cause")
                    .getMessage(),
                "malformed failure message")
            .contains("unexpected immutable state"));
  }

  @Test
  void retainedWitnessRejectsAParentReplacementThatLosesItsImmutableEvidence() throws Exception {
    Path originalParent = Files.createDirectory(tempDirectory.resolve("identity-parent"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(originalParent);
    Path targetPath = originalParent.resolve("book.key");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      Files.move(originalParent, tempDirectory.resolve("identity-parent-retained"));
      Files.createDirectory(originalParent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(originalParent);

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "identity failure message")
              .contains("not valid"));
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

  private Path publicationCapabilityState(String suffix) throws IOException {
    return publicationCapabilityState(tempDirectory, suffix);
  }

  private static void establishWitness(
      Path targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind)
      throws IOException {
    try (SqlitePublicationCapabilityWitness.Set ignored =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                new SqlitePublicationCapabilityWitness.Requirement(targetPath, primitiveKind)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      // The subsequent acquisition validates the retained immutable facts.
    }
  }

  private static Path publicationCapabilityState(Path parent, String suffix) throws IOException {
    try (var entries = Files.list(parent)) {
      return entries
          .filter(
              candidate ->
                  candidate
                          .getFileName()
                          .toString()
                          .startsWith(".fingrind-publication-capability-v2-")
                      && candidate.getFileName().toString().endsWith(suffix))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Missing publication capability state " + suffix));
    }
  }
}
