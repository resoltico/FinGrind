package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;

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
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.rekeyBook(bookAccess, replacementPassphraseSource),
        outputMode,
        result -> responseWriter.writeRekeyBookResult(result, outputMode),
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
      BookAccess bookAccess,
      ReportingPeriod reportingPeriod,
      AccountCode closingEquityAccountCode,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.closePeriod(
            bookAccess, new ClosePeriodCommand(reportingPeriod, closingEquityAccountCode)),
        outputMode,
        result -> responseWriter.writeClosePeriodResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }
}
