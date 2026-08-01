package dev.erst.fingrind.contract.bookkeeping;

/** Closed classification for posting rejections specific to workflow and reversal admission. */
sealed interface WorkflowPostingRejection extends PostingRejection
    permits PostingRejection.OpeningPositionWindowClosed,
        PostingRejection.OpeningPositionTouchesNominalAccount,
        PostingRejection.ReservedResultClassification,
        PostingRejection.ReversalTargetNotFound,
        ReversalTargetIsReversal,
        PostingRejection.ReversalAlreadyExists,
        PostingRejection.ReversalDoesNotNegateTarget {}
