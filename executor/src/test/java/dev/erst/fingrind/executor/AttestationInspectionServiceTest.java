package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers read-only attestation review and independently retained receipt lifecycle semantics. */
class AttestationInspectionServiceTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  @Test
  void verifiesReviewsExportsAndVerifiesAnIndependentlyRetainedReceipt() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    Path retainedDirectory = Files.createDirectories(temporaryDirectory.resolve("retained"));
    Path receiptPath = retainedDirectory.resolve("book.fgar");

    VerifyBookAttestationResult.Valid verified =
        assertInstanceOf(
            VerifyBookAttestationResult.Valid.class, service.verifyBook(access).requireAccepted());
    assertEquals(0, verified.headOrder().intValueExact());
    assertEquals(verified.bookId(), service.review(access).requireAccepted().bookId());

    ExportAttestationReceiptResult exported =
        service.exportReceipt(access, receiptPath).requireAccepted();
    assertEquals(List.of(), exported.warnings());
    assertTrue(Files.isRegularFile(receiptPath));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Valid.class,
        service.verifyReceipt(access, receiptPath).requireAccepted());
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        service.verifyReceipt(access, retainedDirectory.resolve("missing.fgar")).requireAccepted());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        service.exportReceipt(access, receiptPath));
    Files.writeString(retainedDirectory.resolve("malformed.fgar"), "not an attestation receipt");
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        service
            .verifyReceipt(access, retainedDirectory.resolve("malformed.fgar"))
            .requireAccepted());
    Path alteredReceiptPath = retainedDirectory.resolve("altered.fgar");
    byte[] alteredReceipt = Files.readAllBytes(receiptPath);
    alteredReceipt[alteredReceipt.length - 1] ^= 1;
    Files.write(alteredReceiptPath, alteredReceipt);
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        service.verifyReceipt(access, alteredReceiptPath).requireAccepted());
    Path unreadableReceiptPath = retainedDirectory.resolve("unreadable.fgar");
    Files.writeString(unreadableReceiptPath, "unreadable receipt");
    Files.setPosixFilePermissions(unreadableReceiptPath, Set.of());
    try {
      assertInstanceOf(
          dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
          service.verifyReceipt(access, unreadableReceiptPath));
    } finally {
      Files.setPosixFilePermissions(
          unreadableReceiptPath,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        service.exportReceipt(access, Path.of("/dev/fingrind-receipt-output.fgar")));
  }

  @Test
  void marksReceiptsStoredBesideTheBookAsNonIndependent() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = Files.createDirectories(temporaryDirectory.resolve("book"));
    Path bookPath = bookDirectory.resolve("live.sqlite");
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));

    ExportAttestationReceiptResult exported =
        service
            .exportReceipt(
                AttestationMaintenanceTestSupport.bookAccess(bookPath, credential),
                bookDirectory.resolve("receipt.fgar"))
            .requireAccepted();

    assertEquals(List.of("receipt-not-independent"), exported.warnings());
  }

  @Test
  void separatesStructuralInvalidityAndCredentialAdmissionFromReadableBookVerification()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationInspectionService structurallyInvalid = service(bookPath, List.of());

    assertInstanceOf(
        VerifyBookAttestationResult.Invalid.class,
        structurallyInvalid.verifyBook(access).requireAccepted());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        structurallyInvalid.review(access));

    AttestationInspectionService valid = service(bookPath, List.of(genesis(credential)));
    BookAccess credentialFreeAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(credentialFreeAccess, temporaryDirectory.resolve("receipt.fgar")));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        structurallyInvalid.exportReceipt(access, temporaryDirectory.resolve("receipt.fgar")));

    BookAccess unreadableCredentialAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of(
                new AttestationCredentialSource(
                    credential.source().principalId(),
                    temporaryDirectory.resolve("missing.fgatk"),
                    temporaryDirectory.resolve("missing.passphrase"))));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(
            unreadableCredentialAccess, temporaryDirectory.resolve("receipt.fgar")));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        valid.exportReceipt(access, temporaryDirectory.resolve("missing-parent/receipt.fgar")));

    Path retainedDirectory = Files.createDirectories(temporaryDirectory.resolve("retained"));
    Path receiptPath = retainedDirectory.resolve("book.fgar");
    valid.exportReceipt(access, receiptPath).requireAccepted();
    AttestationInspectionService changedBook =
        service(
            bookPath,
            List.of(
                AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT.plusSeconds(1))));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        changedBook.verifyReceipt(access, receiptPath).requireAccepted());
    BookAccess rootPathAccess =
        new BookAccess(
            Path.of("/"),
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of());
    assertInstanceOf(
        VerifyAttestationReceiptResult.Valid.class,
        valid.verifyReceipt(rootPathAccess, receiptPath).requireAccepted());
    AttestationInspectionService malformedEvidence =
        service(bookPath, List.of(new AttestationEvidence(new byte[0], new byte[0], new byte[0])));
    assertInstanceOf(
        VerifyAttestationReceiptResult.Invalid.class,
        malformedEvidence.verifyReceipt(access, receiptPath).requireAccepted());
  }

  @Test
  void rejectsUnreadableProtectedBookStatesBeforeReadingAttestationEvidence() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    AttestationMaintenanceTestSupport.Store verificationFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    verificationFailure.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath, ProtectedBookVerificationFailure.MISSING)));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        new AttestationInspectionService(CLOCK, verificationFailure).verifyBook(access));

    AttestationMaintenanceTestSupport.Store storageFailure =
        new AttestationMaintenanceTestSupport.Store(bookPath, List.of(genesis(credential)));
    storageFailure.setLiveVerification(
        MaintenanceDecision.failed(
            new MaintenanceFailure(
                ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
                "simulated protected-book storage failure",
                null,
                null,
                null)));
    assertInstanceOf(
        dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class,
        new AttestationInspectionService(CLOCK, storageFailure).verifyBook(access));
  }

  private AttestationInspectionService service(Path bookPath, List<AttestationEvidence> evidence) {
    return new AttestationInspectionService(
        CLOCK, new AttestationMaintenanceTestSupport.Store(bookPath, evidence));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private static AttestationEvidence genesis(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT);
  }
}
