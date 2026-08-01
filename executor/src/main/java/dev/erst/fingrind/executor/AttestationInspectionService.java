package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
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
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Read-only verification, review, and independently retained receipt service for one book. */
public final class AttestationInspectionService {
  private final AttestationReceiptExportOperations receiptExportOperations;
  private final AttestedProtectedBookMaintenanceStore store;

  /** Creates one service over the mandatory persisted-attestation evidence boundary. */
  public AttestationInspectionService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.receiptExportOperations =
        new AttestationReceiptExportOperations(Objects.requireNonNull(clock, "clock"));
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
  }

  /** Verifies every immutable attestation structure from genesis to the current head. */
  public ContractDecision<VerifyBookAttestationResult> verifyBook(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    List<AttestationCompromiseReview> checkedReviews =
        List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    return withCanonicalLiveBookAccess(
        checkedBookAccess,
        canonicalBookAccess ->
            readCanonicalEvidence(canonicalBookAccess)
                .fold(
                    evidence -> {
                      try {
                        AttestationBookInspection inspection =
                            AttestationVerifier.verifyAndInspectBook(evidence, checkedReviews);
                        return ContractDecision.accepted(validBookResult(inspection));
                      } catch (AttestationVerificationException exception) {
                        return ContractDecision.accepted(
                            new VerifyBookAttestationResult.Invalid(exception.code()));
                      }
                    },
                    ContractDecision::rejected));
  }

  /** Returns the non-persisted compromise-review findings for a structurally valid book. */
  public ContractDecision<AttestationReviewResult> review(
      BookAccess bookAccess, List<AttestationCompromiseReview> compromiseReviews) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    List<AttestationCompromiseReview> checkedReviews =
        List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    return withCanonicalLiveBookAccess(
        checkedBookAccess,
        canonicalBookAccess ->
            readCanonicalEvidence(canonicalBookAccess)
                .fold(
                    evidence -> {
                      try {
                        AttestationVerification verification =
                            AttestationVerifier.verifyBook(evidence, checkedReviews);
                        return ContractDecision.accepted(
                            new AttestationReviewResult.Valid(
                                verification.bookId(),
                                verification.headOrder(),
                                HexFormat.of().formatHex(verification.operationHead()),
                                verification.reviewFindings()));
                      } catch (AttestationVerificationException exception) {
                        return ContractDecision.accepted(
                            new AttestationReviewResult.Invalid(exception.code()));
                      }
                    },
                    ContractDecision::rejected));
  }

  /** Exports one non-mutating, quorum-signed receipt through atomic no-clobber publication. */
  public ContractDecision<ExportAttestationReceiptResult> exportReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return withCanonicalLiveBookAccess(
        checkedBookAccess,
        canonicalBookAccess ->
            readCanonicalEvidence(canonicalBookAccess)
                .fold(
                    evidence ->
                        receiptExportOperations.export(
                            canonicalBookAccess, checkedReceiptPath, evidence),
                    ContractDecision::rejected));
  }

  /** Verifies an independently retained receipt against the complete supplied book chain. */
  public ContractDecision<VerifyAttestationReceiptResult> verifyReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return withCanonicalLiveBookAccess(
        checkedBookAccess,
        canonicalBookAccess ->
            readCanonicalEvidence(canonicalBookAccess)
                .fold(
                    evidence ->
                        AttestationReceiptVerificationOperations.verify(
                            canonicalBookAccess.bookFilePath(), checkedReceiptPath, evidence),
                    ContractDecision::rejected));
  }

  /** Reads evidence only after the public live-book access tuple has been canonicalized once. */
  private ContractDecision<List<AttestationEvidence>> readCanonicalEvidence(BookAccess bookAccess) {
    BookAccess canonicalBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    try {
      return store
          .verifyInitializedBook(
              ProtectedBookAccess.fromPublished(canonicalBookAccess),
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)
          .fold(
              verification -> {
                if (verification
                    instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
                  try (verifiedBook) {
                    return ContractDecision.accepted(store.loadAttestationEvidence(verifiedBook));
                  }
                }
                return ContractDecision.rejected(
                    ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failureAt(
                        canonicalBookAccess.bookFilePath(),
                        "The selected protected book could not be opened and verified.",
                        "Confirm the book path and passphrase source, then retry.",
                        "--book-file"));
              },
              failure -> ContractDecision.rejected(failure.toContractFailure()));
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return ContractDecision.rejected(
          AttestationInspectionPathFailureMapper.toContractFailure(exception));
    }
  }

  /**
   * Applies one inspection operation only after its complete live-book access tuple is admitted.
   */
  private <T> ContractDecision<T> withCanonicalLiveBookAccess(
      BookAccess bookAccess, Function<BookAccess, ContractDecision<T>> inspection) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Function<BookAccess, ContractDecision<T>> checkedInspection =
        Objects.requireNonNull(inspection, "inspection");
    BookAccess canonicalBookAccess;
    try {
      canonicalBookAccess = canonicalizeLiveBookAccess(checkedBookAccess);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return ContractDecision.rejected(
          AttestationInspectionPathFailureMapper.toContractFailure(exception));
    }
    return checkedInspection.apply(canonicalBookAccess);
  }

  /**
   * Canonicalizes the complete public access tuple while retaining receipt-authorization sources.
   *
   * <p>The local maintenance projection deliberately carries no attestation credentials, so this
   * boundary rebuilds the public value instead of projecting it back with an empty credential list.
   */
  private BookAccess canonicalizeLiveBookAccess(BookAccess bookAccess) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    ProtectedBookAccess canonicalAccess =
        ProtectedBookAccess.canonicalizeLiveBookAccess(
            store, ProtectedBookAccess.fromPublished(checkedBookAccess));
    return new BookAccess(
        canonicalAccess.bookFilePath(),
        canonicalAccess.passphraseSource().toPublished(),
        checkedBookAccess.attestationCredentialSources());
  }

  private static VerifyBookAttestationResult.Valid validBookResult(
      AttestationBookInspection inspection) {
    AttestationVerification verification = inspection.verification();
    return new VerifyBookAttestationResult.Valid(
        verification.bookId(),
        verification.headOrder(),
        HexFormat.of().formatHex(verification.operationHead()),
        HexFormat.of().formatHex(verification.previousHead()),
        verification.reviewFindings(),
        inspection.registry());
  }
}
