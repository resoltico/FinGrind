package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Tests capability-witness acquisition, admission, and scope ownership. */
class SqlitePublicationCapabilityWitnessAcquisitionTest
    extends SqlitePublicationCapabilityWitnessTestFixture {
  @Test
  void witnessKeyEqualityUsesOnlyTheCanonicalParentFingerprintAndPrimitive() {
    Path parent = tempDirectory.resolve("witness-key-parent");
    SqlitePublicationCapabilityWitnessKey first =
        new SqlitePublicationCapabilityWitnessKey(
            parent,
            "canonical-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    SqlitePublicationCapabilityWitnessKey equivalent =
        new SqlitePublicationCapabilityWitnessKey(
            parent.resolve("alternate-spelling"),
            "canonical-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);

    assertEquals(first, equivalent);
    Set<SqlitePublicationCapabilityWitnessKey> retainedWitnesses = new HashSet<>();
    assertTrue(retainedWitnesses.add(first));
    assertFalse(retainedWitnesses.add(equivalent));
    assertNotEquals(first, "not a witness key");
    assertNotEquals(
        first,
        new SqlitePublicationCapabilityWitnessKey(
            parent,
            "other-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
    assertNotEquals(
        first,
        new SqlitePublicationCapabilityWitnessKey(
            parent,
            "canonical-parent",
            SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
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
                atomicFailure, SqliteCallerPathFailure.ATOMIC_ARTIFACT_PUBLICATION_UNSUPPORTED));
    assertEquals(
        SqliteCallerPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED,
        typedFailure.pathFailure());
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
                      throw new FileSystemException(
                          target.toString(), staged.toString(), "Permission denied");
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
            SqliteCallerPathFailure.ATOMIC_ARTIFACT_REPLACEMENT_UNSUPPORTED));
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

    Path firstTarget = firstParent.resolve("first.key");
    try (SqlitePublicationCapabilityWitness.Set afterFailure =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(firstTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      afterFailure.requireCurrent(
          firstTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    }
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
      assertDoesNotThrow(
          () ->
              witnesses.requireCurrent(
                  noReplaceTarget,
                  SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
      assertDoesNotThrow(
          () ->
              witnesses.requireCurrent(
                  replacementTarget,
                  SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE));
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
      assertDoesNotThrow(
          () ->
              witnesses.requireCurrent(
                  targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));
    }
  }

  @Test
  void retainedWitnessFailsClosedOnRetiredRandomProbeResidue() throws Exception {
    for (String retiredPrefix :
        java.util.List.of(
            ".fingrind-book-no-replace-probe-",
            ".fingrind-no-replace-probe-",
            ".fingrind-book-replace-probe-")) {
      Path parent =
          Files.createDirectory(tempDirectory.resolve("legacy-" + retiredPrefix.hashCode()));
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
                  Objects.requireNonNull(failure.getCause(), "capability failure cause")
                      .getMessage(),
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
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      witnesses.close();
      witnesses.close();

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  witnesses.requireCurrent(
                      targetPath,
                      SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK));

      assertTrue(
          Objects.requireNonNull(exception.getMessage(), "closed witness message")
              .contains("closed"));
    }
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
    try (SqlitePublicationCapabilityWitness.Set ignored =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      assertThrows(
          SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
          () ->
              SqlitePublicationCapabilityWitness.acquire(
                  java.util.List.of(
                      SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
                  Files::createLink,
                  SqliteProtectedBookPublicationSupport::moveReplacing));
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
  void retainedWitnessKeepsPrimitiveAdmissionDistinctWithinOneParentDirectory() throws Exception {
    Path noReplaceTarget = tempDirectory.resolve("no-replace.key");
    Path atomicReplaceTarget = tempDirectory.resolve("atomic-replace.sqlite");

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePublicationCapabilityWitness.acquire(
            java.util.List.of(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(noReplaceTarget),
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(atomicReplaceTarget)),
            Files::createLink,
            SqliteProtectedBookPublicationSupport::moveReplacing)) {
      witnesses.requireCurrent(
          noReplaceTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      witnesses.requireCurrent(
          atomicReplaceTarget, SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);

      assertPrimitiveIsNotAdmitted(
          witnesses,
          atomicReplaceTarget,
          SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
      assertPrimitiveIsNotAdmitted(
          witnesses,
          noReplaceTarget,
          SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE);
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

  private static void assertPrimitiveIsNotAdmitted(
      SqlitePublicationCapabilityWitness.Set witnesses,
      Path targetPath,
      SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind) {
    IOException exception =
        assertThrows(IOException.class, () -> witnesses.requireCurrent(targetPath, primitiveKind));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "primitive mismatch message")
            .contains("not admitted"));
  }
}
