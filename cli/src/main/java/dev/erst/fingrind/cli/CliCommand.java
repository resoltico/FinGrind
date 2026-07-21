package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;

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
    CliReportOutput output();
  }
}
