package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import java.util.Objects;

/** Shared deterministic mapping from posting rejections to replay lifecycle status snapshots. */
public final class PostingLifecycleStatusMapper {
  private PostingLifecycleStatusMapper() {}

  /** Resolves the stable replay lifecycle status for one posting rejection family. */
  public static PostingLifecycleStatus forRejection(PostingRejection rejection) {
    PostingRejection requiredRejection = Objects.requireNonNull(rejection, "rejection");
    if (requiredRejection instanceof PostingRejection.BookNotInitialized) {
      return PostingLifecycleStatus.BOOK_NOT_INITIALIZED;
    }
    if (requiredRejection instanceof PostingRejection.AccountStateViolations violations) {
      return accountStateViolationStatus(violations);
    }
    if (requiredRejection instanceof PostingRejection.EntrySemanticsViolations) {
      return PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS;
    }
    if (requiredRejection instanceof PostingRejection.IdempotencyKeyConflict) {
      return PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT;
    }
    return detailedStatus(requiredRejection);
  }

  private static PostingLifecycleStatus detailedStatus(PostingRejection rejection) {
    if (rejection instanceof PostingRejection.PostingEffectiveDateInFuture) {
      return PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_IN_FUTURE;
    }
    if (rejection instanceof PostingRejection.BookFunctionalCurrencyMismatch) {
      return PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH;
    }
    if (rejection instanceof PostingRejection.SweptInterimResultViolation) {
      return PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION;
    }
    if (rejection instanceof PostingRejection.OpeningPositionWindowClosed) {
      return PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED;
    }
    if (rejection instanceof PostingRejection.OpeningPositionTouchesNominalAccount) {
      return PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT;
    }
    if (rejection instanceof PostingRejection.ReservedResultClassification) {
      return PostingLifecycleStatus.RESERVED_RESULT_CLASSIFICATION;
    }
    if (rejection instanceof PostingRejection.ReversalTargetNotFound) {
      return PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND;
    }
    if (rejection instanceof dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal) {
      return PostingLifecycleStatus.REVERSAL_TARGET_IS_REVERSAL;
    }
    if (rejection instanceof PostingRejection.ReversalAlreadyExists) {
      return PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS;
    }
    if (rejection instanceof PostingRejection.ReversalDoesNotNegateTarget) {
      return PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET;
    }
    throw new IllegalStateException(
        "Detailed posting rejection status dispatch received a family owned elsewhere.");
  }

  private static PostingLifecycleStatus accountStateViolationStatus(
      PostingRejection.AccountStateViolations accountStateViolations) {
    boolean allUnknown =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.UnknownAccount.class::isInstance);
    if (allUnknown) {
      return PostingLifecycleStatus.UNKNOWN_ACCOUNT;
    }
    boolean allInactive =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.InactiveAccount.class::isInstance);
    if (allInactive) {
      return PostingLifecycleStatus.INACTIVE_ACCOUNT;
    }
    return PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS;
  }
}
