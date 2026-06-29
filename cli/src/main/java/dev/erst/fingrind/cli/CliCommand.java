package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parsed CLI command model for one FinGrind process invocation. */
sealed interface CliCommand
    permits CliCommand.OutputModeCommand,
        CliCommand.ReportCommand,
        PrintPlanTemplate,
        PrintRequestTemplate {

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
          CliBookNullablePathOutputModeCommand,
          CliBookRequestOutputModeCommand,
          InterimResultSweep,
          FiscalYearClose,
          InspectBook,
          GetPosting,
          CliBookQueryOutputModeCommand {
    /** Selected operator-facing output mode for this command. */
    OutputMode outputMode();
  }

  /** Command family whose successful output can target either terminal text/JSON or a PDF file. */
  sealed interface ReportCommand extends CliCommand permits CliBookQueryReportCommand {
    /** Selected report presentation settings for this command. */
    ReportOutput output();
  }

  /** Shared report-presentation settings for one successful report command. */
  record ReportOutput(OutputMode outputMode, @Nullable Path pdfOutPath) {
    public ReportOutput {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }
}
