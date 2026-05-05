package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingIdGenerator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Narrow posting-session view over one SQLite-backed store. */
final class SqlitePostingBookSessionView implements PostingBookSession {
  private final SqliteStoreReadOperations readOperations;
  private final SqliteStoreMutationOperations mutationOperations;

  SqlitePostingBookSessionView(
      SqliteStoreReadOperations readOperations, SqliteStoreMutationOperations mutationOperations) {
    this.readOperations = Objects.requireNonNull(readOperations, "readOperations");
    this.mutationOperations = Objects.requireNonNull(mutationOperations, "mutationOperations");
  }

  @Override
  public boolean isInitialized() {
    return readOperations.isInitialized();
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return readOperations.findAccount(accountCode);
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return readOperations.findAccounts(accountCodes);
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return readOperations.findExistingPosting(idempotencyKey);
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return readOperations.findPosting(postingId);
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return readOperations.findReversalFor(priorPostingId);
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return mutationOperations.commit(postingDraft, postingIdGenerator);
  }
}
