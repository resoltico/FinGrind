package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteCallerPathSecurity;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
  private final CliAccountRegistryMutationActions accountRegistryCommands;

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
    this.accountRegistryCommands =
        new CliAccountRegistryMutationActions(
            this.requestReader, this.responseWriter, this.failureWriter, this.mutationWorkflow);
  }

  int runGenerateBookKeyFileCommand(
      Path bookKeyFilePath, boolean tightenParents, OutputMode outputMode) {
    List<Path> tightenedParentDirectories =
        tightenedBookKeyParentDirectories(bookKeyFilePath, tightenParents);
    return SqliteBookKeyFileGenerator.generateDecision(bookKeyFilePath)
        .fold(
            (GeneratedBookKeyFile generatedKeyFile) -> {
              responseWriter.writeGenerateBookKeyFileResult(
                  generatedKeyFile, tightenedParentDirectories, outputMode);
              return 0;
            },
            failure ->
                CliCommandOutcomeWriter.writeDeterministicFailure(
                    failure, failureWriter, outputMode));
  }

  int runOpenBookCommand(
      BookAccess bookAccess,
      OpenBookCommand command,
      boolean tightenParents,
      OutputMode outputMode) {
    List<Path> tightenedParentDirectories =
        tightenedBookParentDirectories(bookAccess.bookFilePath(), tightenParents);
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return lifecycleWorkflow
        .openBook(bookAccess, command)
        .fold(
            result -> {
              responseWriter.writeOpenBookResult(
                  bookAccess.bookFilePath(), tightenedParentDirectories, result, outputMode);
              return CliAdministrativeExitCodes.exitCodeFor(result);
            },
            failure -> {
              CliFailure cliFailure =
                  failure.descriptor() == ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED
                      ? new CliFailure(
                          failure.code(),
                          failure.message(),
                          failure.hint(),
                          failure.argument(),
                          bookAccess.bookFilePath(),
                          List.of())
                      : CliFailureMapper.contractFailure(failure);
              failureWriter.writeFailure(cliFailure, outputMode);
              return CliExecutionPolicy.contractFailureExitCode(failure);
            });
  }

  private static List<Path> tightenedBookKeyParentDirectories(
      Path bookKeyFilePath, boolean tightenParents) {
    if (!tightenParents) {
      return List.of();
    }
    try {
      return SqliteCallerPathSecurity.tightenExistingBookKeyParentDirectory(bookKeyFilePath)
          .stream()
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to tighten the existing book-key parent directory.", exception);
    }
  }

  private static List<Path> tightenedBookParentDirectories(
      Path bookFilePath, boolean tightenParents) {
    if (!tightenParents) {
      return List.of();
    }
    try {
      return SqliteCallerPathSecurity.tightenExistingBookParentDirectory(bookFilePath).stream()
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to tighten the existing book parent directory.", exception);
    }
  }

  int runRekeyBookCommand(BookAccess bookAccess, Path newBookKeyFilePath, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.rekeyBook(bookAccess, newBookKeyFilePath),
        result -> responseWriter.writeRekeyBookResult(result, newBookKeyFilePath, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
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
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath),
        result -> responseWriter.writeBackupBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runRestoreBookCommand(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      boolean replaceExistingBook,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.restoreBook(
            bookFilePath,
            newBookKeyFilePath,
            backupFilePath,
            backupKeyFilePath,
            replaceExistingBook),
        result -> responseWriter.writeRestoreBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runInspectRekeyRollbackCommand(Path bookFilePath, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.inspectRekeyRollback(bookFilePath),
        result -> responseWriter.writeInspectRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
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
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.restoreRekeyRollback(
            bookFilePath, rollbackArtifactPath, expectedPassphraseSource),
        result -> responseWriter.writeRestoreRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runDeleteRekeyRollbackCommand(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.deleteRekeyRollback(bookAccess, rollbackArtifactPath),
        result -> responseWriter.writeDeleteRekeyRollbackResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return accountRegistryCommands.runDeclareAccountCommand(bookAccess, requestFile, outputMode);
  }

  int runDeclareTaxRegistrationCommand(
      BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    DeclareTaxRegistrationCommand command =
        requestReader.readDeclareTaxRegistrationCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.declareTaxRegistration(bookAccess, command),
        result -> responseWriter.writeDeclareTaxRegistrationResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runAmendAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return accountRegistryCommands.runAmendAccountCommand(bookAccess, requestFile, outputMode);
  }

  int runRetireAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return accountRegistryCommands.runRetireAccountCommand(bookAccess, requestFile, outputMode);
  }

  int runInterimResultSweepCommand(
      BookAccess bookAccess, LocalDate throughEffectiveDate, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.interimResultSweep(
            bookAccess, new InterimResultSweepCommand(throughEffectiveDate)),
        result -> responseWriter.writeInterimResultSweepResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runFiscalYearCloseCommand(BookAccess bookAccess, int fiscalYearLabel, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.fiscalYearClose(bookAccess, new FiscalYearCloseCommand(fiscalYearLabel)),
        result -> responseWriter.writeFiscalYearCloseResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }
}
