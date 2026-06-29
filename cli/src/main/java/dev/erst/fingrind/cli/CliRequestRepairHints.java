package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Builds action-first repair hints for request-shape failures before scaffold fallback. */
final class CliRequestRepairHints {
  private static final java.util.regex.Pattern REQUIRED_REQUEST_FIELD_PATTERN =
      java.util.regex.Pattern.compile(
          "^Command '.*' requires request field (?<field>[^ ]+) to be '(?<required>[^']+)', but the request carries '(?<actual>[^']+)'\\.$");

  private CliRequestRepairHints() {}

  static String refine(
      String message,
      String defaultHint,
      CliErrorJsonModels.@Nullable ErrorDetails details,
      OperationId templateOperation) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(defaultHint, "defaultHint");
    Objects.requireNonNull(templateOperation, "templateOperation");
    String directHint = directHint(message, details);
    if (directHint.isBlank()) {
      return defaultHint;
    }
    return directHint
        + " If you need a starter file, run '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + templateTopicSuffix(templateOperation)
        + "'.";
  }

  static String refineLedgerPlan(String message, String defaultHint) {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(defaultHint, "defaultHint");
    if (message.startsWith("Expected one canonical YYYY-MM-DD local date for ")) {
      String fieldName =
          trimTerminalPeriod(
              message.substring("Expected one canonical YYYY-MM-DD local date for ".length()));
      return "Replace "
          + fieldName
          + " with one canonical date such as 2026-04-01, then rerun. If you need a starter plan, run '"
          + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
          + "'.";
    }
    if (message.startsWith("Missing required field: ")) {
      String fieldName = message.substring("Missing required field: ".length());
      return "Add "
          + fieldName
          + " to the ledger plan document, then rerun. If you need a starter plan, run '"
          + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
          + "'.";
    }
    return defaultHint;
  }

  private static String directHint(
      String message, CliErrorJsonModels.@Nullable ErrorDetails details) {
    if (details instanceof CliErrorJsonModels.InvalidRequestDetails invalidRequestDetails) {
      List<String> violations = invalidRequestDetails.violations();
      if (violations.size() == 1
          && violations.getFirst().contains("must contain at least one line")) {
        return "Add at least one balanced journal line under entry.journalEntry.lines, then rerun.";
      }
    }
    if (message.startsWith("Missing required field: ")) {
      String fieldName = message.substring("Missing required field: ".length());
      return "Add " + fieldName + " to the request document, then rerun.";
    }
    if (message.startsWith("Field must be a string when present: ")) {
      String fieldName = message.substring("Field must be a string when present: ".length());
      return "Replace " + fieldName + " with one JSON string value, then rerun.";
    }
    if (message.startsWith("Field must be a string: ")) {
      String fieldName = message.substring("Field must be a string: ".length());
      return "Replace " + fieldName + " with one JSON string value, then rerun.";
    }
    if (message.startsWith("Field must be an integer when present: ")) {
      String fieldName = message.substring("Field must be an integer when present: ".length());
      return "Replace " + fieldName + " with one JSON integer value, then rerun.";
    }
    java.util.regex.Matcher requestFieldMismatch = REQUIRED_REQUEST_FIELD_PATTERN.matcher(message);
    if (requestFieldMismatch.matches()) {
      return "Replace "
          + requestFieldMismatch.group("field")
          + " with "
          + requestFieldMismatch.group("required")
          + " instead of "
          + requestFieldMismatch.group("actual")
          + ", then rerun.";
    }
    return "";
  }

  private static String trimTerminalPeriod(String value) {
    return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
  }

  private static String templateTopicSuffix(OperationId templateOperation) {
    if (templateOperation == OperationId.PREFLIGHT_ENTRY) {
      return "";
    }
    return " " + templateOperation.wireName();
  }
}
