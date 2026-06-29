package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;
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
final class ListAccounts extends CliBookQueryOutputModeCommand<ListAccountsQuery> {
  ListAccounts(BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode) {
    super(bookAccess, query, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      ListAccountsQuery query,
      OutputMode outputMode) {
    return executionContext.query().runListAccountsCommand(bookAccess, query, outputMode);
  }
}

/** Query CLI commands that inspect declared tax registrations without mutating state. */
final class ListTaxRegistrations extends CliBookQueryOutputModeCommand<ListTaxRegistrationsQuery> {
  ListTaxRegistrations(
      BookAccess bookAccess, ListTaxRegistrationsQuery query, OutputMode outputMode) {
    super(bookAccess, query, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      ListTaxRegistrationsQuery query,
      OutputMode outputMode) {
    return executionContext.query().runListTaxRegistrationsCommand(bookAccess, query, outputMode);
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
final class ListPostings extends CliBookQueryOutputModeCommand<ListPostingsQuery> {
  ListPostings(BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode) {
    super(bookAccess, query, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      ListPostingsQuery query,
      OutputMode outputMode) {
    return executionContext.query().runListPostingsCommand(bookAccess, query, outputMode);
  }
}

/** Query CLI command that computes one bounded tax-obligation report. */
final class TaxObligation extends CliBookQueryOutputModeCommand<TaxObligationQuery> {
  TaxObligation(BookAccess bookAccess, TaxObligationQuery query, OutputMode outputMode) {
    super(bookAccess, query, outputMode);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      TaxObligationQuery query,
      OutputMode outputMode) {
    return executionContext.query().runTaxObligationCommand(bookAccess, query, outputMode);
  }
}
