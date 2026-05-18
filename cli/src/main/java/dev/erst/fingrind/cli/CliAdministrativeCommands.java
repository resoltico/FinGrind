package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import java.nio.file.Path;
import java.util.Objects;

/** Administrative CLI commands that create or reconfigure book state. */
record GenerateBookKeyFile(Path bookKeyFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  GenerateBookKeyFile {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runGenerateBookKeyFileCommand(bookKeyFilePath, outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
record OpenBook(BookAccess bookAccess, OpenBookCommand command, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  OpenBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runOpenBookCommand(bookAccess, command, outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
record RekeyBook(
    BookAccess bookAccess,
    BookAccess.PassphraseSource replacementPassphraseSource,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  RekeyBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runRekeyBookCommand(bookAccess, replacementPassphraseSource, outputMode);
  }
}

/** Administrative CLI command that exports one closed encrypted-book backup pair. */
record BackupBook(
    BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  BackupBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(backupFilePath, "backupFilePath");
    Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runBackupBookCommand(bookAccess, backupFilePath, backupBookKeyFilePath, outputMode);
  }
}

/** Administrative CLI command that restores one encrypted-book backup pair. */
record RestoreBook(
    Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  RestoreBook {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(backupFilePath, "backupFilePath");
    Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runRestoreBookCommand(bookFilePath, backupFilePath, backupBookKeyFilePath, outputMode);
  }
}

/** Administrative CLI command that inspects or applies one sibling rekey rollback artifact. */
record RecoverRekey(
    Path bookFilePath,
    RekeyRecoveryAction action,
    @org.jspecify.annotations.Nullable Path rollbackArtifactPath,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  RecoverRekey {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runRecoverRekeyCommand(bookFilePath, action, rollbackArtifactPath, outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
record DeclareAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  DeclareAccount {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(requestFile, "requestFile");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runDeclareAccountCommand(bookAccess, requestFile, outputMode);
  }
}

/** Administrative CLI command that closes one contiguous reporting period. */
record ClosePeriod(
    BookAccess bookAccess,
    ReportingPeriod reportingPeriod,
    AccountCode closingEquityAccountCode,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  ClosePeriod {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(closingEquityAccountCode, "closingEquityAccountCode");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runClosePeriodCommand(bookAccess, reportingPeriod, closingEquityAccountCode, outputMode);
  }
}
