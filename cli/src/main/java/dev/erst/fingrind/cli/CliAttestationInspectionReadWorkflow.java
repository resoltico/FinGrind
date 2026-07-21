package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;

/** Read-only attestation verification, review, and receipt capability for one protected book. */
interface CliAttestationInspectionReadWorkflow {
  ContractDecision<VerifyBookAttestationResult> verifyBookAttestation(BookAccess bookAccess);

  ContractDecision<AttestationReviewResult> reviewAttestation(BookAccess bookAccess);

  ContractDecision<ExportAttestationReceiptResult> exportAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath);

  ContractDecision<VerifyAttestationReceiptResult> verifyAttestationReceipt(
      BookAccess bookAccess, Path receiptFilePath);
}
