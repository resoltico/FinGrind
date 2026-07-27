package dev.erst.fingrind.executor.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers exact recovery binding and retained-pair value contracts at the storage boundary. */
class ProtectedBookPairPublicationContractsTest {
  private static final Path BOOK = Path.of("pair-contracts", "book.sqlite");
  private static final Path KEY = Path.of("pair-contracts", "book.key");
  private static final Path BACKUP = Path.of("pair-contracts", "backup.fgba");
  private static final Path BACKUP_KEY = Path.of("pair-contracts", "backup.key");
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @Test
  void bindingsMatchOnlyTheirExactRecoveryRequests() {
    AttestationBackupAcknowledgement acknowledgement = acknowledgement(BACKUP_ID);
    ProtectedBookPairPublicationBinding.Backup backup =
        new ProtectedBookPairPublicationBinding.Backup(BOOK, acknowledgement);
    ProtectedBookPairPublicationBinding.Restore restore =
        new ProtectedBookPairPublicationBinding.Restore(
            BACKUP, BACKUP_KEY, acknowledgement, commit());
    ProtectedBookPairPublicationSourceIdentity identity = keyFileIdentity();
    ProtectedBookPairPublicationBinding.Rekey rekey =
        new ProtectedBookPairPublicationBinding.Rekey(identity, commit(), commit());

    assertEquals(OperationId.BACKUP_BOOK, backup.operation());
    assertTrue(
        backup.matches(new ProtectedBookPairPublicationRecoveryRequest.Backup(BOOK, BACKUP_ID)));
    assertFalse(
        backup.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Backup(
                BOOK, UUID.fromString("018f0000-0000-7000-8000-000000000002"))));
    assertFalse(
        backup.matches(new ProtectedBookPairPublicationRecoveryRequest.Backup(BACKUP, BACKUP_ID)));
    assertFalse(backup.matches(new ProtectedBookPairPublicationRecoveryRequest.Rekey(identity)));

    assertEquals(OperationId.RESTORE_BOOK, restore.operation());
    assertTrue(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP, BACKUP_KEY, acknowledgement(BACKUP_ID))));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP, KEY, acknowledgement(BACKUP_ID))));
    assertFalse(
        restore.matches(new ProtectedBookPairPublicationRecoveryRequest.Backup(BOOK, BACKUP_ID)));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                KEY, BACKUP_KEY, acknowledgement(BACKUP_ID))));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP,
                BACKUP_KEY,
                new AttestationBackupAcknowledgement(
                    BACKUP_ID, bytes(9), BigInteger.ONE, bytes(8)))));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP,
                BACKUP_KEY,
                new AttestationBackupAcknowledgement(
                    UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                    bytes(1),
                    BigInteger.ONE,
                    bytes(3)))));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP,
                BACKUP_KEY,
                new AttestationBackupAcknowledgement(
                    BACKUP_ID, bytes(1), BigInteger.TWO, bytes(3)))));
    assertFalse(
        restore.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP,
                BACKUP_KEY,
                new AttestationBackupAcknowledgement(
                    BACKUP_ID, bytes(1), BigInteger.ONE, bytes(4)))));

    assertEquals(OperationId.REKEY_BOOK, rekey.operation());
    assertTrue(rekey.matches(new ProtectedBookPairPublicationRecoveryRequest.Rekey(identity)));
    assertFalse(
        rekey.matches(
            new ProtectedBookPairPublicationRecoveryRequest.Rekey(
                new ProtectedBookPairPublicationSourceIdentity(
                    BOOK, ProtectedBookPairPublicationSourceIdentity.Kind.STANDARD_INPUT, null))));
    assertFalse(
        rekey.matches(new ProtectedBookPairPublicationRecoveryRequest.Backup(BOOK, BACKUP_ID)));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void recoveryRequestsAndBindingsRejectAmbiguousOrIncompleteValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP, BACKUP, acknowledgement(BACKUP_ID)));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookPairPublicationRecoveryRequest.Backup(null, BACKUP_ID));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookPairPublicationBinding.Backup(BOOK, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationBinding.Restore(
                BACKUP, BACKUP, acknowledgement(BACKUP_ID), commit()));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookPairPublicationBinding.Rekey(null, commit(), commit()));

    assertEquals(
        OperationId.BACKUP_BOOK,
        new ProtectedBookPairPublicationRecoveryRequest.Backup(BOOK, BACKUP_ID).operation());
    assertEquals(
        OperationId.RESTORE_BOOK,
        new ProtectedBookPairPublicationRecoveryRequest.Restore(
                BACKUP, BACKUP_KEY, acknowledgement(BACKUP_ID))
            .operation());
    assertEquals(
        OperationId.REKEY_BOOK,
        new ProtectedBookPairPublicationRecoveryRequest.Rekey(keyFileIdentity()).operation());
  }

  @Test
  void sourceIdentityKeepsOnlyTheSelectedNonsecretTransportFacts() {
    ProtectedBookPairPublicationSourceIdentity keyFile =
        ProtectedBookPairPublicationSourceIdentity.from(
            new ProtectedBookAccess(BOOK, new ProtectedBookPassphraseSource.KeyFile(KEY)));
    ProtectedBookPairPublicationSourceIdentity standardInput =
        ProtectedBookPairPublicationSourceIdentity.from(
            new ProtectedBookAccess(BOOK, ProtectedBookPassphraseSource.StandardInput.INSTANCE));
    ProtectedBookPairPublicationSourceIdentity interactivePrompt =
        ProtectedBookPairPublicationSourceIdentity.from(
            new ProtectedBookAccess(
                BOOK, ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE));

    assertEquals(
        ProtectedBookPairPublicationSourceIdentity.Kind.KEY_FILE, keyFile.passphraseSourceKind());
    assertEquals(KEY.toAbsolutePath().normalize(), keyFile.keyFilePath());
    assertEquals(
        ProtectedBookPairPublicationSourceIdentity.Kind.STANDARD_INPUT,
        standardInput.passphraseSourceKind());
    assertEquals(null, standardInput.keyFilePath());
    assertEquals(
        ProtectedBookPairPublicationSourceIdentity.Kind.INTERACTIVE_PROMPT,
        interactivePrompt.passphraseSourceKind());
    assertEquals(null, interactivePrompt.keyFilePath());
    assertThrows(
        NullPointerException.class,
        () ->
            new ProtectedBookPairPublicationSourceIdentity(
                BOOK, ProtectedBookPairPublicationSourceIdentity.Kind.KEY_FILE, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookPairPublicationSourceIdentity(
                BOOK, ProtectedBookPairPublicationSourceIdentity.Kind.STANDARD_INPUT, KEY));
  }

  @Test
  void pairAdmissionAndFailureOutcomesPreserveOnlySafeEvidenceStates() {
    ProtectedBookPairPublicationRetention retention = retention();
    ProtectedBookPairPublicationBinding.Backup binding =
        new ProtectedBookPairPublicationBinding.Backup(BOOK, acknowledgement(BACKUP_ID));
    try (ProtectedBookMaintenanceStore.PreparedPairPublication prepared = preparedPair()) {
      assertEquals(
          prepared, new ProtectedBookPairPublicationAdmission.Prepared(prepared).publication());
      assertEquals(
          binding,
          new ProtectedBookPairPublicationAdmission.Recovered(binding, retention).binding());
      assertEquals(
          BACKUP.toAbsolutePath().normalize(),
          new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(BACKUP, BACKUP_KEY)
              .backupArtifactPath());
      assertThrows(
          IllegalArgumentException.class,
          () -> new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(BACKUP, BACKUP));

      assertEquals(
          ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
          new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
                  BOOK,
                  KEY,
                  ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                  retention)
              .recoveryRecordState());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
                  BOOK,
                  KEY,
                  ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                  mismatchedRetention()));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
                  BOOK,
                  KEY,
                  ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                  new ProtectedBookPairPublicationRetention(
                      publication(BOOK, ".book-stage"),
                      publication(BACKUP_KEY, ".backup-key-stage"))));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
                  BOOK,
                  BOOK,
                  ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
                  retention));

      assertEquals(
          ProtectedBookPairPublicationMemberState.UNESTABLISHED,
          new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  null)
              .bookArtifactState());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  null));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  null));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  retention));

      assertEquals(
          ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
          new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                  KEY,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  retention)
              .bookArtifactState());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  null));
      assertEquals(
          ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
          new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                  null)
              .secretArtifactState());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  KEY,
                  ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                  null));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
                  BOOK,
                  ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
                  KEY,
                  ProtectedBookPairPublicationMemberState.UNESTABLISHED,
                  null));
    }
  }

  @Test
  void workflowSourceMembersAdmitOnlyDistinctExistingSourceRoles() {
    ProtectedBookMaintenanceStore.WorkflowSourceMember bookMember =
        new ProtectedBookMaintenanceStore.WorkflowSourceMember(
            BOOK, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    ProtectedBookMaintenanceStore.WorkflowSourceMembers members =
        new ProtectedBookMaintenanceStore.WorkflowSourceMembers(List.of(bookMember));

    assertEquals(bookMember, members.primaryMember());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtectedBookMaintenanceStore.WorkflowSourceMembers(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookMaintenanceStore.WorkflowSourceMembers(
                List.of(
                    bookMember,
                    new ProtectedBookMaintenanceStore.WorkflowSourceMember(
                        BOOK, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookMaintenanceStore.WorkflowSourceMember(
                BOOK, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET));
    assertEquals(BOOK, new ProtectedBookMaintenanceStore.LeaseBusy(BOOK).artifactPath());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
        new ProtectedBookMaintenanceStore.WorkflowScopeBusy(
                BACKUP, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE)
            .artifactRole());
  }

  private static AttestationBackupAcknowledgement acknowledgement(UUID backupId) {
    return new AttestationBackupAcknowledgement(backupId, bytes(1), BigInteger.ONE, bytes(3));
  }

  private static byte[] bytes(int first) {
    byte[] bytes = new byte[32];
    bytes[0] = (byte) first;
    return bytes;
  }

  private static AttestationCommit commit() {
    return new AttestationCommit(BigInteger.ONE, "a".repeat(64));
  }

  private static ProtectedBookPairPublicationSourceIdentity keyFileIdentity() {
    return new ProtectedBookPairPublicationSourceIdentity(
        BOOK, ProtectedBookPairPublicationSourceIdentity.Kind.KEY_FILE, KEY);
  }

  private static ProtectedBookMaintenanceStore.PreparedPairPublication preparedPair() {
    return new ProtectedBookMaintenanceStore.PreparedPairPublication() {
      @Override
      public Path bookTargetPath() {
        return BOOK.toAbsolutePath().normalize();
      }

      @Override
      public Path secretTargetPath() {
        return KEY.toAbsolutePath().normalize();
      }

      @Override
      public ProtectedBookMaintenanceStore.RestoredBookTargetPolicy bookTargetPolicy() {
        return ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT;
      }

      @Override
      public void close() {}
    };
  }

  private static ProtectedBookPairPublicationRetention retention() {
    return new ProtectedBookPairPublicationRetention(
        publication(BOOK, ".book-stage"), publication(KEY, ".key-stage"));
  }

  private static ProtectedBookPairPublicationRetention mismatchedRetention() {
    return new ProtectedBookPairPublicationRetention(
        publication(BACKUP, ".backup-stage"), publication(BACKUP_KEY, ".backup-key-stage"));
  }

  private static ArtifactPublicationResult publication(Path published, String stageName) {
    Path normalizedPublished = published.toAbsolutePath().normalize();
    return ArtifactPublicationResult.restoreCapturedCanonicalPaths(
        normalizedPublished,
        new ArtifactPublicationRetention(normalizedPublished.resolveSibling(stageName)));
  }
}
