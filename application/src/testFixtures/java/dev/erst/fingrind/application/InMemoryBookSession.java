package dev.erst.fingrind.application;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReference;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory book session for tests and non-durable harness composition. */
public final class InMemoryBookSession implements BookSession {
  private final Map<AccountCode, DeclaredAccount> accountsByCode = new ConcurrentHashMap<>();
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey =
      new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> postingsByPostingId = new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> reversalsByPriorPostingId = new ConcurrentHashMap<>();
  private boolean initialized;

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public OpenBookResult openBook(Instant initializedAt) {
    if (initialized) {
      return new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized());
    }
    initialized = true;
    return new OpenBookResult.Opened(initializedAt);
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return Optional.ofNullable(accountsByCode.get(accountCode));
  }

  @Override
  public DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    if (!initialized) {
      return new DeclareAccountResult.Rejected(
          new BookAdministrationRejection.BookNotInitialized());
    }
    DeclaredAccount existingAccount = accountsByCode.get(accountCode);
    if (existingAccount != null && existingAccount.normalBalance() != normalBalance) {
      return new DeclareAccountResult.Rejected(
          new BookAdministrationRejection.NormalBalanceConflict(
              accountCode, existingAccount.normalBalance(), normalBalance));
    }
    DeclaredAccount declaredAccount =
        new DeclaredAccount(
            accountCode,
            accountName,
            existingAccount == null ? normalBalance : existingAccount.normalBalance(),
            true,
            existingAccount == null ? declaredAt : existingAccount.declaredAt());
    accountsByCode.put(accountCode, declaredAccount);
    return new DeclareAccountResult.Declared(declaredAccount);
  }

  @Override
  public List<DeclaredAccount> listAccounts() {
    return accountsByCode.values().stream()
        .sorted(Comparator.comparing(account -> account.accountCode().value()))
        .toList();
  }

  @Override
  public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
    return Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey));
  }

  @Override
  public Optional<PostingFact> findByPostingId(PostingId postingId) {
    return Optional.ofNullable(postingsByPostingId.get(postingId));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId));
  }

  @Override
  public PostingCommitResult commit(PostingFact postingFact) {
    if (!initialized) {
      return new PostingCommitResult.BookNotInitialized();
    }
    Optional<PostingCommitResult> accountOutcome = accountOutcome(postingFact);
    if (accountOutcome.isPresent()) {
      return accountOutcome.orElseThrow();
    }
    IdempotencyKey idempotencyKey = postingFact.provenance().requestProvenance().idempotencyKey();
    PostingFact existingPosting = postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
    if (existingPosting != null) {
      return new PostingCommitResult.DuplicateIdempotency(idempotencyKey);
    }
    postingsByPostingId.put(postingFact.postingId(), postingFact);

    Optional<ReversalReference> reversalReference = postingFact.reversalReference();
    if (reversalReference.isPresent()) {
      ReversalReference postedReversal = reversalReference.orElseThrow();
      PostingId priorPostingId = postedReversal.priorPostingId();
      PostingFact existingReversal =
          reversalsByPriorPostingId.putIfAbsent(priorPostingId, postingFact);
      if (existingReversal != null) {
        postingsByIdempotencyKey.remove(idempotencyKey, postingFact);
        postingsByPostingId.remove(postingFact.postingId(), postingFact);
        return new PostingCommitResult.DuplicateReversalTarget(priorPostingId);
      }
    }
    return new PostingCommitResult.Committed(postingFact);
  }

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  /** Deactivates one declared account for fixture-driven tests. */
  public void deactivateAccount(AccountCode accountCode) {
    DeclaredAccount existingAccount = accountsByCode.get(accountCode);
    if (existingAccount == null) {
      throw new IllegalArgumentException("Account is not declared: " + accountCode.value());
    }
    accountsByCode.put(
        accountCode,
        new DeclaredAccount(
            existingAccount.accountCode(),
            existingAccount.accountName(),
            existingAccount.normalBalance(),
            false,
            existingAccount.declaredAt()));
  }

  private Optional<PostingCommitResult> accountOutcome(PostingFact postingFact) {
    for (var line : postingFact.journalEntry().lines()) {
      DeclaredAccount account = accountsByCode.get(line.accountCode());
      if (account == null) {
        return unknownAccount(line.accountCode());
      }
      if (!account.active()) {
        return inactiveAccount(line.accountCode());
      }
    }
    return Optional.empty();
  }

  private static Optional<PostingCommitResult> unknownAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.UnknownAccount(accountCode));
  }

  private static Optional<PostingCommitResult> inactiveAccount(AccountCode accountCode) {
    return Optional.of(new PostingCommitResult.InactiveAccount(accountCode));
  }
}
