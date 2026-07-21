package dev.erst.fingrind.executor.bookkeeping;

/** Refusal for a fiscal-year close that would not persist any generated close posting. */
public record FiscalYearCloseRequiresGeneratedPostings()
    implements BookkeepingAdministrationRejection {}
