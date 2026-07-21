package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies published maintenance commands preserve signed lifecycle outcomes and rejections. */
class ProtectedBookMaintenanceServiceTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @Test
  void projectsSuccessfulSignedBackupRestoreAndRekeyCommands() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    ProtectedBookMaintenanceService service = service(bookPath, credential);
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    assertInstanceOf(
        BackupBookResult.BackedUp.class,
        service.backupBook(access, backupPath, backupKeyPath, BACKUP_ID).requireAccepted());
    assertInstanceOf(
        RestoreBookResult.Restored.class,
        service
            .restoreBook(
                temporaryDirectory.resolve("restored/book.sqlite"),
                temporaryDirectory.resolve("restored/book.key"),
                backupPath,
                backupKeyPath,
                List.of(credential.source()))
            .requireAccepted());
    assertInstanceOf(
        RekeyBookResult.Rekeyed.class,
        service
            .rekeyBook(access, temporaryDirectory.resolve("rekeyed/book.key"))
            .requireAccepted());
  }

  @Test
  void rejectsPublishedMutationsWithoutAttestationCredentials() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    ProtectedBookMaintenanceService service = service(bookPath, credential);
    BookAccess credentialFreeAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());

    assertInstanceOf(
        ContractDecision.Rejected.class,
        service.backupBook(
            credentialFreeAccess,
            temporaryDirectory.resolve("retained/book.fgba"),
            temporaryDirectory.resolve("retained/book.key"),
            BACKUP_ID));
    assertInstanceOf(
        ContractDecision.Rejected.class,
        service.restoreBook(
            temporaryDirectory.resolve("restored/book.sqlite"),
            temporaryDirectory.resolve("restored/book.key"),
            temporaryDirectory.resolve("retained/book.fgba"),
            temporaryDirectory.resolve("retained/book.key"),
            List.of()));
    assertInstanceOf(
        ContractDecision.Rejected.class,
        service.rekeyBook(credentialFreeAccess, temporaryDirectory.resolve("rekeyed/book.key")));
  }

  @Test
  void projectsLocalStorageFailuresAsPublishedContractRejections() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);

    AttestationMaintenanceTestSupport.Store backupStore = store(bookPath, credential);
    backupStore.setPrepareFailure(new IllegalStateException("backup staging unavailable"));
    assertInstanceOf(
        ContractDecision.Rejected.class,
        new ProtectedBookMaintenanceService(CLOCK, backupStore)
            .backupBook(
                access,
                temporaryDirectory.resolve("retained/book.fgba"),
                temporaryDirectory.resolve("retained/book.key"),
                BACKUP_ID));

    AttestationMaintenanceTestSupport.Store restoreStore = store(bookPath, credential);
    restoreStore.setExistingLeaseFailure(new IllegalStateException("backup lease unavailable"));
    assertInstanceOf(
        ContractDecision.Rejected.class,
        new ProtectedBookMaintenanceService(CLOCK, restoreStore)
            .restoreBook(
                temporaryDirectory.resolve("restored/book.sqlite"),
                temporaryDirectory.resolve("restored/book.key"),
                temporaryDirectory.resolve("retained/book.fgba"),
                temporaryDirectory.resolve("retained/book.key"),
                List.of(credential.source())));

    AttestationMaintenanceTestSupport.Store rekeyStore = store(bookPath, credential);
    rekeyStore.setPrepareFailure(new IllegalStateException("rekey staging unavailable"));
    assertInstanceOf(
        ContractDecision.Rejected.class,
        new ProtectedBookMaintenanceService(CLOCK, rekeyStore)
            .rekeyBook(access, temporaryDirectory.resolve("rekeyed/book.key")));
  }

  private ProtectedBookMaintenanceService service(
      Path bookPath, AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new ProtectedBookMaintenanceService(CLOCK, store(bookPath, credential));
  }

  private AttestationMaintenanceTestSupport.Store store(
      Path bookPath, AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath, List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }
}
