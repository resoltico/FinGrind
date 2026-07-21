package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
