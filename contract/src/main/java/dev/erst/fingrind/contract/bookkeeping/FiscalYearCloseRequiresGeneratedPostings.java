package dev.erst.fingrind.contract.bookkeeping;

/** Rejection for a fiscal-year close that would not persist any generated close posting. */
public record FiscalYearCloseRequiresGeneratedPostings() implements BookAdministrationRejection {}
