package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/** Child-JVM probe that exercises real process-level output defaults for deterministic failures. */
public final class CliFailureEnvelopeProbe {
  private CliFailureEnvelopeProbe() {}

  public static void main(String[] arguments) {
    if (arguments.length < 2) {
      throw new IllegalArgumentException(
          "Expected one failure scenario plus one or more CLI arguments.");
    }
    String scenario = arguments[0];
    String[] cliArguments = Arrays.copyOfRange(arguments, 1, arguments.length);
    CliBookWorkflow workflow = workflowFor(scenario);
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            System.out,
            System.err,
            CliFilesystemFixtureSupport.fixedClock(),
            workflow,
            workflow,
            workflow);
    System.exit(cli.run(cliArguments));
  }

  private static CliBookWorkflow workflowFor(String scenario) {
    return switch (scenario) {
      case "invalid-request" -> new CliBookWorkflowAdapter() {};
      case "report-rejected" ->
          CliWorkflowDoubleSupport.reportingWorkflow(
              new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()));
      case "report-internal-error" -> internalErrorWorkflow();
      default ->
          throw new IllegalArgumentException("Unsupported failure probe scenario: " + scenario);
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
}
