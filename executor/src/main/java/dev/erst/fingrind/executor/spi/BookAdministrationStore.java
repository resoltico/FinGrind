package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.time.Instant;

/** Writes administrative book and account-registry mutations. */
public interface BookAdministrationStore {
  /** Explicitly initializes one new book if the selected path is currently empty. */
  BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity);

  /** Declares or reactivates one account in the selected book. */
  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt);
}
