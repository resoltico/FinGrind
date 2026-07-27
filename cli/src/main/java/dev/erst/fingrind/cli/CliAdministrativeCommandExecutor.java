package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Executes administrative CLI commands that mutate book setup or key material. */
final class CliAdministrativeCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliMutationResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookLifecycleWorkflow lifecycleWorkflow;
  private final CliBookMutationWorkflow mutationWorkflow;
  private final CliAccountRegistryMutationActions accountRegistryCommands;
  private final CliAttestationKeyFileWorkflow attestationKeyFileWorkflow;

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
    this.attestationKeyFileWorkflow =
        new CliAttestationKeyFileWorkflow(this.responseWriter, this.failureWriter);
  }

  int runGenerateAttestationKeyFileCommand(
      dev.erst.fingrind.core.attestation.AttestationCustodian custodian,
      Path attestationKeyFilePath,
      Path passphraseFilePath,
      OutputMode outputMode) {
    return attestationKeyFileWorkflow.generate(
        custodian, attestationKeyFilePath, passphraseFilePath, outputMode);
  }

  int runInspectAttestationKeyFileCommand(
      dev.erst.fingrind.core.attestation.AttestationCustodian custodian,
      Path attestationKeyFilePath,
      OutputMode outputMode) {
    return attestationKeyFileWorkflow.inspect(custodian, attestationKeyFilePath, outputMode);
  }

  int runGenerateBookKeyFileCommand(Path bookKeyFilePath, OutputMode outputMode) {
    return SqliteBookKeyFileGenerator.generateDecision(bookKeyFilePath)
        .fold(
            (GeneratedBookKeyFile generatedKeyFile) -> {
              responseWriter.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
              return 0;
            },
            failure ->
                CliCommandOutcomeWriter.writeDeterministicFailure(
                    failure, failureWriter, outputMode));
  }

  int runOpenBookCommand(BookAccess bookAccess, OpenBookCommand command, OutputMode outputMode) {
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
              responseWriter.writeOpenBookResult(bookAccess.bookFilePath(), result, outputMode);
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
        result -> responseWriter.writeRekeyBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runBackupBookCommand(
      BookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      java.util.UUID backupId,
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
        lifecycleWorkflow.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath, backupId),
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
      java.util.List<dev.erst.fingrind.core.attestation.AttestationCredentialSource>
          attestationCredentialSources,
      OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.restoreBook(
            bookFilePath,
            newBookKeyFilePath,
            backupFilePath,
            backupKeyFilePath,
            attestationCredentialSources),
        result -> responseWriter.writeRestoreBookResult(result, outputMode),
        CliAdministrativeExitCodes::exitCodeFor,
        failureWriter,
        outputMode);
  }

  int runAttestationRegistryMutationCommand(
      OperationId operationId, BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    dev.erst.fingrind.core.attestation.AttestationRegistryMutation mutation =
        requestReader.readAttestationRegistryMutation(requestFile, operationId);
    return CliCommandOutcomeWriter.writeResolvedResult(
        lifecycleWorkflow.mutateRegistry(bookAccess, mutation),
        result ->
            responseWriter.writeAttestationRegistryMutationResult(operationId, result, outputMode),
        CliAttestationExitCodes::exitCodeFor,
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
