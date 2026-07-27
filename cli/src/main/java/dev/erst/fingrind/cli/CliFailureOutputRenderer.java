package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Orchestrates plain-language rendering for deterministic CLI errors and rejections. */
final class CliFailureOutputRenderer {
  private CliFailureOutputRenderer() {}

  static String renderFailureText(CliFailure failure) {
    return renderTextDocument(TextDocument.failure("Error", failure));
  }

  static String renderDeterministicFailureText(CliFailure failure) {
    return renderTextDocument(TextDocument.failure("Rejected", failure));
  }

  static String renderRejectedText(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String idempotencyKey,
      CliRejectionJsonModels.@Nullable RejectionDetails details) {
    @Nullable String dedicatedPostingText =
        CliPostingRejectionTextRenderer.renderDedicatedRejectedText(
            code, message, idempotencyKey, details);
    if (dedicatedPostingText != null) {
      return dedicatedPostingText;
    }
    return renderTextDocument(TextDocument.rejection(code, message, hint, idempotencyKey, details));
  }

  private static String renderTextDocument(TextDocument document) {
    List<List<String>> rows = baseRows(document);
    CliErrorDetailsTextRenderer.appendRows(rows, document.details());
    appendFallbackFailurePaths(rows, document);
    appendRejectionDetails(rows, document.rejectionDetails());
    return CliTextFormat.renderTitledBlock(
        document.title(), CliTextFormat.renderKeyValueBlock(rows));
  }

  private static List<List<String>> baseRows(TextDocument document) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Code", document.code()));
    rows.add(List.of("Message", document.message()));
    if (document.idempotencyKey() != null) {
      rows.add(List.of("Idempotency key", document.idempotencyKey()));
    }
    if (document.argument() != null) {
      rows.add(List.of("Argument", document.argument()));
    }
    if (document.hint() != null) {
      rows.add(List.of("Hint", document.hint()));
    }
    return rows;
  }

  private static void appendFallbackFailurePaths(List<List<String>> rows, TextDocument document) {
    @Nullable CliFailure failure = document.pathFailure();
    if (failure == null || CliErrorDetailsTextRenderer.rendersFailurePaths(document.details())) {
      return;
    }
    @Nullable Path primaryPath = failure.path();
    if (primaryPath == null) {
      return;
    }
    rows.add(List.of("Path", CliTextDisplay.path(primaryPath)));
    if (!failure.relatedPaths().isEmpty()) {
      rows.add(
          List.of(
              "Related paths",
              CliTextFormat.joined(
                  failure.relatedPaths().stream().map(CliTextDisplay::path).toList())));
    }
    if (failure.retainedStage() != null) {
      rows.add(List.of("Retained stage", CliTextDisplay.path(failure.retainedStage())));
    }
  }

  private static void appendRejectionDetails(
      List<List<String>> rows, CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails) {
    if (rejectionDetails != null) {
      CliRejectionDetailsTextRenderer.appendRows(rows, rejectionDetails);
    }
  }

  private record TextDocument(
      String title,
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      CliErrorJsonModels.@Nullable ErrorDetails details,
      CliRejectionJsonModels.@Nullable RejectionDetails rejectionDetails,
      @Nullable CliFailure pathFailure) {
    static TextDocument failure(String title, CliFailure failure) {
      return new TextDocument(
          title,
          failure.code(),
          failure.message(),
          failure.hint(),
          failure.argument(),
          null,
          failure.details(),
          null,
          failure);
    }

    static TextDocument rejection(
        String code,
        String message,
        @Nullable String hint,
        @Nullable String idempotencyKey,
        CliRejectionJsonModels.@Nullable RejectionDetails details) {
      return new TextDocument(
          "Rejected", code, message, hint, null, idempotencyKey, null, details, null);
    }
  }
}
