package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Runs account-registry mutations through one request, admission, and response path. */
final class CliAccountRegistryMutationActions {
  private final CliRequestReader requestReader;
  private final CliMutationResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookMutationWorkflow mutationWorkflow;

  CliAccountRegistryMutationActions(
      CliRequestReader requestReader,
      CliMutationResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookMutationWorkflow mutationWorkflow) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.mutationWorkflow = Objects.requireNonNull(mutationWorkflow, "mutationWorkflow");
  }

  int runDeclareAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return runRequestMutation(
        bookAccess,
        requestFile,
        outputMode,
        requestReader::readDeclareAccountCommand,
        mutationWorkflow::declareAccount,
        responseWriter::writeDeclareAccountResult,
        CliAdministrativeExitCodes::exitCodeFor);
  }

  int runAmendAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return runRequestMutation(
        bookAccess,
        requestFile,
        outputMode,
        requestReader::readAmendAccountCommand,
        mutationWorkflow::amendAccount,
        responseWriter::writeAmendAccountResult,
        CliAdministrativeExitCodes::exitCodeFor);
  }

  int runRetireAccountCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    return runRequestMutation(
        bookAccess,
        requestFile,
        outputMode,
        requestReader::readRetireAccountCommand,
        mutationWorkflow::retireAccount,
        responseWriter::writeRetireAccountResult,
        CliAdministrativeExitCodes::exitCodeFor);
  }

  private <C, R> int runRequestMutation(
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode,
      Function<Path, C> requestParser,
      BiFunction<BookAccess, C, ContractDecision<R>> mutation,
      BiConsumer<R, OutputMode> resultWriter,
      ToIntFunction<R> successExitCode) {
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
        mutation.apply(bookAccess, requestParser.apply(requestFile)),
        result -> resultWriter.accept(result, outputMode),
        successExitCode,
        failureWriter,
        outputMode);
  }
}
