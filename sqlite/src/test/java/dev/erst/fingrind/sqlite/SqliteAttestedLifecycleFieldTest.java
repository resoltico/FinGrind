package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
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

      assertInstanceOf(
          ProtectedBookRekeyOutcome.Rekeyed.class,
          acceptedValue(
              workflow.rekeyBook(localAccess(sourceAccess), rekeyedBookKeyPath, signingSession)));

      assertInstanceOf(
          ProtectedBookRestoreOutcome.Restored.class,
          acceptedValue(
              workflow.restoreBook(
                  restoredBookPath,
                  restoredBookKeyPath,
                  backupPath,
                  backupKeyPath,
                  signingSession)));
    }

    assertEquals(
        3,
        attestationEvidence(store, bookAccessWithKey(sourceBookPath, rekeyedBookKeyPath)).size());
    assertEquals(
        2,
        attestationEvidence(store, bookAccessWithKey(restoredBookPath, restoredBookKeyPath))
            .size());
  }

  private AttestationCredentialSource createFounderCredential() throws Exception {
    Path credentialDirectory = tempDirectory.resolve("attestation");
    Files.createDirectories(credentialDirectory);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(credentialDirectory);
    Path encryptedKeyPath = credentialDirectory.resolve("founder.fgatk");
    Path passphrasePath = credentialDirectory.resolve("founder.passphrase");
    Files.writeString(passphrasePath, "field-test-attestation-passphrase\n");
    return new AttestationCredentialSource(PRINCIPAL_ID, encryptedKeyPath, passphrasePath);
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
        AttestationGenesisFactory.create(
            SqlitePostingFactFixtureSupport.bookIdentity(),
            RECORDED_AT,
            List.of(
                new AttestationFounderInput(
                    credentialSource.principalId(),
                    credentialSource.encryptedKeyFilePath(),
                    credentialSource.passphraseFilePath())));
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
