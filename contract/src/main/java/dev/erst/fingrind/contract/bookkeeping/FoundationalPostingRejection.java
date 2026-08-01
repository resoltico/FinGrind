package dev.erst.fingrind.contract.bookkeeping;

/** Closed classification for posting rejections evaluated before workflow-specific admission. */
sealed interface FoundationalPostingRejection extends PostingRejection
    permits PostingRejection.BookNotInitialized,
        PostingRejection.AccountStateViolations,
        PostingRejection.EntrySemanticsViolations,
        PostingRejection.IdempotencyKeyConflict,
        PostingEffectiveDateBeforeBookStart,
        PostingRejection.PostingEffectiveDateInFuture,
        PostingRejection.BookFunctionalCurrencyMismatch,
        PostingRejection.SweptInterimResultViolation {}
