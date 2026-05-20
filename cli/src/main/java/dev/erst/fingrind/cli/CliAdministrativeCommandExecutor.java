package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes administrative CLI commands that mutate book setup or key material. */
final class CliAdministrativeCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;

  CliAdministrativeCommandExecutor(
      CliRequestReader requestReader,
      CliResponseWriter responseWriter,
      CliBookWorkflow bookWorkflow) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
  }

  int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    return SqliteBookKeyFileGenerator.generateDecision(bookKeyFilePath)
        .fold(
            generatedKeyFile -> {
              responseWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
              return 0;
            },
            failure ->
                CliCommandOutcomeWriter.writeDeterministicFailure(
                    failure, outputMode, responseWriter));
  }

  int runOpenBookCommand(BookAccess bookAccess, OpenBookCommand command, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.openBook(bookAccess, command),
        outputMode,
        result -> responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runRekeyBookCommand(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource),
        outputMode,
        result ->
            responseWriter.writeRekeyBookResult(result, replacementPassphraseSource, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runBackupBookCommand(
      BookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath),
        outputMode,
        result -> responseWriter.writeBackupBookResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runRestoreBookCommand(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.restoreBook(bookFilePath, backupFilePath, backupBookKeyFilePath),
        outputMode,
        result -> responseWriter.writeRestoreBookResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runInspectRekeyRollbackCommand(Path bookFilePath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.inspectRekeyRollback(bookFilePath),
        outputMode,
        result -> responseWriter.writeInspectRekeyRollbackResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runRestoreRekeyRollbackCommand(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.restoreRekeyRollback(
            bookFilePath, rollbackArtifactPath, expectedPassphraseSource),
        outputMode,
        result -> responseWriter.writeRestoreRekeyRollbackResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runDeleteRekeyRollbackCommand(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.deleteRekeyRollback(bookAccess, rollbackArtifactPath),
        outputMode,
        result -> responseWriter.writeDeleteRekeyRollbackResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.declareAccount(bookAccess, command),
        outputMode,
        result -> responseWriter.writeDeclareAccountResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runClosePeriodCommand(
      BookAccess bookAccess, ReportingPeriod reportingPeriod, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.closePeriod(bookAccess, new ClosePeriodCommand(reportingPeriod)),
        outputMode,
        result -> responseWriter.writeClosePeriodResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }
}
