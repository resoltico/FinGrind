package dev.erst.fingrind.executor.bookkeeping;

/** Resolution outcome for selecting one declared close-target account. */
public sealed interface CloseTargetSelection
    permits AcceptedCloseTargetSelection, RejectedCloseTargetSelection {}
