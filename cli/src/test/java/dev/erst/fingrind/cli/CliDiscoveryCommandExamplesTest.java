package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import org.junit.jupiter.api.Test;

/** Focused coverage for runtime-aware command-example selection. */
class CliDiscoveryCommandExamplesTest {
  @Test
  void primaryStarterRequestCommand_prefersQuickStartCopyForBundleSale() {
    String originalDistribution =
        System.getProperty(
            FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY,
            FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
    try {
      assertEquals(
          "cp ./quick-start-request.json ./request.json",
          CliDiscoveryCommandExamples.primaryStarterRequestCommand(
              OperationId.RECORD_SALE_SETTLED));
      assertTrue(
          CliDiscoveryCommandExamples.primaryStarterRequestCommand(
                  OperationId.RECORD_EXPENSE_SETTLED)
              .contains("print-request-template record-expense-settled > request.json"));
    } finally {
      System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, originalDistribution);
    }
  }

  @Test
  void primaryStarterRequestCommand_usesRequestTemplatesForOtherEntryCommands() {
    String recordExpense =
        CliDiscoveryCommandExamples.primaryStarterRequestCommand(
            OperationId.RECORD_EXPENSE_SETTLED);
    String postEntry =
        CliDiscoveryCommandExamples.primaryStarterRequestCommand(OperationId.POST_ENTRY);

    assertTrue(
        recordExpense.contains("print-request-template record-expense-settled > request.json"));
    assertTrue(postEntry.contains("print-request-template post-entry > request.json"));
  }
}
