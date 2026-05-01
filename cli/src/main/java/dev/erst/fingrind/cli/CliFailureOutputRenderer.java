package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared human-readable rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureHuman(CliFailure failure) {
    return renderHumanDocument(
        "Error",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details());
  }

  static String renderWarningHuman(CliFailure failure) {
    return renderHumanDocument(
        "Warning",
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        null,
        failure.details());
  }

  static String renderRejectedHuman(String code, String message, @Nullable String idempotencyKey) {
    return renderHumanDocument("Rejected", code, message, null, null, idempotencyKey, null);
  }

  private static String renderHumanDocument(
      String title,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Code", code));
    rows.add(List.of("Message", message));
    if (idempotencyKey != null) {
      rows.add(List.of("Idempotency key", idempotencyKey));
    }
    if (argument != null) {
      rows.add(List.of("Argument", argument));
    }
    if (hint != null) {
      rows.add(List.of("Hint", hint));
    }
    if (details != null) {
      switch (details) {
        case CliErrorJsonModels.InvalidJsonDetails invalidJsonDetails -> {
          rows.add(List.of("Parse message", invalidJsonDetails.parseMessage()));
          rows.add(
              List.of(
                  "Parse location",
                  "line " + invalidJsonDetails.line() + ", column " + invalidJsonDetails.column()));
        }
        case CliErrorJsonModels.InvalidRequestDetails invalidRequestDetails ->
            rows.add(
                List.of("Violations", CliTextFormat.joined(invalidRequestDetails.violations())));
      }
    }
    return CliTextFormat.renderTitledBlock(title, CliTextFormat.renderKeyValueBlock(rows));
  }
}
