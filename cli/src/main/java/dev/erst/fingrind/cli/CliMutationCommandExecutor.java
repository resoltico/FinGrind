package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Executes posting and ledger-plan commands that consume request payloads. */
final class CliMutationCommandExecutor {
  private final CliRequestReader requestReader;
  private final CliMutationResponseWriter mutationResponseWriter;
  private final CliPlanResponseWriter planResponseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookMutationWorkflow mutationWorkflow;

  CliMutationCommandExecutor(
      CliRequestReader requestReader,
      CliMutationResponseWriter mutationResponseWriter,
      CliPlanResponseWriter planResponseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookMutationWorkflow mutationWorkflow) {
    this.requestReader = Objects.requireNonNull(requestReader, "requestReader");
    this.mutationResponseWriter =
        Objects.requireNonNull(mutationResponseWriter, "mutationResponseWriter");
    this.planResponseWriter = Objects.requireNonNull(planResponseWriter, "planResponseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.mutationWorkflow = Objects.requireNonNull(mutationWorkflow, "mutationWorkflow");
  }

  int runExecutePlanCommand(
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode,
      PlanResultDetail resultDetail) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, outputMode, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    LedgerPlan plan = requestReader.readLedgerPlan(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.executePlan(bookAccess, plan),
        outputMode,
        result -> planResponseWriter.writeLedgerPlanResult(result, outputMode, resultDetail),
        CliPostingExitCodes::exitCodeFor,
        failureWriter);
  }

  int runPreflightEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, outputMode, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.preflight(bookAccess, command),
        outputMode,
        result -> mutationResponseWriter.writePostEntryResult(result, outputMode),
        CliPostingExitCodes::exitCodeFor,
        failureWriter);
  }

  int runPostEntryCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, outputMode, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    return CliCommandOutcomeWriter.writeResolvedResult(
        mutationWorkflow.commit(bookAccess, command),
        outputMode,
        result -> mutationResponseWriter.writePostEntryResult(result, outputMode),
        CliPostingExitCodes::exitCodeFor,
        failureWriter);
  }
}
