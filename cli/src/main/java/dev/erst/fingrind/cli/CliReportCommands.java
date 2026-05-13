package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.util.Objects;

/** Report CLI commands that can render to terminal output or a PDF file. */
record AccountBalance(
    BookAccess bookAccess, AccountBalanceQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  AccountBalance {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runAccountBalanceCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record TrialBalance(BookAccess bookAccess, TrialBalanceQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  TrialBalance {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runTrialBalanceCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record AccountLedger(
    BookAccess bookAccess, AccountLedgerQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  AccountLedger {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runAccountLedgerCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record PeriodSummary(
    BookAccess bookAccess, PeriodSummaryQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  PeriodSummary {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runPeriodSummaryCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record FinancialPosition(
    BookAccess bookAccess, FinancialPositionQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  FinancialPosition {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runFinancialPositionCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record IncomeStatement(
    BookAccess bookAccess, IncomeStatementQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  IncomeStatement {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runIncomeStatementCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
record ChangesInEquity(
    BookAccess bookAccess, ChangesInEquityQuery query, CliCommand.ReportOutput output)
    implements CliCommand.ReportCommand {
  ChangesInEquity {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(output, "output");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .report()
        .runChangesInEquityCommand(bookAccess, query, output);
  }
}
