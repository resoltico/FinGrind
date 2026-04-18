package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.Optional;

/** Read-only seam for inspecting books, listings, and balances. */
public interface BookQuerySession extends AutoCloseable {
  /** Inspects the selected book file without mutating it. */
  BookInspection inspectBook();

  /** Reports whether the selected book already carries the explicit initialization marker. */
  boolean isInitialized();

  /** Returns one paginated slice of the declared account registry for one initialized book. */
  AccountPage listAccounts(ListAccountsQuery query);

  /** Looks up one declared account in one initialized book. */
  Optional<DeclaredAccount> findAccount(AccountCode accountCode);

  /** Looks up one committed posting fact by durable posting identity in one initialized book. */
  Optional<PostingFact> findPosting(PostingId postingId);

  /** Returns one filtered page of postings in a stable order from one initialized book. */
  PostingPage listPostings(ListPostingsQuery query);

  /** Computes grouped per-currency balances for one declared account in one initialized book. */
  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query);

  @Override
  void close();
}
