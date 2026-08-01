package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers receipt inspection and the retained, independently verifiable export flow. */
class AttestationInspectionServiceTest extends AttestationInspectionServiceTestSupport {
  @Test
  void verifiesReviewsExportsAndVerifiesAnIndependentlyRetainedReceipt() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    Files.createDirectories(bookPath.getParent());
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));
    BookAccess access = AttestationMaintenanceTestSupport.bookAccess(bookPath, credential);
    Path retainedDirectory = privateOutputDirectory("retained");
    Path receiptPath = retainedDirectory.resolve("book.fgar");

    VerifyBookAttestationResult.Valid verified =
        assertInstanceOf(
            VerifyBookAttestationResult.Valid.class,
            service.verifyBook(access, List.of()).requireAccepted());
    AttestationReviewResult.Valid reviewed =
        assertInstanceOf(
            AttestationReviewResult.Valid.class,
            service.review(access, List.of()).requireAccepted());
    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            service.exportReceipt(access, receiptPath).requireAccepted());
    VerifyAttestationReceiptResult.Valid verifiedReceipt =
        assertInstanceOf(
            VerifyAttestationReceiptResult.Valid.class,
            service.verifyReceipt(access, receiptPath).requireAccepted());

    assertEquals(0, verified.headOrder().intValueExact());
    assertEquals(verified.bookId(), reviewed.bookId());
    assertEquals(verified.operationHeadHex(), reviewed.operationHeadHex());
    assertEquals(List.of(), exported.warnings());
    assertEquals(receiptPath.toRealPath(), exported.receiptFilePath());
    assertTrue(Files.isRegularFile(exported.retainedStage().retainedStagePath()));
    assertEquals(exported.bookId(), verifiedReceipt.bookId());
    assertEquals(exported.operationOrder(), verifiedReceipt.operationOrder());
    assertEquals(exported.operationHeadHex(), verifiedReceipt.operationHeadHex());
  }

  @Test
  void marksReceiptsStoredBesideTheBookAsNonIndependent() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookDirectory = privateOutputDirectory("book");
    Path bookPath = bookDirectory.resolve("live.sqlite");
    AttestationInspectionService service = service(bookPath, List.of(genesis(credential)));

    ExportAttestationReceiptResult.Exported exported =
        assertInstanceOf(
            ExportAttestationReceiptResult.Exported.class,
            service
                .exportReceipt(
                    AttestationMaintenanceTestSupport.bookAccess(bookPath, credential),
                    bookDirectory.resolve("receipt.fgar"))
                .requireAccepted());

    assertEquals(List.of("receipt-not-independent"), exported.warnings());
    assertEquals(bookDirectory.toRealPath().resolve("receipt.fgar"), exported.receiptFilePath());
  }

  @Test
  void projectsCredentialAdmissionRejectionsAsPublishedReceiptAuthorizationOutcomes()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("book/live.sqlite");
    BookAccess duplicateCredentialAccess =
        new BookAccess(
            bookPath,
            new BookAccess.PassphraseSource.KeyFile(bookPath.resolveSibling("book.key")),
            List.of(credential.source(), credential.source()));

    ExportAttestationReceiptResult.AuthorizationRejected rejected =
        assertInstanceOf(
            ExportAttestationReceiptResult.AuthorizationRejected.class,
            new AttestationReceiptExportOperations(CLOCK)
                .export(
                    duplicateCredentialAccess,
                    privateOutputDirectory("retained").resolve("duplicate.fgar"),
                    List.of(genesis(credential)))
                .requireAccepted());

    assertEquals(AttestationVerificationFailure.DUPLICATE_PRINCIPAL, rejected.failure());
  }

  @Test
  void treatsThePlatformRootAsWithinTheBookTrustBoundary() {
    Path root = FileSystems.getDefault().getRootDirectories().iterator().next();

    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY,
        AttestationReceiptVerificationOperations.publicationReceiptRetention(
            temporaryDirectory.resolve("book/live.sqlite"), root));
  }
}
