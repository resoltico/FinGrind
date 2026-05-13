package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Executes query-only CLI commands that read book state without producing PDF artifacts. */
final class CliQueryCommandExecutor {
  private final CliResponseWriter responseWriter;
  private final CliBookWorkflow bookWorkflow;

  CliQueryCommandExecutor(CliResponseWriter responseWriter, CliBookWorkflow bookWorkflow) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.bookWorkflow = Objects.requireNonNull(bookWorkflow, "bookWorkflow");
  }

  int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.inspectBook(bookAccess),
        outputMode,
        inspection ->
            responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode),
        ignored -> 0,
        responseWriter);
  }

  int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.listAccounts(bookAccess, query),
        outputMode,
        result -> responseWriter.writeListAccountsResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runGetPostingCommand(BookAccess bookAccess, PostingId postingId, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.getPosting(bookAccess, postingId),
        outputMode,
        result -> responseWriter.writeGetPostingResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }

  int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    return CliCommandOutcomeWriter.writeResolvedResult(
        bookWorkflow.listPostings(bookAccess, query),
        outputMode,
        result -> responseWriter.writeListPostingsResult(result, outputMode),
        CliExecutionPolicy::exitCodeFor,
        responseWriter);
  }
}
