package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.*;
import dev.erst.fingrind.core.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** In-memory book session for tests and non-durable harness composition. */
public final class InMemoryBookSession
    implements LedgerPlanSession, BookAdministrationSession, PostingBookSession, BookQuerySession {
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<AccountCode, DeclaredAccount> accountsByCode = mutableMap();
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey = mutableMap();
  private final Map<PostingId, PostingFact> postingsByPostingId = mutableMap();
  private final Map<PostingId, PostingFact> reversalsByPriorPostingId = mutableMap();
  private Snapshot transactionSnapshot;
  private boolean initialized;
  private Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");

  @Override
  public BookAdministrationSession administrationSession() {
    return this;
  }

  @Override
  public PostingBookSession postingSession() {
    return this;
  }

  @Override
  public BookQuerySession querySession() {
    return this;
  }

  @Override
  public BookInspection inspectBook() {
    return withLock(
        () -> {
          if (!initialized) {
            return new BookInspection.Missing(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
          }
          return new BookInspection.Initialized(
              1_179_079_236, 1, 1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE, initializedAt);
        });
  }

  @Override
  public boolean isInitialized() {
    return withLock(() -> initialized);
  }

  @Override
  public OpenBookResult openBook(Instant initializedAt) {
    return withLock(
        () -> {
          if (initialized) {
            return new OpenBookResult.Rejected(
                new BookAdministrationRejection.BookAlreadyInitialized());
          }
          initialized = true;
          this.initializedAt = initializedAt;
          return new OpenBookResult.Opened(initializedAt);
        });
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return withLock(() -> Optional.ofNullable(accountsByCode.get(accountCode)));
  }

  @Override
  @SuppressWarnings("PMD.UseConcurrentHashMap")
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return withLock(
        () -> {
          Map<AccountCode, DeclaredAccount> accounts = new LinkedHashMap<>();
          for (AccountCode accountCode : accountCodes) {
            DeclaredAccount account = accountsByCode.get(accountCode);
            if (account != null) {
              accounts.put(accountCode, account);
            }
          }
          return Map.copyOf(accounts);
        });
  }

  @Override
  public DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return withLock(
        () -> {
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
        });
  }

  @Override
  public AccountPage listAccounts(ListAccountsQuery query) {
    return withLock(
        () -> {
          List<DeclaredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(Comparator.comparing(account -> account.accountCode().value()))
                  .toList();
          int start = Math.min(query.offset(), accounts.size());
          int end = Math.min(start + query.limit(), accounts.size());
          return new AccountPage(
              accounts.subList(start, end), query.limit(), query.offset(), end < accounts.size());
        });
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return withLock(() -> Optional.ofNullable(postingsByIdempotencyKey.get(idempotencyKey)));
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return withLock(() -> Optional.ofNullable(postingsByPostingId.get(postingId)));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return withLock(() -> Optional.ofNullable(reversalsByPriorPostingId.get(priorPostingId)));
  }

  @Override
  public PostingCommitResult commit(
      PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    return withLock(
        () -> {
          Optional<PostingRejection> rejection = PostingValidation.rejectionFor(postingDraft, this);
          if (rejection.isPresent()) {
            return new PostingCommitResult.Rejected(rejection.orElseThrow());
          }
          PostingFact postingFact = postingDraft.materialize(postingIdGenerator.nextPostingId());
          IdempotencyKey idempotencyKey =
              postingFact.provenance().requestProvenance().idempotencyKey();
          PostingFact existingPosting =
              postingsByIdempotencyKey.putIfAbsent(idempotencyKey, postingFact);
          if (existingPosting != null) {
            return new PostingCommitResult.Rejected(new PostingRejection.DuplicateIdempotencyKey());
          }
          postingsByPostingId.put(postingFact.postingId(), postingFact);

          Optional<ReversalReference> reversalReference =
              postingFact.postingLineage().reversalReference();
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
        });
  }

  @Override
  public PostingPage listPostings(ListPostingsQuery query) {
    return withLock(
        () -> {
          List<PostingFact> matchingPostings =
              postingsByPostingId.values().stream()
                  .filter(posting -> matchesAccountFilter(posting, query.accountCode()))
                  .filter(
                      posting ->
                          matchesDateRange(
                              posting, query.effectiveDateFrom(), query.effectiveDateTo()))
                  .filter(posting -> matchesCursor(posting, query.cursor()))
                  .sorted(
                      Comparator.comparing(
                              (PostingFact posting) -> posting.journalEntry().effectiveDate())
                          .reversed()
                          .thenComparing(
                              posting -> posting.provenance().recordedAt(),
                              Comparator.reverseOrder())
                          .thenComparing(
                              posting -> posting.postingId().value(), Comparator.reverseOrder()))
                  .toList();
          int end = Math.min(query.limit(), matchingPostings.size());
          List<PostingFact> pageItems = matchingPostings.subList(0, end);
          return new PostingPage(
              pageItems,
              query.limit(),
              end < matchingPostings.size()
                  ? Optional.of(PostingPageCursor.fromPosting(pageItems.getLast()))
                  : Optional.empty());
        });
  }

  @Override
  public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    return withLock(
        () -> {
          DeclaredAccount account = accountsByCode.get(query.accountCode());
          if (account == null) {
            return Optional.empty();
          }
          Map<CurrencyCode, Totals> totalsByCurrency = mutableMap();
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
          return Optional.of(
              new AccountBalanceSnapshot(
                  account, query.effectiveDateFrom(), query.effectiveDateTo(), balances));
        });
  }

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  @Override
  public void beginLedgerPlanTransaction() {
    withLock(
        () -> {
          if (transactionSnapshot != null) {
            throw new IllegalStateException("Ledger plan transaction is already active.");
          }
          transactionSnapshot =
              new Snapshot(
                  initialized,
                  initializedAt,
                  Map.copyOf(accountsByCode),
                  Map.copyOf(postingsByIdempotencyKey),
                  Map.copyOf(postingsByPostingId),
                  Map.copyOf(reversalsByPriorPostingId));
        });
  }

  @Override
  public void commitLedgerPlanTransaction() {
    withLock(
        () -> {
          if (transactionSnapshot == null) {
            throw new IllegalStateException("No ledger plan transaction is active.");
          }
          transactionSnapshot = null;
        });
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    withLock(
        () -> {
          Snapshot snapshot = transactionSnapshot;
          if (snapshot == null) {
            return;
          }
          initialized = snapshot.initialized();
          initializedAt = snapshot.initializedAt();
          accountsByCode.clear();
          accountsByCode.putAll(snapshot.accountsByCode());
          postingsByIdempotencyKey.clear();
          postingsByIdempotencyKey.putAll(snapshot.postingsByIdempotencyKey());
          postingsByPostingId.clear();
          postingsByPostingId.putAll(snapshot.postingsByPostingId());
          reversalsByPriorPostingId.clear();
          reversalsByPriorPostingId.putAll(snapshot.reversalsByPriorPostingId());
          transactionSnapshot = null;
        });
  }

  /** Deactivates one declared account for fixture-driven tests. */
  public void deactivateAccount(AccountCode accountCode) {
    withLock(
        () -> {
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
        });
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

  private static boolean matchesCursor(
      PostingFact postingFact, Optional<PostingPageCursor> cursor) {
    if (cursor.isEmpty()) {
      return true;
    }
    PostingPageCursor pageCursor = cursor.orElseThrow();
    java.time.LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    Instant recordedAt = postingFact.provenance().recordedAt();
    String postingId = postingFact.postingId().value();
    return effectiveDate.isBefore(pageCursor.effectiveDate())
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.isBefore(pageCursor.recordedAt()))
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.equals(pageCursor.recordedAt())
            && postingId.compareTo(pageCursor.postingId().value()) < 0);
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

  private <T> T withLock(Supplier<T> action) {
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }

  private void withLock(Runnable action) {
    lock.lock();
    try {
      action.run();
    } finally {
      lock.unlock();
    }
  }

  private static <K, V> Map<K, V> mutableMap() {
    return new HashMap<>();
  }

  /** Mutable debit and credit accumulators for one currency bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
  }

  private record Snapshot(
      boolean initialized,
      Instant initializedAt,
      Map<AccountCode, DeclaredAccount> accountsByCode,
      Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey,
      Map<PostingId, PostingFact> postingsByPostingId,
      Map<PostingId, PostingFact> reversalsByPriorPostingId) {}
}
