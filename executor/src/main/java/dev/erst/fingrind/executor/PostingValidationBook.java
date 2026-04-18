package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lookup-only seam used by posting validation rules in both preflight and commit paths. */
public interface PostingValidationBook {
  /** Reports whether the selected book is initialized and eligible for posting rules. */
  boolean isInitialized();

  /** Looks up one declared account in the selected book. */
  Optional<DeclaredAccount> findAccount(AccountCode accountCode);

  /** Looks up the supplied declared accounts in one batch when the store can do so efficiently. */
  @SuppressWarnings("PMD.UseConcurrentHashMap")
  default Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    Map<AccountCode, DeclaredAccount> accounts = new LinkedHashMap<>();
    for (AccountCode accountCode : accountCodes) {
      findAccount(accountCode).ifPresent(account -> accounts.put(accountCode, account));
    }
    return Map.copyOf(accounts);
  }

  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by durable posting identity. */
  Optional<PostingFact> findPosting(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<PostingFact> findReversalFor(PostingId priorPostingId);
}
