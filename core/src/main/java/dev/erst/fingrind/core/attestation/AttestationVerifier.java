package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Public, pure boundary for verification of persisted operation evidence. */
public final class AttestationVerifier {
  private AttestationVerifier() {}

  /**
   * Verifies one complete operation chain from genesis and returns its authenticated head.
   *
   * @throws AttestationVerificationException when the first canonical attestation rule fails
   */
  public static AttestationVerification verifyBook(List<AttestationEvidence> operations) {
    return verifyBook(operations, List.of());
  }

  /**
   * Verifies one complete operation chain and derives non-persisted compromise-review findings.
   *
   * @throws AttestationVerificationException when the first canonical attestation rule fails
   * @throws AttestationReviewWindowException when a review interval extends beyond the verified
   *     head
   */
  public static AttestationVerification verifyBook(
      List<AttestationEvidence> operations, List<AttestationCompromiseReview> compromiseReviews) {
    return verifyAndInspectBook(operations, compromiseReviews).verification();
  }

  /** Verifies one complete operation chain and reconstructs its current authority state. */
  public static AttestationBookInspection verifyAndInspectBook(
      List<AttestationEvidence> operations) {
    return verifyAndInspectBook(operations, List.of());
  }

  /**
   * Verifies one complete operation chain, reconstructs its current authority state, and derives
   * only review findings whose declared intervals fit the authenticated head.
   *
   * @throws AttestationReviewWindowException when a review interval extends beyond the verified
   *     head
   */
  public static AttestationBookInspection verifyAndInspectBook(
      List<AttestationEvidence> operations, List<AttestationCompromiseReview> compromiseReviews) {
    Objects.requireNonNull(operations, "operations");
    Objects.requireNonNull(compromiseReviews, "compromiseReviews");
    try {
      AttestationBookVerification verification = verifyEvidence(operations, compromiseReviews);
      return new AttestationBookInspection(
          publicVerification(verification), verification.registryInspection());
    } catch (AttestationAuthorizationException exception) {
      throw new AttestationVerificationException(exception.failure().code(), exception);
    }
  }

  /**
   * Verifies the complete chain and projects every signed posting fact to its committing operation
   * reference.
   *
   * <p>The projection is derived only after cryptographic and semantic chain verification. It is
   * therefore suitable for read-side provenance without creating a second persisted source of
   * truth.
   *
   * @throws AttestationVerificationException when the first canonical attestation rule fails
   */
  public static AttestationPostingCommitmentInspection verifyAndInspectPostingCommitments(
      List<AttestationEvidence> operations) {
    Objects.requireNonNull(operations, "operations");
    AttestationBookVerification verification;
    try {
      verification = verifyEvidence(operations, List.of());
    } catch (AttestationAuthorizationException exception) {
      throw new AttestationVerificationException(exception.failure().code(), exception);
    }
    Map<UUID, AttestationOperationCommitment> commitments = new ConcurrentHashMap<>();
    for (AttestationBookVerification.VerifiedOperation verifiedOperation :
        verification.operations()) {
      AttestationOperationCommitment commitment = operationCommitment(verifiedOperation);
      AttestationBookOperation operation = verifiedOperation.operation();
      AttestationOperationKind operationKind =
          AttestationOperationKind.forWireToken(operation.envelope().payload().operationKind());
      List<AttestationPreimage.Fact> effectFacts =
          operationKind == AttestationOperationKind.EXECUTE_PLAN
              ? AttestationPlanQualifiedFact.effectFacts(operation.effectPreimage())
              : operation.effectPreimage().records();
      for (AttestationPreimage.Fact fact : effectFacts) {
        if (fact.recordTypeTag() != 0x0020) {
          continue;
        }
        UUID postingId =
            AttestationPreimageValueReader.uuid(
                fact, 1, AttestationAuthorizationFailure.PREIMAGE_INVALID);
        if (commitments.putIfAbsent(postingId, commitment) != null) {
          throw new AttestationVerificationException(
              AttestationAuthorizationFailure.PREIMAGE_INVALID.code());
        }
      }
    }
    return new AttestationPostingCommitmentInspection(
        publicVerification(verification), commitments);
  }

  /**
   * Refuses one prospective registry mutation against the authenticated current authority state.
   *
   * <p>The caller must make this check in the same admission transaction that will append the
   * signed operation. The candidate verifier remains the independent proof that the persisted
   * history obeys the same rule.
   *
   * @throws AttestationVerificationException when the supplied existing chain is invalid
   * @throws AttestationAuthorizationException when the prospective mutation is not admissible
   */
  public static void requireRegistryMutationAdmissible(
      List<AttestationEvidence> operations, AttestationRegistryMutation mutation) {
    Objects.requireNonNull(operations, "operations");
    AttestationRegistryMutation checkedMutation = Objects.requireNonNull(mutation, "mutation");
    AttestationBookVerification verification;
    try {
      verification = verifyEvidence(operations, List.of());
    } catch (AttestationAuthorizationException exception) {
      throw new AttestationVerificationException(exception.failure().code(), exception);
    }
    verification.registry().requireMutationAdmissible(checkedMutation, verification.headOrder());
  }

  static AttestationBookVerification verifyEvidence(
      List<AttestationEvidence> operations, List<AttestationCompromiseReview> compromiseReviews) {
    if (operations.isEmpty()) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    return AttestationBookVerifier.verify(
        new AttestationBook(
            operations.stream()
                .map(
                    operation -> {
                      AttestationEvidence evidence =
                          Objects.requireNonNull(operation, "operations must not contain null");
                      return AttestationBookOperation.decode(
                          evidence.operationEnvelope(),
                          evidence.requestPreimage(),
                          evidence.effectPreimage());
                    })
                .toList()),
        compromiseReviews);
  }

  private static AttestationVerification publicVerification(
      AttestationBookVerification verification) {
    return new AttestationVerification(
        verification.bookId(),
        verification.headOrder(),
        verification.head().bytes(),
        verification.previousHead().bytes(),
        verification.reviewFindings());
  }

  private static AttestationOperationCommitment operationCommitment(
      AttestationBookVerification.VerifiedOperation operation) {
    return new AttestationOperationCommitment(operation.operationOrder(), operation.head().bytes());
  }
}
