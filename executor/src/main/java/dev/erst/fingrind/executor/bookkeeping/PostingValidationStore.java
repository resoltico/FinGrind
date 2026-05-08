package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Public validation store used by posting preflight and transactional commit policies. */
public interface PostingValidationStore {
  /** Returns one local lifecycle snapshot for initialized-book posting validation. */
  BookLifecycleInspection inspectBook();

  /** Looks up one declared account in the selected book. */
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Looks up the supplied declared accounts in one batch when the store can do so efficiently. */
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return accountCodes.stream()
        .map(accountCode -> Map.entry(accountCode, findAccount(accountCode)))
        .flatMap(
            entry -> entry.getValue().stream().map(account -> Map.entry(entry.getKey(), account)))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** Looks up one existing posting fact by book-local idempotency identity. */
  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Looks up one existing posting fact by durable posting identity. */
  Optional<CommittedPosting> findPosting(PostingId postingId);

  /** Looks up an existing full reversal for one prior posting, if such a reversal exists. */
  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId);
}
