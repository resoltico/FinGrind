package dev.erst.fingrind.contract;

/** Result family returned by posting preflight, which can validate or reject but never commit. */
public sealed interface PreflightEntryResult extends PostEntryResult
    permits PostEntryResult.PreflightAccepted, PostEntryResult.PreflightRejected {}
