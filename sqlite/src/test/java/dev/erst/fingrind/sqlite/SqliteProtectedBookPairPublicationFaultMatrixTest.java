package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Fault coverage for recovery admission before any final protected-book member is published. */
class SqliteProtectedBookPairPublicationFaultMatrixTest
    extends SqliteArtifactPublicationTestSupport {
  @Test
  void durableIntentInOneParentStillBarsAnAlternateRequestSharingTheOtherParent() throws Exception {
    PartialEvidence partial = partialEvidence("intent-boundary", "pair-intent-");
    Path alternateBook = absentTarget("intent-boundary/alternate/book.sqlite");
    Path alternateSecret = partial.secretTargetPath().resolveSibling("alternate.key");
    List<String> secretParentBefore = childNames(parentOf(partial.secretTargetPath()));

    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, parent) -> {},
            ignored -> {});

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                admitPairPublication(
                    store,
                    alternateBook,
                    alternateSecret,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    new ProtectedBookPairPublicationRecoveryRequest.Backup(
                        alternateBook.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    ProtectedBookMaintenanceRejection.RecoveryPending pending =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.RecoveryPending.class, refusal.rejection());
    assertEquals(partial.bookTargetPath(), pending.bookTargetPath());
    assertEquals(partial.secretTargetPath(), pending.generatedSecretTargetPath());
    assertTrue(
        partial
            .durabilitySteps()
            .contains(
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .RECOVERY_INTENT));
    assertFalse(Files.exists(alternateBook));
    assertFalse(Files.exists(alternateSecret));
    assertEquals(
        secretParentBefore, nonCoordinationChildNames(parentOf(partial.secretTargetPath())));
  }

  @Test
  void durableRecoveryCopyInOneParentBarsAnAlternateRequestSharingTheOtherParent()
      throws Exception {
    PartialEvidence partial = partialEvidence("recovery-boundary", "pair-recovery-v3-");
    Path alternateBook = absentTarget("recovery-boundary/alternate/book.sqlite");
    Path alternateSecret = partial.secretTargetPath().resolveSibling("alternate.key");
    List<String> secretParentBefore = childNames(parentOf(partial.secretTargetPath()));

    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, parent) -> {},
            ignored -> {});

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                admitPairPublication(
                    store,
                    alternateBook,
                    alternateSecret,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    new ProtectedBookPairPublicationRecoveryRequest.Backup(
                        alternateBook.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertInstanceOf(ProtectedBookMaintenanceRejection.RecoveryPending.class, refusal.rejection());
    assertTrue(
        partial
            .durabilitySteps()
            .contains(
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .RECOVERY_RECORD));
    assertFalse(Files.exists(alternateBook));
    assertFalse(Files.exists(alternateSecret));
    assertEquals(
        secretParentBefore, nonCoordinationChildNames(parentOf(partial.secretTargetPath())));
  }

  @Test
  void keyFirstSameParentRekeyAdmissionDoesNotTreatItsTargetAsAWorkflowSource() throws Exception {
    Path bookTarget = writeArtifact("key-first-rekey/z-live.sqlite", "live book bytes");
    Path secretTarget = absentTarget("key-first-rekey/a-new.key");
    SqliteProtectedBookMaintenanceStore store = recoveryStore();
    byte[] bookBefore = Files.readAllBytes(bookTarget);

    SqliteNativeActivityRegistration activityRegistration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookTarget, true);
    try {
      ProtectedBookPairPublicationAdmission.Prepared prepared =
          assertInstanceOf(
              ProtectedBookPairPublicationAdmission.Prepared.class,
              admitPairPublication(
                  store,
                  bookTarget,
                  secretTarget,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  new ProtectedBookPairPublicationRecoveryRequest.Rekey(
                      rekeyBinding(bookTarget, bookTarget.resolveSibling("source.key"))
                          .sourceIdentity()),
                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                  ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));
      try (PreparedPairPublication ignored = prepared.publication()) {
        assertEquals(bookTarget.toAbsolutePath().normalize(), ignored.bookTargetPath());
        assertEquals(secretTarget.toAbsolutePath().normalize(), ignored.secretTargetPath());
      }

      assertFalse(Files.exists(secretTarget));
      assertArrayEquals(bookBefore, Files.readAllBytes(bookTarget));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
    }
  }

  @Test
  void exactRecoveryRepairsMissingImmutableEvidenceBeforePublishingEitherFinalMember()
      throws Exception {
    PartialEvidence partial = partialEvidence("exact-repair", "pair-recovery-v3-");
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> recoverySteps =
        new ArrayList<>();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, parent) -> recoverySteps.add(step),
            ignored -> {});

    ProtectedBookPairPublicationAdmission.Recovered recovered =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Recovered.class,
            admitPairPublication(
                store,
                partial.bookTargetPath(),
                partial.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                partial.request(),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertInstanceOf(ProtectedBookPairPublicationBinding.Backup.class, recovered.binding());
    assertTrue(recovered.binding().matches(partial.request()));
    assertEquals("book bytes", Files.readString(partial.bookTargetPath()));
    assertEquals("secret bytes", Files.readString(partial.secretTargetPath()));
    assertTrue(Files.exists(partial.bookStagePath()));
    assertTrue(Files.exists(partial.secretStagePath()));
    assertFalse(SqliteOwnedStageRecord.findFor(partial.bookTargetPath()).isEmpty());
    assertFalse(SqliteOwnedStageRecord.findFor(partial.secretTargetPath()).isEmpty());
    assertTrue(
        recoverySteps.contains(
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD));
    assertTrue(
        recoverySteps.contains(
            SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                .GENERATED_SECRET_PUBLICATION));
  }

  @Test
  void intentOnlyEvidenceNeverPublishesEitherFinalMember() throws Exception {
    PartialEvidence partial = partialEvidence("intent-only", "pair-intent-");
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> recoverySteps =
        new ArrayList<>();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> false,
            (step, parent) -> recoverySteps.add(step),
            ignored -> {});

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                store,
                partial.bookTargetPath(),
                partial.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                partial.request(),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertFalse(Files.exists(partial.bookTargetPath()));
    assertFalse(Files.exists(partial.secretTargetPath()));
    assertTrue(recoverySteps.isEmpty(), () -> "Unexpected recovery mutation: " + recoverySteps);
  }

  @Test
  void completeClaimOnlyEvidenceIsInertForANewPairAdmission() throws Exception {
    PartialEvidence partial = completeClaimOnlyEvidence("claim-only");
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> recoverySteps =
        new ArrayList<>();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, parent) -> recoverySteps.add(step),
            ignored -> {});

    ProtectedBookPairPublicationAdmission.Prepared prepared =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Prepared.class,
            admitPairPublication(
                store,
                partial.bookTargetPath(),
                partial.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                partial.request(),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    try (PreparedPairPublication ignored = prepared.publication()) {
      assertEquals(partial.bookTargetPath(), ignored.bookTargetPath());
      assertEquals(partial.secretTargetPath(), ignored.secretTargetPath());
    }
    assertFalse(Files.exists(partial.bookTargetPath()));
    assertFalse(Files.exists(partial.secretTargetPath()));
    assertTrue(Files.exists(partial.bookStagePath()));
    assertTrue(Files.exists(partial.secretStagePath()));
    assertFalse(SqliteOwnedStageRecord.findFor(partial.bookTargetPath()).isEmpty());
    assertFalse(SqliteOwnedStageRecord.findFor(partial.secretTargetPath()).isEmpty());
    assertTrue(recoverySteps.isEmpty(), () -> "Unexpected recovery mutation: " + recoverySteps);
  }

  @Test
  void partialClaimEvidenceIsInertForAnAlternatePairSharingItsParent() throws Exception {
    PartialEvidence partial = partialEvidence("claim-alternate", "pair-claim-");
    Path alternateBook = absentTarget("claim-alternate/alternate/book.sqlite");
    Path alternateSecret = partial.secretTargetPath().resolveSibling("alternate.key");
    List<String> secretParentBefore = childNames(parentOf(partial.secretTargetPath()));
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> recoverySteps =
        new ArrayList<>();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> false,
            (step, parent) -> recoverySteps.add(step),
            ignored -> {});

    ProtectedBookPairPublicationAdmission.Prepared prepared =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Prepared.class,
            admitPairPublication(
                store,
                alternateBook,
                alternateSecret,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    alternateBook.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    try (PreparedPairPublication ignored = prepared.publication()) {
      assertEquals(alternateBook, ignored.bookTargetPath());
      assertEquals(alternateSecret, ignored.secretTargetPath());
    }
    assertTrue(
        partial
            .durabilitySteps()
            .contains(
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .PAIR_STAGE_CLAIM));
    assertFalse(
        partial
            .durabilitySteps()
            .contains(
                SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                    .RECOVERY_INTENT));
    assertFalse(Files.exists(alternateBook));
    assertFalse(Files.exists(alternateSecret));
    assertTrue(
        nonCoordinationChildNames(parentOf(partial.secretTargetPath()))
            .containsAll(secretParentBefore));
    assertTrue(recoverySteps.isEmpty(), () -> "Unexpected recovery mutation: " + recoverySteps);
  }

  @Test
  void completeClaimInAnUnleasedSiblingDomainIsInertAndPreservesItsSecretStage() throws Exception {
    PartialEvidence partial = completeClaimOnlyEvidence("claim-cross-domain");
    Path alternateBook = absentTarget("claim-cross-domain/alternate/book.sqlite");
    Path alternateSecret = partial.secretTargetPath().resolveSibling("alternate.key");
    SqliteProtectedBookMaintenanceStore store = recoveryStore();

    ProtectedBookPairPublicationAdmission.Prepared prepared =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Prepared.class,
            admitPairPublication(
                store,
                alternateBook,
                alternateSecret,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    alternateBook.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    try (PreparedPairPublication ignored = prepared.publication()) {
      assertEquals(alternateBook, ignored.bookTargetPath());
      assertEquals(alternateSecret, ignored.secretTargetPath());
    }
    assertTrue(Files.exists(partial.bookStagePath()));
    assertTrue(Files.exists(partial.secretStagePath()));
    assertFalse(Files.exists(alternateBook));
    assertFalse(Files.exists(alternateSecret));
  }

  @Test
  void retiredPairEvidenceFailsClosedWithoutBeingReadOrDeleted() throws Exception {
    Path bookTarget = absentTarget("retired-evidence/book.sqlite");
    Path secretTarget = absentTarget("retired-evidence/book.key");
    Path retiredEvidence =
        parentOf(bookTarget)
            .resolve(
                ".fingrind-protected-book-pair-correlation-"
                    + "00000000-0000-0000-0000-000000000001.pending");
    Files.writeString(retiredEvidence, "retired pair evidence");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (ignoredStep, ignoredParent) -> {},
            ignored -> {});

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                store,
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    bookTarget.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertFalse(Files.exists(bookTarget));
    assertFalse(Files.exists(secretTarget));
    assertTrue(Files.exists(retiredEvidence));
  }

  @Test
  void malformedCurrentEvidenceFailsClosedWithoutBeingReadOrDeleted() throws Exception {
    Path bookTarget = absentTarget("malformed-current-evidence/book.sqlite");
    Path secretTarget = absentTarget("malformed-current-evidence/book.key");
    Path malformedEvidence =
        parentOf(bookTarget)
            .resolve(
                ".fingrind-protected-book-pair-claim-"
                    + "00000000-0000-0000-0000-000000000001.unrecognized");
    Files.writeString(malformedEvidence, "malformed current pair evidence");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (ignoredStep, ignoredParent) -> {},
            ignored -> {});

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                store,
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    bookTarget.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertFalse(Files.exists(bookTarget));
    assertFalse(Files.exists(secretTarget));
    assertTrue(Files.exists(malformedEvidence));
  }

  @Test
  void oversizedCurrentEvidenceFailsClosedWithoutUnboundedRecoveryRead() throws Exception {
    Path bookTarget = absentTarget("oversized-current-evidence/book.sqlite");
    Path secretTarget = absentTarget("oversized-current-evidence/book.key");
    Path oversizedEvidence =
        parentOf(bookTarget)
            .resolve(
                ".fingrind-protected-book-pair-claim-"
                    + "00000000-0000-0000-0000-000000000001.claim");
    Files.writeString(
        oversizedEvidence,
        "x".repeat(SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES + 1));

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                recoveryStore(),
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    bookTarget.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertTrue(Files.exists(oversizedEvidence));
  }

  @Test
  void oversizedCurrentOwnerRecordFailsClosedWithoutUnboundedRecoveryRead() throws Exception {
    Path bookTarget = absentTarget("oversized-owner-record/book.sqlite");
    Path secretTarget = absentTarget("oversized-owner-record/book.key");
    Path oversizedOwnerRecord =
        parentOf(bookTarget)
            .resolve(".fingrind-maintenance-stage-" + "00000000-0000-0000-0000-000000000001.owner");
    SqliteTestPrivateDirectorySupport.writeOwnerOnlyUtf8File(
        oversizedOwnerRecord,
        "x".repeat(SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES + 1));

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                recoveryStore(),
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    bookTarget.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertTrue(Files.exists(oversizedOwnerRecord));
  }

  @Test
  void rootPathInCurrentEvidenceClassifiesAsUnsafeInsteadOfEscapingTheDecoder() throws Exception {
    PartialEvidence partial = partialEvidence("root-path-evidence", "pair-claim-");
    Path malformedEvidence = currentPairEvidence(parentOf(partial.bookTargetPath()));
    String malformedContent =
        Files.readString(malformedEvidence)
            .replaceFirst(
                "(?m)^book-target=.*$",
                "book-target="
                    + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(Path.of("/")));
    Files.writeString(malformedEvidence, malformedContent);
    SqliteProtectedBookMaintenanceStore store = recoveryStore();

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                store,
                partial.bookTargetPath(),
                partial.secretTargetPath(),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                partial.request(),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertTrue(Files.exists(malformedEvidence));
  }

  @Test
  void missingKeyFileSourcePathInCurrentRekeyEvidenceClassifiesAsUnsafe() throws Exception {
    Path bookTarget = writeArtifact("missing-source-key/book.sqlite", "selected book");
    Path secretTarget = absentTarget("missing-source-key/book.key");
    Path bookStage = writeArtifact("missing-source-key/.book.stage", "rekeyed book");
    Path secretStage = writeArtifact("missing-source-key/.secret.stage", "generated key");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    ProtectedBookPairPublicationBinding.Rekey binding =
        rekeyBinding(bookTarget, bookTarget.resolveSibling("source.key"));
    SqliteProtectedBookPairPublicationRecord record =
        SqliteProtectedBookPairPublicationRecord.create(
            bookTarget,
            secretTarget,
            bookStage,
            secretStage,
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            binding,
            (ignoredStep, ignoredParent) -> {});
    Path malformedEvidence =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Files.writeString(
        malformedEvidence,
        Files.readString(malformedEvidence).replaceFirst("(?m)^source-key=.*\\R", ""));
    SqliteProtectedBookMaintenanceStore store = recoveryStore();

    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
            admitPairPublication(
                store,
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                new ProtectedBookPairPublicationRecoveryRequest.Rekey(binding.sourceIdentity()),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    assertEvidenceBlocked(admission);
    assertTrue(Files.exists(malformedEvidence));
  }

  @Test
  void completedPairRetainedEvidenceDoesNotBlockAnotherPairSharingOneParent() throws Exception {
    Path bookTarget = absentTarget("completed-retention/original/book.sqlite");
    Path secretTarget = absentTarget("completed-retention/secret/book.key");
    Path bookStage = writeArtifact("completed-retention/original/.book.stage", "book bytes");
    Path secretStage = writeArtifact("completed-retention/secret/.secret.stage", "secret bytes");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    ProtectedBookPairPublicationBinding.Backup binding =
        backupBinding(bookTarget.resolveSibling("source.sqlite"));
    SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        binding,
        (ignoredStep, ignoredParent) -> {});
    Files.createLink(bookTarget, bookStage);
    Files.createLink(secretTarget, secretStage);

    Path alternateBook = absentTarget("completed-retention/alternate/book.sqlite");
    Path alternateSecret = secretTarget.resolveSibling("alternate.key");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, parent) -> {},
            ignored -> {});

    ProtectedBookPairPublicationAdmission.Prepared prepared =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Prepared.class,
            admitPairPublication(
                store,
                alternateBook,
                alternateSecret,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    alternateBook.resolveSibling("source.sqlite"), new java.util.UUID(0L, 1L)),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    try (PreparedPairPublication ignored = prepared.publication()) {
      assertEquals(alternateBook.toAbsolutePath().normalize(), ignored.bookTargetPath());
    }
    assertTrue(Files.exists(bookTarget));
    assertTrue(Files.exists(secretTarget));
  }

  @Test
  void recoveredCompletedPairRetainsItsOwnedStagesAndOwnerRecords() throws Exception {
    Path bookTarget = absentTarget("completed-retention/book-parent/book.sqlite");
    Path secretTarget = absentTarget("completed-retention/secret-parent/book.key");
    Path bookStage = writeArtifact("completed-retention/book-parent/.book.stage", "book bytes");
    Path secretStage =
        writeArtifact("completed-retention/secret-parent/.secret.stage", "secret bytes");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    ProtectedBookPairPublicationBinding.Backup binding =
        backupBinding(bookTarget.resolveSibling("source.sqlite"));
    SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        binding,
        (ignoredStep, ignoredParent) -> {});
    Files.createLink(bookTarget, bookStage);
    Files.createLink(secretTarget, secretStage);
    deleteOnePairEvidenceCopy(parentOf(secretTarget));

    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (ignoredStep, ignoredParent) -> {},
            ignored -> {});
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            binding.sourceBookPath(), binding.acknowledgement().backupId());

    ProtectedBookPairPublicationAdmission.Recovered recovered =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Recovered.class,
            admitPairPublication(
                store,
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                request,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertTrue(recovered.binding().matches(request));
    assertEquals("book bytes", Files.readString(bookTarget));
    assertEquals("secret bytes", Files.readString(secretTarget));
    assertTrue(Files.exists(bookStage));
    assertTrue(Files.exists(secretStage));
    assertFalse(SqliteOwnedStageRecord.findFor(bookTarget).isEmpty());
    assertFalse(SqliteOwnedStageRecord.findFor(secretTarget).isEmpty());
  }

  @Test
  void recoveryDoesNotPublishTheSecretWhenTheSelectedRekeyBookHasChanged() throws Exception {
    Path finalBookPath = writeArtifact("rekey-recovery/book.sqlite", "selected source book");
    Path finalKeyPath = tempDirectory.resolve("rekey-recovery/book.key");
    Path stagedBookPath = writeArtifact("rekey-recovery/.book.stage", "rekeyed book");
    Path stagedKeyPath = writeArtifact("rekey-recovery/.key.stage", "generated key");
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    ProtectedBookPairPublicationBinding.Rekey binding =
        rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.book-key"));
    SqliteProtectedBookPairPublicationRecord.create(
        finalBookPath,
        finalKeyPath,
        stagedBookPath,
        stagedKeyPath,
        RestoredBookTargetPolicy.REPLACE_SELECTED,
        binding,
        (ignoredStep, ignoredParent) -> {});

    Files.writeString(finalBookPath, "externally changed selected source book");
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> durabilitySteps =
        new ArrayList<>();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredBook, ignoredKey, ignoredBinding) -> true,
            (step, ignoredParent) -> durabilitySteps.add(step),
            ignoredRecord -> {});

    ProtectedBookPairPublicationFailureOutcome.CompletionUncertain admission =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
            admitPairPublication(
                store,
                finalBookPath,
                finalKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                new ProtectedBookPairPublicationRecoveryRequest.Rekey(binding.sourceIdentity()),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, admission.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, admission.secretArtifactState());
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(durabilitySteps.isEmpty(), () -> "Unexpected recovery mutation: " + durabilitySteps);
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  private PartialEvidence partialEvidence(String directoryName, String failingFileNameFragment)
      throws java.io.IOException {
    Path bookTarget = absentTarget(directoryName + "/book-parent/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/secret-parent/book.key");
    Path bookStage = writeArtifact(directoryName + "/book-parent/.book.stage", "book bytes");
    Path secretStage =
        writeArtifact(directoryName + "/secret-parent/.secret.stage", "secret bytes");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    ProtectedBookPairPublicationBinding.Backup binding =
        backupBinding(bookTarget.resolveSibling("source.sqlite"));
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> durabilitySteps =
        new ArrayList<>();

    assertThrows(
        SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException.class,
        () ->
            SqliteProtectedBookPairPublicationRecord.create(
                bookTarget,
                secretTarget,
                bookStage,
                secretStage,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                binding,
                (step, ignoredParent) -> durabilitySteps.add(step),
                (evidencePath, temporaryPath) -> {
                  if (parentOf(evidencePath).equals(parentOf(secretTarget))
                      && evidencePath.getFileName().toString().contains(failingFileNameFragment)) {
                    throw new java.io.IOException("injected second-parent evidence failure");
                  }
                  Files.createLink(evidencePath, temporaryPath);
                }));

    return new PartialEvidence(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        binding,
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            binding.sourceBookPath(), binding.acknowledgement().backupId()),
        List.copyOf(durabilitySteps));
  }

  private PartialEvidence completeClaimOnlyEvidence(String directoryName)
      throws java.io.IOException {
    Path bookTarget = absentTarget(directoryName + "/book-parent/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/secret-parent/book.key");
    Path bookStage = writeArtifact(directoryName + "/book-parent/.book.stage", "book bytes");
    Path secretStage =
        writeArtifact(directoryName + "/secret-parent/.secret.stage", "secret bytes");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    ProtectedBookPairPublicationBinding.Backup binding =
        backupBinding(bookTarget.resolveSibling("source.sqlite"));
    List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> durabilitySteps =
        new ArrayList<>();

    assertThrows(
        java.io.IOException.class,
        () ->
            SqliteProtectedBookPairPublicationRecord.create(
                bookTarget,
                secretTarget,
                bookStage,
                secretStage,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                binding,
                (step, ignoredParent) -> durabilitySteps.add(step),
                (evidencePath, temporaryPath) -> {
                  if (evidencePath.getFileName().toString().contains("pair-intent-")) {
                    throw new java.io.IOException("injected claim-only intent refusal");
                  }
                  Files.createLink(evidencePath, temporaryPath);
                }));

    return new PartialEvidence(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        binding,
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            binding.sourceBookPath(), binding.acknowledgement().backupId()),
        List.copyOf(durabilitySteps));
  }

  private SqliteProtectedBookMaintenanceStore recoveryStore() {
    return new SqliteProtectedBookMaintenanceStore(
        KEY_FILE_RESOLVER,
        (ignoredBook, ignoredKey, ignoredBinding) -> true,
        (ignoredStep, ignoredParent) -> {},
        ignored -> {});
  }

  private static void assertEvidenceBlocked(
      ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked admission) {
    assertEquals(
        ProtectedBookPairPublicationMemberState.UNESTABLISHED, admission.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.UNESTABLISHED, admission.secretArtifactState());
    assertNull(admission.pairPublicationRetention());
  }

  private Path absentTarget(String relativePath) throws java.io.IOException {
    Path target = tempDirectory.resolve(relativePath);
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }

  private static List<String> childNames(Path parent) throws java.io.IOException {
    try (var children = Files.list(parent)) {
      return children
          .map(path -> path.getFileName().toString())
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  /** Excludes the durable directory coordination control from payload-residue assertions. */
  private static List<String> nonCoordinationChildNames(Path parent) throws java.io.IOException {
    String controlFileName =
        SqliteMaintenanceLeaseArtifacts.controlFilePath(parent).getFileName().toString();
    return childNames(parent).stream().filter(name -> !name.equals(controlFileName)).toList();
  }

  private static Path parentOf(Path path) {
    return Objects.requireNonNull(Objects.requireNonNull(path, "path").getParent(), "path parent");
  }

  private static Path currentPairEvidence(Path parent) throws java.io.IOException {
    try (var children = Files.list(parent)) {
      return children
          .filter(
              path -> path.getFileName().toString().startsWith(".fingrind-protected-book-pair-"))
          .findFirst()
          .orElseThrow(
              () -> new AssertionError("Expected one current protected-book pair evidence file."));
    }
  }

  private static void deleteOnePairEvidenceCopy(Path parent) throws java.io.IOException {
    try (var children = Files.list(parent)) {
      Path evidence =
          children
              .filter(
                  path ->
                      path.getFileName().toString().startsWith(".fingrind-protected-book-pair-"))
              .findFirst()
              .orElseThrow(
                  () -> new AssertionError("Expected one protected-book pair evidence copy."));
      Files.delete(evidence);
    }
  }

  private record PartialEvidence(
      Path bookTargetPath,
      Path secretTargetPath,
      Path bookStagePath,
      Path secretStagePath,
      ProtectedBookPairPublicationBinding.Backup binding,
      ProtectedBookPairPublicationRecoveryRequest.Backup request,
      List<SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep> durabilitySteps) {}
}
