package dev.erst.fingrind.executor.bookkeeping;

/**
 * Closed classification for local posting rejections specific to workflow and reversal admission.
 */
sealed interface WorkflowBookkeepingPostingRejection extends BookkeepingPostingRejection
    permits BookkeepingPostingRejection.OpeningPositionWindowClosed,
        BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount,
        BookkeepingPostingRejection.ReservedResultClassification,
        BookkeepingPostingRejection.ReversalTargetNotFound,
        ReversalTargetIsReversal,
        BookkeepingPostingRejection.ReversalAlreadyExists,
        BookkeepingPostingRejection.ReversalDoesNotNegateTarget {}
