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
    VerificationProgress progress = new VerificationProgress();
    for (AttestationBookOperation operation : checkedBook.operations()) {
      progress.verify(Objects.requireNonNull(operation, "operations"));
    }
    return progress.complete(checkedReviews);
  }

  /**
   * Holds the single ordered verification cursor for one immutable protected-book evidence chain.
   */
  private static final class VerificationProgress {
    private final List<AttestationBookVerification.VerifiedOperation> verifiedOperations =
        new ArrayList<>();
    private @Nullable VerificationHistories histories;
    private @Nullable UUID bookId;
    private AttestationHash expectedPreviousHead = ZERO_HEAD;
    private BigInteger expectedOrder = BigInteger.ZERO;

    private void verify(AttestationBookOperation checkedOperation) {
      AttestationOperationPayload payload = checkedOperation.envelope().payload();
      requireBoundPreimages(payload, checkedOperation);
      if (payload.operationOrder().signum() == 0) {
        verifyGenesis(payload, checkedOperation);
      } else {
        verifySuccessor(payload, checkedOperation);
      }
      requireExpectedChainPosition(payload);
      acceptVerifiedOperation(payload, checkedOperation);
    }

    private void verifyGenesis(
        AttestationOperationPayload payload, AttestationBookOperation checkedOperation) {
      if (!expectedOrder.equals(BigInteger.ZERO)) {
        throw previousHeadFailure();
      }
      AttestationGenesisAuthorizationContext genesis =
          AttestationGenesisAuthorizationContext.verify(
              payload, checkedOperation.requestPreimage(), checkedOperation.effectPreimage());
      AttestationAuthorization.requireGenesis(
          genesis, checkedOperation.envelope().authorizationEnvelope());
      AttestationRegistryHistory registryHistory = AttestationRegistryHistory.genesis(genesis);
      AttestationPeriodCloseHistory closeHistory =
          AttestationPeriodCloseHistory.genesis(checkedOperation.effectPreimage());
      AttestationLifecycleHistory lifecycleHistory = AttestationLifecycleHistory.genesis();
      registryHistory.requireAcceptedState();
      bookId = payload.bookId();
      histories = new VerificationHistories(registryHistory, closeHistory, lifecycleHistory);
    }

    private void verifySuccessor(
        AttestationOperationPayload payload, AttestationBookOperation checkedOperation) {
      AttestationOperationKind operationKind =
          AttestationOperationKind.forWireToken(payload.operationKind());
      AttestationVerifiedOperationProvenance provenance =
          AttestationOperationProfile.requireValid(
              payload,
              operationKind,
              checkedOperation.requestPreimage(),
              checkedOperation.effectPreimage());
      VerificationHistories currentHistories = requireHistories();
      AttestationLifecycleHistory nextLifecycleHistory =
          currentHistories
              .lifecycleHistory()
              .accept(
                  operationKind,
                  checkedOperation.requestPreimage(),
                  checkedOperation.effectPreimage());
      AttestationRegistryHistory nextRegistryHistory =
          currentHistories
              .registryHistory()
              .preview(
                  operationKind,
                  payload.operationOrder(),
                  checkedOperation.requestPreimage(),
                  checkedOperation.effectPreimage());
      requireExpectedChainPosition(payload);
      requireVerifiedBackupSource(operationKind, checkedOperation.requestPreimage());
      AttestationAuthorization.requireAuthorized(
          currentHistories.registryHistory().registry(),
          AttestationAuthorizationContext.operation(payload, provenance),
          checkedOperation.envelope().authorizationEnvelope());
      nextRegistryHistory.requireAcceptedState();
      AttestationPeriodCloseHistory nextCloseHistory =
          nextCloseHistory(currentHistories, payload, operationKind, provenance, checkedOperation);
      histories =
          new VerificationHistories(nextRegistryHistory, nextCloseHistory, nextLifecycleHistory);
    }

    private void requireExpectedChainPosition(AttestationOperationPayload payload) {
      requireChainPosition(payload, bookId, expectedOrder, expectedPreviousHead);
    }

    private void requireVerifiedBackupSource(
        AttestationOperationKind operationKind, AttestationPreimage requestPreimage) {
      if (operationKind == AttestationOperationKind.BACKUP_CREATED) {
        AttestationLifecycleEffectProfile.requireVerifiedBackupSource(
            requestPreimage, verifiedOperations);
      }
    }

    private static AttestationPeriodCloseHistory nextCloseHistory(
        VerificationHistories currentHistories,
        AttestationOperationPayload payload,
        AttestationOperationKind operationKind,
        AttestationVerifiedOperationProvenance provenance,
        AttestationBookOperation checkedOperation) {
      if (provenance.sourceChannel() == AttestationSourceChannel.SYSTEM) {
        return AttestationSystemDerivation.requireValid(
            currentHistories.closeHistory(),
            currentHistories.registryHistory().registry(),
            payload,
            operationKind,
            provenance,
            checkedOperation.requestPreimage(),
            checkedOperation.effectPreimage());
      }
      return currentHistories
          .closeHistory()
          .accept(operationKind, checkedOperation.effectPreimage());
    }

    private VerificationHistories requireHistories() {
      if (histories == null) {
        throw previousHeadFailure();
      }
      return histories;
    }

    private void acceptVerifiedOperation(
        AttestationOperationPayload payload, AttestationBookOperation checkedOperation) {
      AttestationHash head = checkedOperation.envelope().head();
      verifiedOperations.add(
          new AttestationBookVerification.VerifiedOperation(
              payload.operationOrder(), head, checkedOperation));
      expectedPreviousHead = head;
      expectedOrder = expectedOrder.add(BigInteger.ONE);
    }

    private AttestationBookVerification complete(
        List<AttestationCompromiseReview> compromiseReviews) {
      VerificationHistories finalHistories = requireHistories();
      List<AttestationCompromiseReview> checkedReviews =
          AttestationCompromiseReview.requireValidForVerifiedHead(
              verifiedOperations.getLast().operationOrder(), compromiseReviews);
      return new AttestationBookVerification(
          Objects.requireNonNull(bookId, "bookId"),
          verifiedOperations,
          finalHistories.registryHistory().registry(),
          reviewFindings(verifiedOperations, checkedReviews));
    }
  }

  private record VerificationHistories(
      AttestationRegistryHistory registryHistory,
      AttestationPeriodCloseHistory closeHistory,
      AttestationLifecycleHistory lifecycleHistory) {}

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
