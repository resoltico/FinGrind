package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import java.util.Objects;

/** Shared deterministic mapping from posting rejections to replay lifecycle status snapshots. */
public final class PostingLifecycleStatusMapper {
  private PostingLifecycleStatusMapper() {}

  /** Resolves the stable replay lifecycle status for one posting rejection family. */
  public static PostingLifecycleStatus forRejection(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> PostingLifecycleStatus.BOOK_NOT_INITIALIZED;
      case PostingRejection.AccountStateViolations violations ->
          accountStateViolationStatus(violations);
      case PostingRejection.EntrySemanticsViolations _ ->
          PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS;
      case PostingRejection.IdempotencyKeyConflict _ ->
          PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT;
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH;
      case PostingRejection.SweptInterimResultViolation _ ->
          PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION;
      case PostingRejection.OpeningPositionWindowClosed _ ->
          PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED;
      case PostingRejection.OpeningPositionTouchesNominalAccount _ ->
          PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT;
      case PostingRejection.ReservedResultClassification _ ->
          PostingLifecycleStatus.RESERVED_RESULT_CLASSIFICATION;
      case PostingRejection.ReversalTargetNotFound _ ->
          PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND;
      case PostingRejection.ReversalAlreadyExists _ ->
          PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
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
