package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.time.Instant;

/** Administration-only seam over an already-open book boundary; lifecycle stays with the owner. */
public interface BookAdministrationSession {
  /** Explicitly initializes one new book if the selected path is currently empty. */
  BookOpeningOutcome openBook(Instant initializedAt);

  /** Declares or reactivates one account in the selected book. */
  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt);
}
