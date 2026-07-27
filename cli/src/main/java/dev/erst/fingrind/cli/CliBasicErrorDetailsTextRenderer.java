package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import java.util.List;

/** Renders scalar and short-list structured error details. */
final class CliBasicErrorDetailsTextRenderer {
  private CliBasicErrorDetailsTextRenderer() {}

  static void appendInvalidJsonRows(
      List<List<String>> rows, CliErrorJsonModels.InvalidJsonDetails details) {
    rows.add(List.of("Parse message", details.parseMessage()));
    rows.add(List.of("Parse location", "line " + details.line() + ", column " + details.column()));
  }

  static void appendInvalidRequestRows(
      List<List<String>> rows, CliErrorJsonModels.InvalidRequestDetails details) {
    rows.add(List.of("Violations", CliTextFormat.joined(details.violations())));
  }

  static void appendStaleHeadRows(
      List<List<String>> rows, CliErrorJsonModels.StaleHeadDetails details) {
    rows.add(List.of("Observed head", details.observedHead()));
    rows.add(List.of("Current head", details.currentHead()));
    rows.add(List.of("Current order", details.currentOrder()));
  }

  static void appendReviewWindowRows(
      List<List<String>> rows, CliErrorJsonModels.AttestationReviewWindowDetails details) {
    rows.add(List.of("Credential key ID", details.credentialKeyId()));
    rows.add(List.of("First affected order", details.firstAffectedOrder()));
    rows.add(
        List.of(
            "Last affected order",
            details.lastAffectedOrder() == null
                ? "(through verified head)"
                : details.lastAffectedOrder()));
    rows.add(List.of("Verified attestation order", details.verifiedHeadOrder()));
  }

  static void appendUnsupportedBookFormatRows(
      List<List<String>> rows, CliErrorJsonModels.UnsupportedBookFormatVersionDetails details) {
    rows.add(
        List.of(
            "Detected book format version", Integer.toString(details.detectedBookFormatVersion())));
    rows.add(
        List.of(
            "Supported book format version",
            Integer.toString(details.supportedBookFormatVersion())));
  }
}
