package dev.erst.fingrind.executor.bookkeeping;

/** Resolution outcome for selecting the single result-holding account required by a close. */
public sealed interface ResultHoldingSelection
    permits AcceptedResultHoldingSelection, RejectedResultHoldingSelection {}
