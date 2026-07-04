package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical public close-operation definitions for the administration catalog. */
final class ProtocolPeriodCloseOperations {
  private ProtocolPeriodCloseOperations() {}

  static ProtocolOperation interimResultSweepOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.INTERIM_RESULT_SWEEP,
        OperationCategory.ADMINISTRATION,
        "Interim Result Sweep",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.THROUGH + " <YYYY-MM-DD>",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Sweep the derived contiguous reporting window into the policy-selected result-holding account.",
        List.of(
            ProtocolExampleStep.note(
                "Declare exactly one active EQUITY account classified as RESULT_HOLDING before sweeping a reporting period."),
            ProtocolExampleStep.note(
                "Declare exactly one active and postable EQUITY account classified as RESULT_HOLDING. Zero matching active accounts or multiple matching active accounts produce deterministic rejections."),
            ProtocolExampleStep.note(
                "The first sweep starts at book start in the selected book. After a sweep is recorded, later sweeps start on the day after the transferred-through horizon and remain inside a fiscal year."),
            ProtocolExampleStep.command(
                "fingrind interim-result-sweep --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --through 2026-04-30")));
  }

  static ProtocolOperation fiscalYearCloseOperation() {
    return ProtocolOperationDefinitions.operation(
        OperationId.FISCAL_YEAR_CLOSE,
        OperationCategory.ADMINISTRATION,
        "Fiscal-Year Close",
        List.of(),
        List.of(
            ProtocolOptions.BOOK_FILE + " <path>",
            ProtocolOptions.currentPassphraseSourceSyntax(),
            ProtocolOptions.YEAR + " <YYYY>",
            ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.JSON, OutputMode.TEXT),
        "Close the fiscal year by settling owner withdrawals into capital and accumulating current-year result into retained accumulated equity.",
        List.of(
            ProtocolExampleStep.note(
                "Declare exactly one active EQUITY account for each required close target: EQUITY_CONTRIBUTION, RESULT_HOLDING, and RETAINED_ACCUMULATED."),
            ProtocolExampleStep.note(
                "Use the fiscal year label. FinGrind derives the year boundaries from the selected book fiscal-year start and sweeps any unswept remaining profit-and-loss movement into RESULT_HOLDING before the year-end close settles the year."),
            ProtocolExampleStep.command(
                "fingrind fiscal-year-close --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --year 2026")));
  }
}
