package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.TrialBalanceQuery;
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
