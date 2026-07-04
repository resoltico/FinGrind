package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import java.util.Objects;

/** Translates published request commands into the local bookkeeping working model. */
public final class BookkeepingRequestPublishedLanguageTranslator {
  private BookkeepingRequestPublishedLanguageTranslator() {}

  /** Translates one public declare-account request into the local bookkeeping model. */
  public static AccountDeclaration fromPublished(DeclareAccountCommand command) {
    Objects.requireNonNull(command, "command");
    return new AccountDeclaration(
        command.accountCode(),
        command.accountName(),
        command.accountType(),
        command.accountTaxonomy());
  }

  /** Translates one public interim-result-sweep request into the local bookkeeping model. */
  public static java.time.LocalDate fromPublished(InterimResultSweepCommand command) {
    Objects.requireNonNull(command, "command");
    return command.throughEffectiveDate();
  }

  /** Translates one public fiscal-year-close request into the local bookkeeping model. */
  public static int fromPublished(FiscalYearCloseCommand command) {
    Objects.requireNonNull(command, "command");
    return command.fiscalYearLabel();
  }

  /** Translates one public open-book request into the local identity model. */
  public static dev.erst.fingrind.core.BookIdentity fromPublished(OpenBookCommand command) {
    Objects.requireNonNull(command, "command");
    return command.bookIdentity();
  }
}
