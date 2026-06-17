package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
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
        (writers, accepted, mode) ->
            writers.mutation().writeOpenBookResult(bookPath, accepted, mode),
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
        (writers, accepted, mode) -> writers.mutation().writeDeclareAccountResult(accepted, mode),
        requiredFragment);
  }

  static <T> void assertRenderedDecision(
      Supplier<ContractDecision<T>> decisionSupplier,
      OutputMode outputMode,
      AcceptedRenderer<T> acceptedRenderer,
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

  static void writeListAccountsJson(
      CliCoverageResponseWriters writers, ListAccountsResult result, OutputMode outputMode) {
    writers.query().writeListAccountsResult(result, outputMode);
  }

  static <T> void assertRenderedAccepted(
      ContractDecision<T> decision,
      OutputMode outputMode,
      AcceptedRenderer<T> renderer,
      @Nullable String requiredFragment)
      throws IOException {
    T result = decision.requireAccepted();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliCoverageResponseWriters writers =
        coverageResponseWriters(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    renderer.render(writers, result, outputMode);
    assertRenderedDocument(
        outputStream.toString(StandardCharsets.UTF_8), outputMode, requiredFragment);
  }

  static void assertRenderedFailure(
      dev.erst.fingrind.contract.runtime.ContractFailure failure,
      OutputMode outputMode,
      @Nullable String requiredFragment)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliCoverageResponseWriters writers =
        coverageResponseWriters(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    writers.failure().writeFailure(CliFailureMapper.contractFailure(failure));
    assertRenderedDocument(
        outputStream.toString(StandardCharsets.UTF_8), outputMode, requiredFragment);
  }

  static void assertRenderedRuntimeFailure(
      RuntimeException exception, OutputMode outputMode, @Nullable String requiredFragment)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliCoverageResponseWriters writers =
        coverageResponseWriters(new PrintStream(outputStream, false, StandardCharsets.UTF_8));
    CliFailure runtimeFailure = CliFailureMapper.runtimeFailure(exception);
    CliFailure failure =
        runtimeFailure != null
            ? runtimeFailure
            : CliFailureMapper.internalError("fg-jazzer-rendering-internal");
    writers.failure().writeFailure(failure);
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

  private static CliCoverageResponseWriters coverageResponseWriters(PrintStream outputStream) {
    CliOutputChannel outputChannel = new CliOutputChannel(outputStream);
    return new CliCoverageResponseWriters(
        new CliDiscoveryResponseWriter(outputChannel),
        new CliMutationResponseWriter(outputChannel),
        new CliCoverageQueryWriter(
            new CliBookReadResponseWriter(outputChannel),
            new CliReportResponseWriter(outputChannel)),
        new CliFailureResponseWriter(outputChannel));
  }

  /** Coverage-only query writer that preserves the harness query surface. */
  static final class CliCoverageQueryWriter {
    private final CliBookReadResponseWriter bookReadWriter;
    private final CliReportResponseWriter reportWriter;

    private CliCoverageQueryWriter(
        CliBookReadResponseWriter bookReadWriter, CliReportResponseWriter reportWriter) {
      this.bookReadWriter = Objects.requireNonNull(bookReadWriter, "bookReadWriter");
      this.reportWriter = Objects.requireNonNull(reportWriter, "reportWriter");
    }

    void writeBookInspection(
        Path bookFilePath,
        dev.erst.fingrind.contract.runtime.BookInspection inspection,
        OutputMode outputMode) {
      bookReadWriter.writeBookInspection(bookFilePath, inspection, outputMode);
    }

    void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
      bookReadWriter.writeListAccountsResult(result, outputMode);
    }

    void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
      bookReadWriter.writeGetPostingResult(result, outputMode);
    }

    void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
      bookReadWriter.writeListPostingsResult(result, outputMode);
    }

    void writeAccountBalanceResult(
        dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult result, OutputMode outputMode) {
      reportWriter.writeAccountBalanceResult(result, outputMode, null);
    }

    void writeTrialBalanceResult(
        dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult result, OutputMode outputMode) {
      reportWriter.writeTrialBalanceResult(result, outputMode, null);
    }

    void writeAccountLedgerResult(
        dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult result, OutputMode outputMode) {
      reportWriter.writeAccountLedgerResult(result, outputMode, null);
    }

    void writePeriodSummaryResult(
        dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult result, OutputMode outputMode) {
      reportWriter.writePeriodSummaryResult(result, outputMode, null);
    }
  }

  /** Renders one accepted workflow result through the bounded CLI response writers. */
  @FunctionalInterface
  interface AcceptedRenderer<T> {
    /** Writes one accepted workflow result using the selected output mode. */
    void render(CliCoverageResponseWriters writers, T result, OutputMode outputMode);
  }

  /** Output-writer bundle used by workflow coverage to follow the production CLI boundaries. */
  record CliCoverageResponseWriters(
      CliDiscoveryResponseWriter discovery,
      CliMutationResponseWriter mutation,
      CliCoverageQueryWriter query,
      CliFailureResponseWriter failure) {}
}
