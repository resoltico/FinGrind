package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;

/** Administration-only seam for initializing books and declaring accounts. */
public interface BookAdministrationSession extends AutoCloseable {
  /** Explicitly initializes one new book if the selected path is currently empty. */
  OpenBookResult openBook(Instant initializedAt);

  /** Declares or reactivates one account in the selected book. */
  DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt);

  @Override
  void close();
}
