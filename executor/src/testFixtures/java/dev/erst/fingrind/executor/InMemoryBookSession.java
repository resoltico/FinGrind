package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.core.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory book session for tests and non-durable harness composition. */
public class InMemoryBookSession implements LedgerPlanSession {
  private final Map<AccountCode, DeclaredAccount> accountsByCode = new ConcurrentHashMap<>();
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey =
      new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> postingsByPostingId = new ConcurrentHashMap<>();
  private final Map<PostingId, PostingFact> reversalsByPriorPostingId = new ConcurrentHashMap<>();
  private Snapshot transactionSnapshot;
  private boolean initialized;

  @Override
  public BookInspection inspectBook() {
    if (!initialized) {
      return new BookInspection(
          BookInspection.Status.MISSING,
          false,
          false,
          true,
          null,
          null,
          1,
          "hard-break-no-migration",
          null);
    }
    return new BookInspection(
        BookInspection.Status.INITIALIZED,
        true,
        true,
        false,
        1_179_079_236,
        1,
        1,
        "hard-break-no-migration",
        null);
  }

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
  public AccountPage listAccounts(ListAccountsQuery query) {
    List<DeclaredAccount> accounts =
        accountsByCode.values().stream()
            .sorted(Comparator.comparing(account -> account.accountCode().value()))
            .toList();
    int start = Math.min(query.offset(), accounts.size());
    int end = Math.min(start + query.limit(), accounts.size());
    return new AccountPage(
        accounts.subList(start, end), query.limit(), query.offset(), end < accounts.size());
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey));
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return Optional.ofNullable(postingsByPostingId.get(postingId));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId));
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    Optional<PostingRejection> rejection = PostingValidation.rejectionFor(postingDraft, this);
    if (rejection.isPresent()) {
      return new PostingCommitResult.Rejected(rejection.orElseThrow());
    }
    PostingFact postingFact = postingDraft.materialize(postingIdGenerator.nextPostingId());
    IdempotencyKey idempotencyKey = postingFact.provenance().requestProvenance().idempotencyKey();
    PostingFact existingPosting = postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
    if (existingPosting != null) {
      return new PostingCommitResult.Rejected(new PostingRejection.DuplicateIdempotencyKey());
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
        return new PostingCommitResult.Rejected(
            new PostingRejection.ReversalAlreadyExists(priorPostingId));
      }
    }
    return new PostingCommitResult.Committed(postingFact);
  }

  @Override
  public PostingPage listPostings(ListPostingsQuery query) {
    List<PostingFact> matchingPostings =
        postingsByPostingId.values().stream()
            .filter(posting -> matchesAccountFilter(posting, query.accountCode()))
            .filter(
                posting ->
                    matchesDateRange(posting, query.effectiveDateFrom(), query.effectiveDateTo()))
            .sorted(
                Comparator.comparing(
                        (PostingFact posting) -> posting.journalEntry().effectiveDate())
                    .reversed()
                    .thenComparing(
                        posting -> posting.provenance().recordedAt(), Comparator.reverseOrder())
                    .thenComparing(
                        posting -> posting.postingId().value(), Comparator.reverseOrder()))
            .toList();
    int start = Math.min(query.offset(), matchingPostings.size());
    int end = Math.min(start + query.limit(), matchingPostings.size());
    return new PostingPage(
        matchingPostings.subList(start, end),
        query.limit(),
        query.offset(),
        end < matchingPostings.size());
  }

  @Override
  public AccountBalanceSnapshot accountBalance(AccountBalanceQuery query) {
    DeclaredAccount account = accountsByCode.get(query.accountCode());
    if (account == null) {
      throw new IllegalArgumentException("Account is not declared: " + query.accountCode().value());
    }
    Map<CurrencyCode, Totals> totalsByCurrency = new ConcurrentHashMap<>();
    postingsByPostingId.values().stream()
        .filter(
            posting ->
                matchesDateRange(posting, query.effectiveDateFrom(), query.effectiveDateTo()))
        .flatMap(posting -> posting.journalEntry().lines().stream())
        .filter(line -> line.accountCode().equals(query.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    List<CurrencyBalance> balances =
        totalsByCurrency.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .map(entry -> balance(entry.getKey(), entry.getValue(), account.normalBalance()))
            .toList();
    return new AccountBalanceSnapshot(
        account, query.effectiveDateFrom(), query.effectiveDateTo(), balances);
  }

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  @Override
  public void beginLedgerPlanTransaction() {
    if (transactionSnapshot != null) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    transactionSnapshot =
        new Snapshot(
            initialized,
            Map.copyOf(accountsByCode),
            Map.copyOf(postingsByIdempotencyKey),
            Map.copyOf(postingsByPostingId),
            Map.copyOf(reversalsByPriorPostingId));
  }

  @Override
  public void commitLedgerPlanTransaction() {
    if (transactionSnapshot == null) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    transactionSnapshot = null;
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    Snapshot snapshot = transactionSnapshot;
    if (snapshot == null) {
      return;
    }
    initialized = snapshot.initialized();
    accountsByCode.clear();
    accountsByCode.putAll(snapshot.accountsByCode());
    postingsByIdempotencyKey.clear();
    postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
    postingsByPostingId.clear();
    postingsByPostingId.putAll(snapshot.postingsByPostingId());
    reversalsByPriorPostingId.clear();
    reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
    transactionSnapshot = null;
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

  private static boolean matchesAccountFilter(
      PostingFact postingFact, Optional<AccountCode> accountCode) {
    return accountCode.isEmpty()
        || postingFact.journalEntry().lines().stream()
            .anyMatch(line -> line.accountCode().equals(accountCode.orElseThrow()));
  }

  private static boolean matchesDateRange(
      PostingFact postingFact,
      Optional<java.time.LocalDate> effectiveDateFrom,
      Optional<java.time.LocalDate> effectiveDateTo) {
    java.time.LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    return effectiveDateFrom.stream().allMatch(date -> !effectiveDate.isBefore(date))
        && effectiveDateTo.stream().allMatch(date -> !effectiveDate.isAfter(date));
  }

  private static void accumulate(Map<CurrencyCode, Totals> totalsByCurrency, JournalLine line) {
    Totals totals =
        totalsByCurrency.computeIfAbsent(
            line.amount().currencyCode(), ignoredCurrencyCode -> new Totals());
    if (line.side() == JournalLine.EntrySide.DEBIT) {
      totals.debit = totals.debit.add(line.amount().amount());
      return;
    }
    totals.credit = totals.credit.add(line.amount().amount());
  }

  private static CurrencyBalance balance(
      CurrencyCode currencyCode, Totals totals, NormalBalance accountNormalBalance) {
    BigDecimal net = totals.debit.subtract(totals.credit);
    BigDecimal absoluteNet = net.abs();
    NormalBalance balanceSide = net.signum() >= 0 ? NormalBalance.DEBIT : NormalBalance.CREDIT;
    if (absoluteNet.signum() == 0) {
      balanceSide = accountNormalBalance;
    }
    return new CurrencyBalance(
        new Money(currencyCode, totals.debit),
        new Money(currencyCode, totals.credit),
        new Money(currencyCode, absoluteNet),
        balanceSide);
  }

  /** Mutable debit and credit accumulators for one currency bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
  }

  private record Snapshot(
      boolean initialized,
      Map<AccountCode, DeclaredAccount> accountsByCode,
      Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey,
      Map<PostingId, PostingFact> postingsByPostingId,
      Map<PostingId, PostingFact> reversalsByPriorPostingId) {}
}
