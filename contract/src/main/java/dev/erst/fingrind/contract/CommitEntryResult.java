package dev.erst.fingrind.contract;

/** Result family returned by posting commit, which can commit or reject but never preflight. */
public sealed interface CommitEntryResult extends PostEntryResult
    permits PostEntryResult.Committed, PostEntryResult.CommitRejected {}
