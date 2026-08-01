package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.SystemUtcClock;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.executor.AttestationInspectionService;
import dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;

/** SQLite implementation of non-mutating attestation inspection and receipt operations. */
interface SqliteCliAttestationInspectionOperations extends CliAttestationInspectionReadWorkflow {
  /** Supplies the book-passphrase resolver used when opening protected SQLite books. */
  CliBookPassphraseResolver passphraseResolver();

  @Override
  default ContractDecision<VerifyBookAttestationResult> verifyBookAttestation(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    return service().verifyBook(bookAccess, compromiseReviews);
  }

  @Override
  default ContractDecision<AttestationReviewResult> reviewAttestation(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    return service().review(bookAccess, compromiseReviews);
  }

  @Override
  default ContractDecision<ExportAttestationReceiptResult> exportAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    return service().exportReceipt(bookAccess, receiptFilePath);
  }

  @Override
  default ContractDecision<VerifyAttestationReceiptResult> verifyAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    return service().verifyReceipt(bookAccess, receiptFilePath);
  }

  private AttestationInspectionService service() {
    return new AttestationInspectionService(
        SystemUtcClock.instance(), new SqliteProtectedBookMaintenanceStore(passphraseResolver()));
  }
}
