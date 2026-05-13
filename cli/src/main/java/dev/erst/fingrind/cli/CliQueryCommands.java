package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.PostingId;
import java.util.Objects;

/** Query CLI commands that inspect existing book data without mutating it. */
record InspectBook(BookAccess bookAccess, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  InspectBook {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runInspectBookCommand(bookAccess, outputMode);
  }
}

/** Query CLI commands that inspect existing book data without mutating it. */
record ListAccounts(BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  ListAccounts {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runListAccountsCommand(bookAccess, query, outputMode);
  }
}

/** Query CLI commands that inspect existing book data without mutating it. */
record GetPosting(BookAccess bookAccess, PostingId postingId, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  GetPosting {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runGetPostingCommand(bookAccess, postingId, outputMode);
  }
}

/** Query CLI commands that inspect existing book data without mutating it. */
record ListPostings(BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  ListPostings {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runListPostingsCommand(bookAccess, query, outputMode);
  }
}
