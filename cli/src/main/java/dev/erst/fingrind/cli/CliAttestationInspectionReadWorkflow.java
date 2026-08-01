package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import java.nio.file.Path;
import java.util.List;

/** Read-only attestation verification, review, and receipt capability for one protected book. */
interface CliAttestationInspectionReadWorkflow {
  /** Verifies the protected book's attestation history against its persisted evidence. */
  ContractDecision<VerifyBookAttestationResult> verifyBookAttestation(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews);

  /** Produces a human-reviewable, non-mutating summary of the book's attestation history. */
  ContractDecision<AttestationReviewResult> reviewAttestation(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews);

  /** Exports a signed attestation receipt without changing the protected book. */
  ContractDecision<ExportAttestationReceiptResult> exportAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath);

  /** Verifies a persisted receipt against the protected book without mutating either artifact. */
  ContractDecision<VerifyAttestationReceiptResult> verifyAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath);
}
