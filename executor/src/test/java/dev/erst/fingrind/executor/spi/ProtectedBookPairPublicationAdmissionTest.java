package dev.erst.fingrind.executor.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.executor.PublicationTransactionTestFixtures;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Proves pair-admission records reject ambiguous recovery inputs before any workflow can use them.
 */
@SuppressWarnings("NullAway")
class ProtectedBookPairPublicationAdmissionTest {
  private static final Path BOOK_TARGET = Path.of("target/book.sqlite");
  private static final Path SECRET_TARGET = Path.of("target/book.key");

  @Test
  void validatesPairAdmissionAndCommitOutcomes() {
    try (PreparedPairPublication prepared = prepared()) {
      assertEquals(
          prepared, new ProtectedBookPairPublicationAdmission.Prepared(prepared).publication());
      assertThrows(
          NullPointerException.class,
          () -> new ProtectedBookPairPublicationAdmission.Prepared(null));
      assertEquals(
          BOOK_TARGET.toAbsolutePath().normalize(),
          new ProtectedBookPairPublicationAdmission.Recovered(
                  new dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication(
                      PublicationTransactionTestFixtures.completedArtifact(BOOK_TARGET),
                      PublicationTransactionTestFixtures.completedArtifact(SECRET_TARGET)))
              .publication()
              .bookPublication()
              .publishedArtifactPath());

      ProtectedBookPairPublicationAdmission.ExistingCompleteBackup existing =
          new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
              BOOK_TARGET, SECRET_TARGET);
      assertEquals(BOOK_TARGET.toAbsolutePath().normalize(), existing.backupArtifactPath());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
                  BOOK_TARGET, BOOK_TARGET));

      ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete admissionIncomplete =
          new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
              BOOK_TARGET, PublicationTransactionTestFixtures.incompleteResult());
      assertEquals(
          BOOK_TARGET.toAbsolutePath().normalize(), admissionIncomplete.candidateArtifactPath());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
                  BOOK_TARGET, PublicationTransactionTestFixtures.completedResult()));

      StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete commitIncomplete =
          new StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete(
              BOOK_TARGET, PublicationTransactionTestFixtures.incompleteResult());
      assertEquals(
          BOOK_TARGET.toAbsolutePath().normalize(), commitIncomplete.candidateArtifactPath());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete(
                  BOOK_TARGET, PublicationTransactionTestFixtures.completedResult()));
    }
  }

  @Test
  void restrictsEvidenceBlockedAndRecoveryRequestsToSafeIdentities() {
    assertEquals(
        BOOK_TARGET.toAbsolutePath().normalize(),
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                BOOK_TARGET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                SECRET_TARGET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED)
            .bookArtifactPath());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                BOOK_TARGET,
                ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
                SECRET_TARGET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                BOOK_TARGET,
                ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                SECRET_TARGET,
                ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED));

    assertEquals(
        OperationId.BACKUP_BOOK,
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
                BOOK_TARGET, UUID.fromString("018f0000-0000-7000-8000-000000000001"))
            .operation());
    assertEquals(
        OperationId.RESTORE_BOOK,
        new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BOOK_TARGET, SECRET_TARGET, acknowledgement())
            .operation());
    assertEquals(
        OperationId.REKEY_BOOK,
        ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE.operation());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BOOK_TARGET, BOOK_TARGET, acknowledgement()));
  }

  @Test
  void requiresAUniqueNonemptySetOfTrueWorkflowSources() {
    WorkflowSourceMember liveBook =
        new WorkflowSourceMember(BOOK_TARGET, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    WorkflowSourceMembers members = new WorkflowSourceMembers(List.of(liveBook));
    assertEquals(liveBook, members.primaryMember());
    assertThrows(IllegalArgumentException.class, () -> new WorkflowSourceMembers(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowSourceMembers(List.of(liveBook, liveBook)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WorkflowSourceMember(
                BOOK_TARGET, ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET));
  }

  private static PreparedPairPublication prepared() {
    return new PreparedPairPublication() {
      @Override
      public Path bookTargetPath() {
        return BOOK_TARGET.toAbsolutePath().normalize();
      }

      @Override
      public Path secretTargetPath() {
        return SECRET_TARGET.toAbsolutePath().normalize();
      }

      @Override
      public RestoredBookTargetPolicy bookTargetPolicy() {
        return RestoredBookTargetPolicy.REQUIRE_ABSENT;
      }

      @Override
      public void close() {}
    };
  }

  private static dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement
      acknowledgement() {
    return new dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement(
        UUID.fromString("018f0000-0000-7000-8000-000000000001"),
        new byte[32],
        java.math.BigInteger.ZERO,
        new byte[32]);
  }
}
