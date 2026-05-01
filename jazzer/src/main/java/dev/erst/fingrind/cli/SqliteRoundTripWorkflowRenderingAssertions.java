package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/** Rendering and envelope assertions for SQLite round-trip workflow coverage. */
final class SqliteRoundTripWorkflowRenderingAssertions {
  private static final ObjectMapper JSON = new ObjectMapper();

  private SqliteRoundTripWorkflowRenderingAssertions() {}

  static void assertOpened(
      ContractDecision<OpenBookResult> decision,
      Path bookPath,
      OutputMode outputMode,
      String requiredFragment)
      throws IOException {
    OpenBookResult result = decision.requireAccepted();
    if (!(result instanceof OpenBookResult.Opened)) {
      throw new IllegalStateException("Expected a fresh workflow book to open successfully.");
    }
    assertRenderedAccepted(
        ContractDecision.accepted(result),
        outputMode,
        (writer, accepted) -> writer.writeOpenBookResult(bookPath, accepted, outputMode),
        requiredFragment);
  }

  static void assertDeclared(
      ContractDecision<DeclareAccountResult> decision,
      OutputMode outputMode,
      String requiredFragment)
      throws IOException {
    DeclareAccountResult result = decision.requireAccepted();
    if (!(result instanceof DeclareAccountResult.Declared)) {
      throw new IllegalStateException("Expected workflow account declaration to succeed.");
    }
    assertRenderedAccepted(
        ContractDecision.accepted(result),
        outputMode,
        (writer, accepted) -> writer.writeDeclareAccountResult(accepted, outputMode),
        requiredFragment);
  }

  static <T> void assertRenderedDecision(
      Supplier<ContractDecision<T>> decisionSupplier,
      OutputMode outputMode,
      BiConsumer<CliResponseWriter, T> acceptedRenderer,
      @Nullable String requiredFragment)
      throws IOException {
    try {
      switch (decisionSupplier.get()) {
        case ContractDecision.Accepted<T>(T result) ->
            assertRenderedAccepted(
                ContractDecision.accepted(result), outputMode, acceptedRenderer, requiredFragment);
        case ContractDecision.Rejected<T>(var failure) ->
            assertRenderedFailure(failure, outputMode, requiredFragment);
      }
    } catch (RuntimeException runtimeException) {
      assertRenderedRuntimeFailure(runtimeException, outputMode, requiredFragment);
    }
  }

  static void writeListAccountsJson(CliResponseWriter writer, ListAccountsResult result) {
    writer.writeListAccountsResult(result, OutputMode.JSON);
  }

  static <T> void assertRenderedAccepted(
      ContractDecision<T> decision,
      OutputMode outputMode,
      BiConsumer<CliResponseWriter, T> renderer,
      @Nullable String requiredFragment)
      throws IOException {
    T result = decision.requireAccepted();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter =
        new CliResponseWriter(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    renderer.accept(responseWriter, result);
    assertRenderedDocument(
        outputStream.toString(StandardCharsets.UTF_8), outputMode, requiredFragment);
  }

  static void assertRenderedFailure(
      dev.erst.fingrind.contract.ContractFailure failure,
      OutputMode outputMode,
      @Nullable String requiredFragment)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter =
        new CliResponseWriter(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    responseWriter.writeFailure(CliFailureMapper.contractFailure(failure), outputMode);
    assertRenderedDocument(
        outputStream.toString(StandardCharsets.UTF_8), outputMode, requiredFragment);
  }

  static void assertRenderedRuntimeFailure(
      RuntimeException exception, OutputMode outputMode, @Nullable String requiredFragment)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter =
        new CliResponseWriter(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    responseWriter.writeFailure(CliFailureMapper.runtimeFailure(exception), outputMode);
    assertRenderedDocument(
        outputStream.toString(StandardCharsets.UTF_8), outputMode, requiredFragment);
  }

  static void assertRenderedDocument(
      String rendered, OutputMode outputMode, @Nullable String requiredFragment)
      throws IOException {
    String normalized = rendered.strip();
    if (normalized.isEmpty()) {
      throw new IllegalStateException("CLI response rendering unexpectedly produced blank output.");
    }
    if (normalized.startsWith("{") || normalized.startsWith("[")) {
      JSON.readTree(normalized);
    } else if (outputMode == OutputMode.CSV && !normalized.contains(",")) {
      throw new IllegalStateException(
          "CLI CSV rendering did not produce a comma-delimited document.");
    }
    if (requiredFragment != null && !normalized.contains(requiredFragment)) {
      throw new IllegalStateException(
          "Rendered CLI output lost the expected fragment '" + requiredFragment + "'.");
    }
  }
}
