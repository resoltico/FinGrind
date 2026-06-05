package dev.erst.fingrind.contract.protocol;

import java.util.ArrayList;
import java.util.List;

/** Shared report-operation builders for the published query catalog. */
final class ProtocolQueryReportOperations {
  private ProtocolQueryReportOperations() {}

  static ProtocolOperation accountWindowReportOperation(
      OperationId operationId, String title, String description, String example) {
    return pdfQueryOperation(
        operationId, title, accountWindowInvocationSyntax(), description, example);
  }

  static ProtocolOperation asOfReportOperation(
      OperationId operationId, String title, String description, String example) {
    return pdfQueryOperation(operationId, title, asOfInvocationSyntax(), description, example);
  }

  static ProtocolOperation periodReportOperation(
      OperationId operationId,
      String title,
      boolean includePostingCoverage,
      String description,
      String example) {
    return pdfQueryOperation(
        operationId, title, periodInvocationSyntax(includePostingCoverage), description, example);
  }

  private static ProtocolOperation pdfQueryOperation(
      OperationId operationId,
      String title,
      List<String> invocationSyntax,
      String description,
      String example) {
    return ProtocolOperationDefinitions.operation(
        new ProtocolOperationDefinitions.OperationDefinition(
            operationId,
            OperationCategory.QUERY,
            title,
            List.of(),
            invocationSyntax,
            ExecutionMode.JSON_ENVELOPE,
            pdfOutputModes(),
            List.of(ProtocolArtifactOutput.pdf()),
            description,
            List.of(ProtocolExampleStep.command(example))));
  }

  private static List<String> accountWindowInvocationSyntax() {
    return List.of(
        ProtocolOptions.BOOK_FILE + " <path>",
        ProtocolOptions.currentPassphraseSourceSyntax(),
        ProtocolOptions.ACCOUNT_CODE + " <account-code>",
        "[" + ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>]",
        "[" + ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>]",
        ProtocolOptions.optionalPostingCoverageSyntax(),
        ProtocolOptions.optionalPdfOutSyntax(),
        ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
  }

  private static List<String> asOfInvocationSyntax() {
    return List.of(
        ProtocolOptions.BOOK_FILE + " <path>",
        ProtocolOptions.currentPassphraseSourceSyntax(),
        "[" + ProtocolOptions.EFFECTIVE_DATE_AS_OF + " <YYYY-MM-DD>]",
        ProtocolOptions.optionalPdfOutSyntax(),
        ProtocolOptions.optionalOutputSyntax(pdfOutputModes()));
  }

  private static List<String> periodInvocationSyntax(boolean includePostingCoverage) {
    List<String> invocationSyntax =
        new ArrayList<>(
            List.of(
                ProtocolOptions.BOOK_FILE + " <path>",
                ProtocolOptions.currentPassphraseSourceSyntax(),
                ProtocolOptions.EFFECTIVE_DATE_FROM + " <YYYY-MM-DD>",
                ProtocolOptions.EFFECTIVE_DATE_TO + " <YYYY-MM-DD>"));
    if (includePostingCoverage) {
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
