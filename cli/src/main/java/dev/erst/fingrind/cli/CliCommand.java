package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parsed CLI command model for one FinGrind process invocation. */
sealed interface CliCommand
    permits CliCommand.OutputModeCommand, CliCommand.JsonFailureCommand, CliCommand.ReportCommand {
  /** Returns the operator-facing output mode to use for deterministic failures. */
  OutputMode failureOutputMode();

  /** Executes this parsed command against one concrete CLI execution context. */
  int execute(CliExecutionContext executionContext);

  /** Command family whose deterministic failures follow one explicit `--output` selection. */
  sealed interface OutputModeCommand extends CliCommand
      permits Help,
          Version,
          Capabilities,
          EnvironmentCommand,
          GenerateBookKeyFile,
          OpenBook,
          RekeyBook,
          BackupBook,
          RestoreBook,
          InspectRekeyRollback,
          RestoreRekeyRollback,
          DeleteRekeyRollback,
          DeclareAccount,
          TransferPeriodResult,
          InspectBook,
          ListAccounts,
          GetPosting,
          ListPostings,
          PreflightEntry,
          PostEntry {
    /** Selected operator-facing output mode for this command. */
    OutputMode outputMode();

    @Override
    default OutputMode failureOutputMode() {
      return outputMode();
    }
  }

  /** Command family whose deterministic failures are always JSON machine envelopes. */
  sealed interface JsonFailureCommand extends CliCommand
      permits PrintRequestTemplate, PrintPlanTemplate, ExecutePlan {
    @Override
    default OutputMode failureOutputMode() {
      return OutputMode.JSON;
    }
  }

  /** Command family whose successful output can target either terminal text/JSON or a PDF file. */
  sealed interface ReportCommand extends CliCommand
      permits AccountBalance,
          TrialBalance,
          AccountLedger,
          PeriodSummary,
          FinancialPosition,
          IncomeStatement,
          ChangesInEquity {
    /** Selected report presentation settings for this command. */
    ReportOutput output();

    @Override
    default OutputMode failureOutputMode() {
      return output().outputMode();
    }
  }

  /** Shared report-presentation settings for one successful report command. */
  record ReportOutput(OutputMode outputMode, @Nullable Path pdfOutPath) {
    public ReportOutput {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }
}
