package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationBookInspection;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Read-only verification, review, and independently retained receipt service for one book. */
public final class AttestationInspectionService {
  private final AttestationReceiptOperations receiptOperations;
  private final AttestedProtectedBookMaintenanceStore store;

  /** Creates one service over the mandatory persisted-attestation evidence boundary. */
  public AttestationInspectionService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.receiptOperations =
        new AttestationReceiptOperations(Objects.requireNonNull(clock, "clock"));
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
  }

  /** Verifies every immutable attestation structure from genesis to the current head. */
  public ContractDecision<VerifyBookAttestationResult> verifyBook(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    List<AttestationCompromiseReview> checkedReviews =
        List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    return readEvidence(checkedBookAccess)
        .fold(
            evidence -> {
              try {
                AttestationBookInspection inspection =
                    AttestationVerifier.verifyAndInspectBook(evidence, checkedReviews);
                return ContractDecision.accepted(validBookResult(inspection));
              } catch (AttestationVerificationException exception) {
                return ContractDecision.accepted(
                    new VerifyBookAttestationResult.Invalid(
                        AttestationVerificationFailure.requireWireCode(exception.code())));
              }
            },
            ContractDecision::rejected);
  }

  /** Returns the non-persisted compromise-review findings for a structurally valid book. */
  public ContractDecision<AttestationReviewResult> review(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    List<AttestationCompromiseReview> checkedReviews =
        List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    return readEvidence(checkedBookAccess)
        .fold(
            evidence -> {
              try {
                AttestationVerification verification =
                    AttestationVerifier.verifyBook(evidence, checkedReviews);
                return ContractDecision.accepted(
                    new AttestationReviewResult(
                        verification.bookId(),
                        verification.headOrder(),
                        verification.reviewFindings()));
              } catch (AttestationVerificationException exception) {
                return ContractDecision.rejected(
                    ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failureAt(
                        checkedBookAccess.bookFilePath(),
                        "The selected book's attestation chain is structurally invalid: "
                            + exception.code()
                            + ".",
                        "Run "
                            + OperationId.VERIFY_BOOK.wireName()
                            + " and repair from a valid independently retained backup or receipt.",
                        "--book-file"));
              }
            },
            ContractDecision::rejected);
  }

  /** Exports one non-mutating, quorum-signed receipt through atomic no-clobber publication. */
  public ContractDecision<ExportAttestationReceiptResult> exportReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return readEvidence(checkedBookAccess)
        .fold(
            evidence -> receiptOperations.export(checkedBookAccess, checkedReceiptPath, evidence),
            ContractDecision::rejected);
  }

  /** Verifies an independently retained receipt against the complete supplied book chain. */
  public ContractDecision<VerifyAttestationReceiptResult> verifyReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return readEvidence(checkedBookAccess)
        .fold(
            evidence ->
                receiptOperations.verify(
                    checkedBookAccess.bookFilePath(), checkedReceiptPath, evidence),
            ContractDecision::rejected);
  }

  private ContractDecision<List<AttestationEvidence>> readEvidence(BookAccess bookAccess) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    return store
        .verifyInitializedBook(
            ProtectedBookAccess.fromPublished(checkedBookAccess),
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)
        .fold(
            verification -> {
              if (verification instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
                try (verifiedBook) {
                  return ContractDecision.accepted(store.loadAttestationEvidence(verifiedBook));
                }
              }
              return ContractDecision.rejected(
                  ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failureAt(
                      checkedBookAccess.bookFilePath(),
                      "The selected protected book could not be opened and verified.",
                      "Confirm the book path and passphrase source, then retry.",
                      "--book-file"));
            },
            failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static VerifyBookAttestationResult.Valid validBookResult(
      AttestationBookInspection inspection) {
    AttestationVerification verification = inspection.verification();
    return new VerifyBookAttestationResult.Valid(
        verification.bookId(),
        verification.headOrder(),
        HexFormat.of().formatHex(verification.operationHead()),
        verification.reviewFindings(),
        inspection.registry());
  }
}
