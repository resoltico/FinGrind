package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

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

  /** Verifies one complete operation chain and reconstructs its current authority state. */
  public static AttestationBookInspection verifyAndInspectBook(
      List<AttestationEvidence> operations, List<AttestationCompromiseReview> compromiseReviews) {
    Objects.requireNonNull(operations, "operations");
    Objects.requireNonNull(compromiseReviews, "compromiseReviews");
    try {
      AttestationBookVerification verification = verifyEvidence(operations, compromiseReviews);
      return new AttestationBookInspection(
          new AttestationVerification(
              verification.bookId(),
              verification.headOrder(),
              verification.head().bytes(),
              verification.reviewFindings()),
          verification.registryInspection());
    } catch (AttestationAuthorizationException exception) {
      throw new AttestationVerificationException(exception.failure().code(), exception);
    }
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

  private static AttestationBookVerification verifyEvidence(
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
}
