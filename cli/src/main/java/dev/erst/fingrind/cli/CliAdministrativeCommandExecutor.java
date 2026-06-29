package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Executes administrative CLI commands that mutate book setup or key material. */
final class CliAdministrativeCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliMutationResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookLifecycleWorkflow lifecycleWorkflow;
  private final CliBookMutationWorkflow mutationWorkflow;

  CliAdministrativeCommandExecutor(
      CliRequestReader requestReader,
      CliMutationResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookLifecycleWorkflow lifecycleWorkflow,
      CliBookMutationWorkflow mutationWorkflow) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.lifecycleWorkflow = Objects.requireNonNull(lifecycleWorkflow, "lifecycleWorkflow");
    this.mutationWorkflow = Objects.requireNonNull(mutationWorkflow, "mutationWorkflow");
  }

  int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    return SqliteBookKeyFileGenerator.generateDecision(bookKeyFilePath)
        .fold(
            (GeneratedBookKeyFile generatedKeyFile) -> {
              responseWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
              return 0;
            },
            failure -> CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
  }

  int runOpenBookCommand(BookAccess bookAccess, OpenBookCommand command, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.openBook(bookAccess, command),
        result -> responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runRekeyBookCommand(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(
                outputMode, bookAccess.passphraseSource(), replacementPassphraseSource)
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.rekeyBook(bookAccess, replacementPassphraseSource),
        result ->
            responseWriter.writeRekeyBookResult(result, replacementPassphraseSource, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runBackupBookCommand(
      BookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath),
        result -> responseWriter.writeBackupBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runRestoreBookCommand(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.restoreBook(bookFilePath, backupFilePath, backupBookKeyFilePath),
        result -> responseWriter.writeRestoreBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runInspectRekeyRollbackCommand(Path bookFilePath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.inspectRekeyRollback(bookFilePath),
        result -> responseWriter.writeInspectRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runRestoreRekeyRollbackCommand(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource,
      OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, expectedPassphraseSource)
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.restoreRekeyRollback(
            bookFilePath, rollbackArtifactPath, expectedPassphraseSource),
        result -> responseWriter.writeRestoreRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runDeleteRekeyRollbackCommand(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.deleteRekeyRollback(bookAccess, rollbackArtifactPath),
        result -> responseWriter.writeDeleteRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.declareAccount(bookAccess, command),
        result -> responseWriter.writeDeclareAccountResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runDeclareTaxRegistrationCommand(
      BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    DeclareTaxRegistrationCommand command =
        requestReader.readDeclareTaxRegistrationCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.declareTaxRegistration(bookAccess, command),
        result -> responseWriter.writeDeclareTaxRegistrationResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runInterimResultSweepCommand(
      BookAccess bookAccess, ReportingPeriod reportingPeriod, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.interimResultSweep(
            bookAccess, new InterimResultSweepCommand(reportingPeriod)),
        result -> responseWriter.writeInterimResultSweepResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }

  int runFiscalYearCloseCommand(
      BookAccess bookAccess, ReportingPeriod reportingPeriod, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.fiscalYearClose(bookAccess, new FiscalYearCloseCommand(reportingPeriod)),
        result -> responseWriter.writeFiscalYearCloseResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter);
  }
}
