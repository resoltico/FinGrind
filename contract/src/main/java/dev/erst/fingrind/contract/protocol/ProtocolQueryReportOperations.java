package dev.erst.fingrind.contract.protocol;

import java.util.ArrayList;
import java.util.List;

/** Declarative report-operation builder for the published query catalog. */
final class ProtocolQueryReportOperations {
  /** Closed syntactic shapes for public report invocations. */
  enum ReportShape {
    ACCOUNT_WINDOW,
    ACCOUNT_LEDGER,
    AS_OF,
    INVENTORY_VALUATION,
    ACCRUAL_CUTOFF_SCHEDULE,
    FIXED_ASSET_REGISTER,
    BOOK_WIDE,
    PERIOD,
    PERIOD_WITH_COMPARATIVE,
    PERIOD_WITH_POSTING_COVERAGE,
    TAX_REGISTRATION_PERIOD
  }

  private ProtocolQueryReportOperations() {}

  static ProtocolOperation reportOperation(
      OperationId operationId,
      String title,
      ReportShape reportShape,
      String description,
      String example) {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            operationId,
            OperationCategory.QUERY,
            title,
            List.of(),
            invocationSyntax(reportShape),
            ExecutionMode.JSON_ENVELOPE,
            pdfOutputModes(),
            List.of(ProtocolArtifactOutput.pdf()),
            description,
            List.of(ProtocolExampleStep.command(example))));
  }

  private static List<String> invocationSyntax(ReportShape reportShape) {
    return switch (reportShape) {
      case ACCOUNT_WINDOW ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              ProtocolOptions.Request.ACCOUNT_CODE + " <account-code>",
              "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
              "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
              ProtocolOptions.optionalPostingCoverageSyntax(),
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case ACCOUNT_LEDGER ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              ProtocolOptions.Request.ACCOUNT_CODE + " <account-code>",
              "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
              "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
              ProtocolOptions.optionalPostingCoverageSyntax(),
              ProtocolOptions.optionalLimitSyntax(),
              ProtocolOptions.optionalCursorSyntax(),
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case AS_OF ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              "[" + ProtocolOptions.DateRange.EFFECTIVE_DATE_AS_OF + " <YYYY-MM-DD>]",
              ProtocolOptions.optionalAsOfComparativeSyntax(),
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case INVENTORY_VALUATION ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              "[" + ProtocolOptions.DateRange.AS_OF + " <YYYY-MM-DD>]",
              "[" + ProtocolOptions.DateRange.MOVEMENTS + "]",
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case ACCRUAL_CUTOFF_SCHEDULE ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              "[" + ProtocolOptions.DateRange.AS_OF + " <YYYY-MM-DD>]",
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case FIXED_ASSET_REGISTER ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              "[" + ProtocolOptions.DateRange.AS_OF + " <YYYY-MM-DD>]",
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case BOOK_WIDE ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
      case PERIOD, PERIOD_WITH_COMPARATIVE, PERIOD_WITH_POSTING_COVERAGE ->
          periodInvocationSyntax(reportShape);
      case TAX_REGISTRATION_PERIOD ->
          List.of(
              ProtocolBookAccessOptions.BOOK_FILE + " <path>",
              ProtocolOptions.currentPassphraseSourceSyntax(),
              ProtocolOptions.Request.TAX_REGISTRATION_ID + " <tax-registration-id>",
              ProtocolOptions.DateRange.PERIOD_START + " <YYYY-MM-DD>",
              ProtocolOptions.DateRange.PERIOD_END + " <YYYY-MM-DD>",
              ProtocolOptions.optionalPdfOutSyntax(),
              ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
    };
  }

  private static List<String> periodInvocationSyntax(ReportShape reportShape) {
    List<String> invocationSyntax =
        new ArrayList<>(
            List.of(
                ProtocolBookAccessOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.DateRange.PERIOD_START + " <YYYY-MM-DD>",
                ProtocolOptions.DateRange.PERIOD_END + " <YYYY-MM-DD>"));
    if (reportShape == ReportShape.PERIOD_WITH_COMPARATIVE) {
      invocationSyntax.add(ProtocolOptions.optionalPeriodComparativeSyntax());
    }
    if (reportShape == ReportShape.PERIOD_WITH_POSTING_COVERAGE) {
      invocationSyntax.add(ProtocolOptions.optionalPostingCoverageSyntax());
    }
    invocationSyntax.add(ProtocolOptions.optionalPdfOutSyntax());
    invocationSyntax.add(ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
    return List.copyOf(invocationSyntax);
  }

  private static List<OutputMode> pdfOutputModes() {
    return List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV);
  }
}
