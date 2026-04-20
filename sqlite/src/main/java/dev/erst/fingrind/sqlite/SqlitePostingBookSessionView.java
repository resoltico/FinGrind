package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingIdGenerator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Narrow posting-session view over one SQLite-backed store. */
final class SqlitePostingBookSessionView implements PostingBookSession {
  private final SqlitePostingFactStore store;

  SqlitePostingBookSessionView(SqlitePostingFactStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public boolean isInitialized() {
    return store.isInitialized();
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return store.findAccount(accountCode);
  }

  @Override
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return store.findAccounts(accountCodes);
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return store.findExistingPosting(idempotencyKey);
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return store.findPosting(postingId);
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return store.findReversalFor(priorPostingId);
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return store.commit(postingDraft, postingIdGenerator);
  }
}
