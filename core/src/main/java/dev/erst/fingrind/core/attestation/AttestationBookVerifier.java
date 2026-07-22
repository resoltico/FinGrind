package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Walks immutable protected-book evidence from genesis and reports only verified review findings.
 */
final class AttestationBookVerifier {
  private static final AttestationHash ZERO_HEAD =
      AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]);

  private AttestationBookVerifier() {}

  static AttestationBookVerification verify(AttestationBook book) {
    return verify(book, List.of());
  }

  static AttestationBookVerification verify(
      AttestationBook book, List<AttestationCompromiseReview> compromiseReviews) {
    AttestationBook checkedBook = Objects.requireNonNull(book, "book");
    List<AttestationCompromiseReview> checkedReviews =
        AttestationCompromiseReview.canonicalize(compromiseReviews);
    List<AttestationBookVerification.VerifiedOperation> verifiedOperations = new ArrayList<>();
    AttestationRegistryHistory registryHistory = null;
    UUID bookId = null;
    AttestationHash expectedPreviousHead = ZERO_HEAD;
    BigInteger expectedOrder = BigInteger.ZERO;
    for (AttestationBookOperation operation : checkedBook.operations()) {
      AttestationBookOperation checkedOperation = Objects.requireNonNull(operation, "operations");
      AttestationOperationPayload payload = checkedOperation.envelope().payload();
      requireBoundPreimages(payload, checkedOperation);
      if (payload.operationOrder().signum() == 0) {
        if (!expectedOrder.equals(BigInteger.ZERO)) {
          throw previousHeadFailure();
        }
        AttestationGenesisAuthorizationContext genesis =
            AttestationGenesisAuthorizationContext.verify(
                payload, checkedOperation.requestPreimage(), checkedOperation.effectPreimage());
        AttestationAuthorization.requireGenesis(
            genesis, checkedOperation.envelope().authorizationEnvelope());
        bookId = payload.bookId();
        registryHistory = AttestationRegistryHistory.genesis(genesis);
        registryHistory.requireAcceptedState();
      } else {
        AttestationOperationKind operationKind =
            AttestationOperationKind.forWireToken(payload.operationKind());
        AttestationVerifiedOperationProvenance provenance =
            AttestationOperationProfile.requireValid(
                payload,
                operationKind,
                checkedOperation.requestPreimage(),
                checkedOperation.effectPreimage());
        if (registryHistory == null) {
          throw previousHeadFailure();
        }
        AttestationRegistryHistory checkedRegistryHistory = registryHistory;
        AttestationRegistryHistory nextRegistryHistory =
            checkedRegistryHistory.preview(
                operationKind,
                payload.operationOrder(),
                checkedOperation.requestPreimage(),
                checkedOperation.effectPreimage());
        requireChainPosition(payload, bookId, expectedOrder, expectedPreviousHead);
        AttestationAuthorization.requireAuthorized(
            checkedRegistryHistory.registry(),
            AttestationAuthorizationContext.operation(payload, provenance),
            checkedOperation.envelope().authorizationEnvelope());
        AttestationSystemDerivation.requireValid(
            checkedRegistryHistory.registry(),
            payload,
            operationKind,
            provenance,
            checkedOperation.requestPreimage(),
            checkedOperation.effectPreimage());
        nextRegistryHistory.requireAcceptedState();
        registryHistory = nextRegistryHistory;
      }
      requireChainPosition(payload, bookId, expectedOrder, expectedPreviousHead);
      AttestationHash head = checkedOperation.envelope().head();
      verifiedOperations.add(
          new AttestationBookVerification.VerifiedOperation(
              payload.operationOrder(), head, checkedOperation));
      expectedPreviousHead = head;
      expectedOrder = expectedOrder.add(BigInteger.ONE);
    }
    AttestationRegistryHistory finalRegistryHistory =
        Objects.requireNonNull(registryHistory, "book must contain genesis");
    return new AttestationBookVerification(
        Objects.requireNonNull(bookId, "bookId"),
        verifiedOperations,
        finalRegistryHistory.registry(),
        reviewFindings(verifiedOperations, checkedReviews));
  }

  private static void requireBoundPreimages(
      AttestationOperationPayload payload, AttestationBookOperation operation) {
    if (!payload
            .requestDigest()
            .equals(AttestationHash.sha256(operation.requestPreimage().encoded()))
        || !payload
            .effectDigest()
            .equals(AttestationHash.sha256(operation.effectPreimage().encoded()))) {
      throw new AttestationAuthorizationException(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
  }

  private static void requireChainPosition(
      AttestationOperationPayload payload,
      @Nullable UUID expectedBookId,
      BigInteger expectedOrder,
      AttestationHash expectedPreviousHead) {
    UUID checkedBookId = Objects.requireNonNull(expectedBookId, "expectedBookId");
    List<Boolean> chainPosition =
        List.of(
            payload.bookId().equals(checkedBookId),
            payload.operationOrder().equals(expectedOrder),
            Arrays.equals(payload.previousHead().bytes(), expectedPreviousHead.bytes()));
    if (chainPosition.contains(false)) {
      throw previousHeadFailure();
    }
  }

  private static List<AttestationReviewFinding> reviewFindings(
      List<AttestationBookVerification.VerifiedOperation> operations,
      List<AttestationCompromiseReview> compromiseReviews) {
    List<AttestationReviewFinding> findings = new ArrayList<>();
    for (AttestationBookVerification.VerifiedOperation operation : operations) {
      for (AttestationCompromiseReview review : compromiseReviews) {
        if (review.includes(operation.operationOrder())
            && operation.operation().envelope().authorizationEnvelope().entries().stream()
                .anyMatch(entry -> entry.keyId().equals(review.keyId()))) {
          findings.add(new AttestationReviewFinding(review, operation.operationOrder()));
        }
      }
    }
    return List.copyOf(findings);
  }

  private static AttestationAuthorizationException previousHeadFailure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID);
  }
}
