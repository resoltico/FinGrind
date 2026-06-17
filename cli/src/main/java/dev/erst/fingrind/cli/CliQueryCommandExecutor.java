package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;
import java.util.Optional;

/** Executes query-only CLI commands that read book state without producing PDF artifacts. */
final class CliQueryCommandExecutor {
  private final CliBookReadResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookReadWorkflow readWorkflow;

  CliQueryCommandExecutor(
      CliBookReadResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookReadWorkflow readWorkflow) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.readWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
  }

  int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        readWorkflow.inspectBook(bookAccess),
        inspection ->
            responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode),
        ignored -> 0,
        failureWriter);
  }

  int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        readWorkflow.listAccounts(bookAccess, query),
        result -> responseWriter.writeListAccountsResult(result, outputMode),
        CliBookQueryExitCodes::exitCodeFor,
        failureWriter);
  }

  int runGetPostingCommand(BookAccess bookAccess, PostingId postingId, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        readWorkflow.getPosting(bookAccess, postingId),
        result -> responseWriter.writeGetPostingResult(result, outputMode),
        CliBookQueryExitCodes::exitCodeFor,
        failureWriter);
  }

  int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(failure, failureWriter));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        readWorkflow.listPostings(bookAccess, query),
        result -> responseWriter.writeListPostingsResult(result, outputMode),
        CliBookQueryExitCodes::exitCodeFor,
        failureWriter);
  }
}
