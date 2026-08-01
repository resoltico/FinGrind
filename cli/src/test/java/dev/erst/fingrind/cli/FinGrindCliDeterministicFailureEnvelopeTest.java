package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Acceptance coverage for deterministic failure envelopes across CLI output-selection surfaces. */
class FinGrindCliDeterministicFailureEnvelopeTest extends CliWorkflowFixtureSupport {
  @Test
  void run_emitsTextForResolvedTextAndCsvInvalidRequestFailuresAndJsonForExplicitJson()
      throws IOException {
    Supplier<CliBookWorkflow> workflowFactory = () -> new CliBookWorkflowAdapter() {};

    ObservedInvocation textObserved =
        runCli(workflowFactory.get(), withExplicitOutput(invalidTrialBalanceArguments(), "text"));
    ObservedInvocation jsonObserved =
        runCli(workflowFactory.get(), withExplicitOutput(invalidTrialBalanceArguments(), "json"));
    ObservedInvocation csvObserved =
        runCli(workflowFactory.get(), withExplicitOutput(invalidTrialBalanceArguments(), "csv"));

    assertTextFailure(textObserved, 1, "Error", "invalid-request");
    assertJsonFailure(jsonObserved, 1, "error", "invalid-request");
    assertTextFailure(csvObserved, 1, "Error", "invalid-request");
    assertEquals(
        normalizedFailureText(textObserved.stderr()), normalizedFailureText(csvObserved.stderr()));
  }

  @Test
  void run_emitsTextForAbsentOutputOnInvalidRequestFailure() throws IOException {
    ObservedInvocation observed =
        runCli(new CliBookWorkflowAdapter() {}, invalidTrialBalanceArguments());

    assertTextFailure(observed, 1, "Error", "invalid-request");
  }

  @Test
  void run_emitsJsonForJsonModeAndPlainTextForTextAndCsvRejectedOutputs() throws IOException {
    Supplier<CliBookWorkflow> workflowFactory =
        () ->
            reportingWorkflow(
                new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));

    ObservedInvocation jsonObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "json"));
    ObservedInvocation textObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "text"));
    ObservedInvocation csvObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "csv"));

    assertJsonFailure(jsonObserved, 2, "rejected", "query-book-not-initialized");
    assertTextFailure(textObserved, 2, "Rejected", "query-book-not-initialized");
    assertTextFailure(csvObserved, 2, "Rejected", "query-book-not-initialized");
    assertEquals(
        normalizedFailureText(textObserved.stderr()), normalizedFailureText(csvObserved.stderr()));
  }

  @Test
  void run_emitsTextForResolvedTextAndCsvInternalFailuresAndJsonForExplicitJson()
      throws IOException {
    Supplier<CliBookWorkflow> workflowFactory =
        FinGrindCliDeterministicFailureEnvelopeTest::internalErrorWorkflow;

    ObservedInvocation textObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "text"));
    ObservedInvocation jsonObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "json"));
    ObservedInvocation csvObserved =
        runCli(workflowFactory.get(), withExplicitOutput(reportTrialBalanceArguments(), "csv"));

    assertTextFailure(textObserved, 70, "Error", "internal-error");
    assertJsonFailure(jsonObserved, 70, "error", "internal-error");
    assertTextFailure(csvObserved, 70, "Error", "internal-error");
    assertEquals(
        normalizedFailureText(textObserved.stderr()), normalizedFailureText(csvObserved.stderr()));
  }

  @Test
  void run_usesTextForDuplicateOutputSelection() throws IOException {
    ObservedInvocation observed =
        runCli(
            new CliBookWorkflowAdapter() {},
            new String[] {
              "trial-balance",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output",
              "text",
              "--output",
              "json"
            });

    assertTextFailure(observed, 1, "Error", "invalid-request");
  }

  @Test
  void run_usesTextForInvalidOutputSelection() throws IOException {
    ObservedInvocation observed =
        runCli(
            new CliBookWorkflowAdapter() {},
            new String[] {
              "trial-balance",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output",
              "markdown"
            });

    assertTextFailure(observed, 1, "Error", "invalid-request");
  }

  @Test
  void run_usesTextWhenCsvIsUnsupportedForTheSelectedCommand() throws IOException {
    ObservedInvocation observed =
        runCli(
            new CliBookWorkflowAdapter() {},
            new String[] {
              "get-posting",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--posting-id",
              "posting-1",
              "--output",
              "csv"
            });

    assertTextFailure(observed, 2, "Error", "unsupported-output-selection");
  }

  @Test
  void run_keepsUnknownCommandFailuresInTheExplicitlySelectedMode() throws IOException {
    ObservedInvocation absentOutputObserved =
        runCli(new CliBookWorkflowAdapter() {}, new String[] {"wat-command"});
    ObservedInvocation textObserved =
        runCli(new CliBookWorkflowAdapter() {}, new String[] {"wat-command", "--output", "text"});
    ObservedInvocation jsonObserved =
        runCli(new CliBookWorkflowAdapter() {}, new String[] {"wat-command", "--output", "json"});

    assertTextFailure(absentOutputObserved, 1, "Error", "unknown-command");
    assertTextFailure(textObserved, 1, "Error", "unknown-command");
    assertJsonFailure(jsonObserved, 1, "error", "unknown-command");
  }

  @Test
  void run_usesTextForMissingOutputValue() throws IOException {
    ObservedInvocation observed =
        runCli(
            new CliBookWorkflowAdapter() {},
            new String[] {
              "trial-balance",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output"
            });

    assertTextFailure(observed, 1, "Error", "invalid-request");
  }

  private static ObservedInvocation runCli(CliBookWorkflow workflow, String[] arguments) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            workflow);
    int exitCode = cli.run(arguments);
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private static void assertJsonFailure(
      ObservedInvocation observed, int expectedExitCode, String expectedStatus, String expectedCode)
      throws IOException {
    assertEquals(expectedExitCode, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stdout(), observed.stdout());
    JsonNode envelope = CliJsonObjectMappers.configuredObjectMapper().readTree(observed.stderr());
    assertEquals(expectedStatus, envelope.path("status").stringValue(), observed.stderr());
    assertEquals(expectedCode, envelope.path("code").stringValue(), observed.stderr());
    assertEquals(
        ContractResponseCatalog.failureCategoryFor(expectedCode).wireValue(),
        envelope.path("category").stringValue(),
        observed.stderr());
    assertTrue(observed.stderr().endsWith(System.lineSeparator()), observed.stderr());
  }

  private static void assertTextFailure(
      ObservedInvocation observed,
      int expectedExitCode,
      String expectedTitle,
      String expectedCode) {
    assertEquals(expectedExitCode, observed.exitCode(), observed.stderr());
    assertEquals("", observed.stdout(), observed.stdout());
    assertTrue(observed.stderr().contains(expectedTitle), observed.stderr());
    assertTrue(observed.stderr().contains(expectedCode), observed.stderr());
    assertTrue(observed.stderr().endsWith(System.lineSeparator()), observed.stderr());
  }

  private static String normalizedFailureText(String document) {
    return document
        .replaceAll("\\r\\n", "\n")
        .replaceAll("fg-internal-[A-Za-z0-9-]+", "fg-internal-<normalized>");
  }

  private static String[] withExplicitOutput(String[] argumentsWithoutOutput, String outputMode) {
    String[] explicitArguments =
        java.util.Arrays.copyOf(argumentsWithoutOutput, argumentsWithoutOutput.length + 2);
    explicitArguments[argumentsWithoutOutput.length] = "--output";
    explicitArguments[argumentsWithoutOutput.length + 1] = outputMode;
    return explicitArguments;
  }

  private static String[] invalidTrialBalanceArguments() {
    return new String[] {
      "trial-balance",
      "--book-file",
      "book.sqlite",
      "--book-key-file",
      "book.key",
      "--effective-date-as-of",
      "not-a-date"
    };
  }

  private static String[] reportTrialBalanceArguments() {
    return new String[] {
      "trial-balance", "--book-file", "book.sqlite", "--book-key-file", "book.key"
    };
  }

  private static CliBookWorkflow internalErrorWorkflow() {
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<TrialBalanceResult> trialBalance(
          BookAccess bookAccess, TrialBalanceQuery query) {
        throw new IllegalStateException("boom");
      }
    };
  }

  private record ObservedInvocation(int exitCode, String stdout, String stderr) {}
}
