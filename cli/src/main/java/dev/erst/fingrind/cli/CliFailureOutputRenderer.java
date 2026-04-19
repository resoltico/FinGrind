package dev.erst.fingrind.cli;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared human-readable rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureHuman(CliFailure failure) {
    return renderHumanDocument(
        "Error", failure.code(), failure.message(), failure.hint(), failure.argument(), null);
  }

  static String renderRejectedHuman(String code, String message, @Nullable String idempotencyKey) {
    return renderHumanDocument("Rejected", code, message, null, null, idempotencyKey);
  }

  private static String renderHumanDocument(
      String title,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey) {
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
    return CliTextFormat.renderTitledBlock(title, CliTextFormat.renderKeyValueBlock(rows));
  }
}
