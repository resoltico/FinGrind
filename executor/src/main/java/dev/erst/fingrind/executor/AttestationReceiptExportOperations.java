package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationCredentialUseException;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationReceipt;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Signs one verified receipt before its dedicated no-clobber publication step. */
final class AttestationReceiptExportOperations {
  private final Clock clock;

  AttestationReceiptExportOperations(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  ContractDecision<ExportAttestationReceiptResult> export(
      BookAccess bookAccess, Path receiptPath, List<AttestationEvidence> evidence) {
    AttestationVerification verification;
    try {
      verification = AttestationVerifier.verifyBook(evidence);
    } catch (AttestationVerificationException exception) {
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.VerificationRejected(
              AttestationVerificationFailure.fromWireCode(exception.code())));
    }
    List<AttestationCredentialSource> sources;
    try {
      sources = bookAccess.requireAttestationCredentialSources();
    } catch (IllegalStateException exception) {
      return AttestationCredentialRefusals.forReceiptExport(bookAccess.bookFilePath());
    }
    byte[] receipt;
    try (AttestationSigningSession session = AttestationSigningSessionFactory.open(sources)) {
      receipt =
          session.createReceipt(
              verification.bookId(),
              verification.headOrder(),
              verification.operationHead(),
              clock.instant());
    } catch (AttestationAdmissionRejectedException exception) {
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.AuthorizationRejected(
              AttestationVerificationFailure.fromWireCode(exception.failure().code())));
    } catch (AttestationCredentialException | AttestationCredentialUseException exception) {
      return AttestationCredentialRefusals.forReceiptExport(bookAccess.bookFilePath());
    }
    try {
      AttestationReceipt.verify(receipt, evidence, AttestationReceiptRetention.INDEPENDENT);
    } catch (AttestationAuthorizationException exception) {
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.AuthorizationRejected(
              AttestationVerificationFailure.fromWireCode(exception.failure().code())));
    }
    return AttestationReceiptPublicationOperations.publish(
        receiptPath, receipt, bookAccess.bookFilePath(), verification);
  }
}
