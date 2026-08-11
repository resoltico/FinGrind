package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises backup, rekey, and restore against one real attested SQLite protected book. */
class SqliteAttestedLifecycleFieldTest extends SqliteArtifactPublicationTestSupport {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T12:00:00Z");
  private static final UUID PRINCIPAL_ID = UUID.fromString("4c6c00ac-82b0-4418-a2a3-c4e7073ce7c8");

  @Test
  void backupRekeyAndRestore_preserveIndependentVerifiableAttestationChains() throws Exception {
    Path sourceBookPath = tempDirectory.resolve("live").resolve("book.sqlite");
    AttestationCredentialSource credential = createFounderCredential();
    BookAccess sourceAccess = attestedBookAccess(sourceBookPath, credential);
    initializeAttestedBook(sourceAccess, credential);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(Clock.fixed(RECORDED_AT, ZoneOffset.UTC), store);
    Path backupPath = tempDirectory.resolve("backup").resolve("book.fgba");
    Path backupKeyPath = tempDirectory.resolve("backup").resolve("book.key");
    Path rekeyedBookKeyPath = tempDirectory.resolve("live").resolve("book-rekeyed.key");
    Path restoredBookPath = tempDirectory.resolve("restored").resolve("book.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restored").resolve("book.key");

    try (AttestationSigningSession signingSession =
        AttestationSigningSession.open(List.of(credential))) {
      ProtectedBookBackupOutcome.BackedUp backup =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              acceptedValue(
                  workflow.backupBook(
                      localAccess(sourceAccess),
                      backupPath,
                      backupKeyPath,
                      UUID.fromString("bd80d27b-01f6-4ebd-8090-2fc5046a5c18"),
                      signingSession)));
      assertEquals(backupPath.toAbsolutePath().normalize(), backup.backupFilePath());
      assertEquals(backupKeyPath.toAbsolutePath().normalize(), backup.backupBookKeyFilePath());
      dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact
          verifiedBackupArtifact = store.verifyBackupArtifact(backupPath, backupKeyPath);
      try (verifiedBackupArtifact) {
        assertEquals(
            UUID.fromString("bd80d27b-01f6-4ebd-8090-2fc5046a5c18"),
            verifiedBackupArtifact.verification().backupId());
        assertEquals(0, verifiedBackupArtifact.verification().sourceOrder().intValueExact());
        assertInstanceOf(
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerifiedBook.class,
            verifiedBackupArtifact.snapshotBook());
      }
      verifiedBackupArtifact.close();
      assertThrows(IllegalStateException.class, verifiedBackupArtifact::verification);
      assertThrows(IllegalStateException.class, verifiedBackupArtifact::snapshotBook);
      Path unrecognizedKeyPath = tempDirectory.resolve("backup").resolve("unrecognized-backup.key");
      Files.writeString(unrecognizedKeyPath, "not a FinGrind backup key");
      ProtectedBookMaintenanceRejectionException unrecognizedKeyFailure =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class,
              () -> store.verifyBackupArtifact(backupPath, unrecognizedKeyPath));
      dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
              .ArtifactVerificationFailed
          unrecognizedKeyRejection =
              assertInstanceOf(
                  dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
                      .ArtifactVerificationFailed.class,
                  unrecognizedKeyFailure.rejection());
      assertEquals(
          dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
              .BACKUP_KEY_SOURCE,
          unrecognizedKeyRejection.artifactRole());
      assertEquals(unrecognizedKeyPath.toRealPath(), unrecognizedKeyRejection.artifactPath());

      BookAccess wrongKeyAccess =
          bookAccess(
              tempDirectory.resolve("wrong-backup-key.sqlite"),
              "a syntactically valid but incorrect backup passphrase");
      Path wrongKeyPath =
          ((BookAccess.PassphraseSource.KeyFile) wrongKeyAccess.passphraseSource())
              .bookKeyFilePath();
      dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
              .ArtifactVerificationFailed
          wrongKeyRejection =
              assertInstanceOf(
                  dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
                      .ArtifactVerificationFailed.class,
                  assertThrows(
                          ProtectedBookMaintenanceRejectionException.class,
                          () -> store.verifyBackupArtifact(backupPath, wrongKeyPath))
                      .rejection());
      assertEquals(
          dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
          wrongKeyRejection.artifactRole());
      assertEquals(backupPath.toRealPath(), wrongKeyRejection.artifactPath());

      ProtectedBookRekeyOutcome.Rekeyed rekeyed =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rekeyed.class,
              acceptedValue(
                  workflow.rekeyBook(
                      localAccess(sourceAccess), rekeyedBookKeyPath, signingSession)));
      ProtectedBookRekeyOutcome.Rekeyed recoveredRekey =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rekeyed.class,
              acceptedValue(
                  workflow.rekeyBook(
                      localAccess(bookAccessWithKey(sourceBookPath, rekeyedBookKeyPath)),
                      rekeyedBookKeyPath,
                      signingSession)));
      assertEquals(rekeyed.attestationCommit(), recoveredRekey.attestationCommit());
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion.RECOVERED,
          recoveredRekey.pairPublicationCompletion());

      ProtectedBookRestoreOutcome.Restored restored =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Restored.class,
              acceptedValue(
                  workflow.restoreBook(
                      restoredBookPath,
                      restoredBookKeyPath,
                      backupPath,
                      backupKeyPath,
                      signingSession)));
      ProtectedBookRestoreOutcome.Restored recoveredRestore =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Restored.class,
              acceptedValue(
                  workflow.restoreBook(
                      restoredBookPath,
                      restoredBookKeyPath,
                      backupPath,
                      backupKeyPath,
                      signingSession)));
      assertEquals(restored.attestationCommit(), recoveredRestore.attestationCommit());
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion.RECOVERED,
          recoveredRestore.pairPublicationCompletion());
    }

    assertEquals(
        3,
        attestationEvidence(store, bookAccessWithKey(sourceBookPath, rekeyedBookKeyPath)).size());
    assertEquals(
        2,
        attestationEvidence(store, bookAccessWithKey(restoredBookPath, restoredBookKeyPath))
            .size());
  }

  @Test
  void backupRecoveryRevalidatesThePublishedPairAgainstItsSignedManifest() throws Exception {
    Path sourceBookPath = tempDirectory.resolve("live").resolve("book.sqlite");
    AttestationCredentialSource credential = createFounderCredential();
    BookAccess sourceAccess = attestedBookAccess(sourceBookPath, credential);
    initializeAttestedBook(sourceAccess, credential);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(Clock.fixed(RECORDED_AT, ZoneOffset.UTC), store);
    Path backupPath = tempDirectory.resolve("backup").resolve("book.fgba");
    Path backupKeyPath = tempDirectory.resolve("backup").resolve("book.key");
    UUID backupId = UUID.fromString("84107a08-c933-4277-a046-1aa4ea402a65");

    try (AttestationSigningSession signingSession =
        AttestationSigningSession.open(List.of(credential))) {
      ProtectedBookBackupOutcome.BackedUp initialBackup =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              acceptedValue(
                  workflow.backupBook(
                      localAccess(sourceAccess),
                      backupPath,
                      backupKeyPath,
                      backupId,
                      signingSession)));
      ProtectedBookBackupOutcome.BackedUp recoveredBackup =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              acceptedValue(
                  workflow.backupBook(
                      localAccess(sourceAccess),
                      backupPath,
                      backupKeyPath,
                      backupId,
                      signingSession)));

      assertEquals(initialBackup.backupFilePath(), recoveredBackup.backupFilePath());
      assertEquals(initialBackup.backupBookKeyFilePath(), recoveredBackup.backupBookKeyFilePath());
      assertEquals(backupId, recoveredBackup.backupId());
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion.RECOVERED,
          recoveredBackup.pairPublicationCompletion());
    }
  }

  private AttestationCredentialSource createFounderCredential() throws IOException {
    Path credentialDirectory = tempDirectory.resolve("attestation");
    Files.createDirectories(credentialDirectory);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(credentialDirectory);
    Path encryptedKeyPath = credentialDirectory.resolve("founder.fgatk");
    Path passphrasePath = credentialDirectory.resolve("founder.passphrase");
    Files.writeString(passphrasePath, "field-test-attestation-passphrase\n");
    return new AttestationCredentialSource(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        PRINCIPAL_ID,
        encryptedKeyPath,
        passphrasePath);
  }

  private BookAccess attestedBookAccess(
      Path bookPath, AttestationCredentialSource credentialSource) {
    BookAccess basicAccess = bookAccess(bookPath);
    return new BookAccess(
        basicAccess.bookFilePath(), basicAccess.passphraseSource(), List.of(credentialSource));
  }

  private static BookAccess bookAccessWithKey(Path bookPath, Path keyPath) {
    return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath), List.of());
  }

  private void initializeAttestedBook(
      BookAccess bookAccess, AttestationCredentialSource credentialSource) {
    AttestationEvidence genesis =
        AttestationGenesisFactory.prepare(
                SqlitePostingFactFixtureSupport.bookIdentity(),
                RECORDED_AT,
                List.of(
                    new AttestationFounderInput(
                        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                        credentialSource.principalId(),
                        credentialSource.encryptedKeyFilePath(),
                        credentialSource.passphraseFilePath())))
            .evidence();
    try (SqlitePostingFactStore store = SqliteStoreFixtureSupport.openStore(bookAccess)) {
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome.Opened.class,
          store.openAttestedBook(
              RECORDED_AT, SqlitePostingFactFixtureSupport.bookIdentity(), List.of(), genesis));
    }
  }

  private static List<AttestationEvidence> attestationEvidence(
      SqliteProtectedBookMaintenanceStore store, BookAccess bookAccess) {
    try (var verifiedBook = verifiedBook(store, bookAccess)) {
      return store.loadAttestationEvidence(verifiedBook);
    }
  }
}
