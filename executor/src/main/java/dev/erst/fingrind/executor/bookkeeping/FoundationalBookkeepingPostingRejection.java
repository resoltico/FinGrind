package dev.erst.fingrind.executor.bookkeeping;

/**
 * Closed classification for local posting rejections evaluated before workflow-specific admission.
 */
sealed interface FoundationalBookkeepingPostingRejection extends BookkeepingPostingRejection
    permits BookkeepingPostingRejection.BookNotInitialized,
        BookkeepingPostingRejection.AccountStateViolations,
        BookkeepingPostingRejection.EntrySemanticsViolations,
        BookkeepingPostingRejection.IdempotencyKeyConflict,
        BookkeepingPostingEffectiveDateBeforeBookStart,
        BookkeepingPostingRejection.PostingEffectiveDateInFuture,
        BookkeepingPostingRejection.BookFunctionalCurrencyMismatch,
        BookkeepingPostingRejection.SweptInterimResultViolation {}
