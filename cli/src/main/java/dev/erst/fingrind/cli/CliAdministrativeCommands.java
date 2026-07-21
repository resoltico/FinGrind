package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;

/** Administrative CLI commands that create or reconfigure book state. */
record GenerateBookKeyFile(Path bookKeyFilePath, boolean tightenParents, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  GenerateBookKeyFile {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runGenerateBookKeyFileCommand(bookKeyFilePath, tightenParents, outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
record OpenBook(
    BookAccess bookAccess, OpenBookCommand command, boolean tightenParents, OutputMode outputMode)
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
        .runOpenBookCommand(bookAccess, command, tightenParents, outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
record RekeyBook(BookAccess bookAccess, Path newBookKeyFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  RekeyBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(newBookKeyFilePath, "newBookKeyFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runRekeyBookCommand(bookAccess, newBookKeyFilePath, outputMode);
  }
}

/** Administrative CLI command that exports one closed encrypted-book backup pair. */
record BackupBook(
    BookAccess bookAccess,
    Path backupFilePath,
    Path backupBookKeyFilePath,
    java.util.UUID backupId,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  BackupBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(backupFilePath, "backupFilePath");
    Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    Objects.requireNonNull(backupId, "backupId");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runBackupBookCommand(
            bookAccess, backupFilePath, backupBookKeyFilePath, backupId, outputMode);
  }
}

/** Administrative CLI command that restores one encrypted-book backup pair. */
record RestoreBook(
    Path bookFilePath,
    Path newBookKeyFilePath,
    Path backupFilePath,
    Path backupKeyFilePath,
    java.util.List<dev.erst.fingrind.core.attestation.AttestationCredentialSource>
        attestationCredentialSources,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  RestoreBook {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(newBookKeyFilePath, "newBookKeyFilePath");
    Objects.requireNonNull(backupFilePath, "backupFilePath");
    Objects.requireNonNull(backupKeyFilePath, "backupKeyFilePath");
    attestationCredentialSources = java.util.List.copyOf(attestationCredentialSources);
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runRestoreBookCommand(
            bookFilePath,
            newBookKeyFilePath,
            backupFilePath,
            backupKeyFilePath,
            attestationCredentialSources,
            outputMode);
  }
}

/** Administrative CLI commands that create or reconfigure book state. */
final class DeclareAccount extends CliBookRequestOutputModeCommand {
  DeclareAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .administrative()
        .runDeclareAccountCommand(bookAccess, requestFile, outputMode);
  }
}

/** Administrative CLI command that replaces one unreferenced account definition. */
final class AmendAccount extends CliBookRequestOutputModeCommand {
  AmendAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .administrative()
        .runAmendAccountCommand(bookAccess, requestFile, outputMode);
  }
}

/** Administrative CLI command that retires one account from ordinary authored use. */
final class RetireAccount extends CliBookRequestOutputModeCommand {
  RetireAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .administrative()
        .runRetireAccountCommand(bookAccess, requestFile, outputMode);
  }
}

/** Administrative CLI commands that declare or update owned tax registrations. */
final class DeclareTaxRegistration extends CliBookRequestOutputModeCommand {
  DeclareTaxRegistration(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    super(bookAccess, requestFile, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode) {
    return executionContext
        .administrative()
        .runDeclareTaxRegistrationCommand(bookAccess, requestFile, outputMode);
  }
}

/** Administrative CLI command that closes one contiguous reporting period. */
record InterimResultSweep(
    BookAccess bookAccess, java.time.LocalDate throughEffectiveDate, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  InterimResultSweep {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runInterimResultSweepCommand(bookAccess, throughEffectiveDate, outputMode);
  }
}

/** Administrative CLI command that closes one fiscal year. */
record FiscalYearClose(BookAccess bookAccess, int fiscalYearLabel, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  FiscalYearClose {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .administrative()
        .runFiscalYearCloseCommand(bookAccess, fiscalYearLabel, outputMode);
  }
}
