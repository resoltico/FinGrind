package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** In-memory book session for tests and non-durable harness composition. */
public final class InMemoryBookSession
    implements LedgerPlanSession,
        BookAdministrationSession,
        PostingBookSession,
        BookReadSession,
        AutoCloseable {
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<AccountCode, DeclaredAccount> accountsByCode = mutableMap();
  private final Map<IdempotencyKey, PostingFact> postingsByIdempotencyKey = mutableMap();
  private final Map<PostingId, PostingFact> postingsByPostingId = mutableMap();
  private final Map<PostingId, PostingFact> reversalsByPriorPostingId = mutableMap();
  private @Nullable Snapshot transactionSnapshot;
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
  public BookReadSession readSession() {
    return this;
  }

  @Override
  public BookInspection inspectBook() {
    return withLock(
        () -> {
          if (!initialized) {
            return new BookInspection.Missing(
                1, dev.erst.fingrind.contract.BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
          }
          return new BookInspection.Initialized(
              1_179_079_236,
              1,
              1,
              dev.erst.fingrind.contract.BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
              initializedAt);
        });
  }

  @Override
  public boolean isInitialized() {
    return withLock(() -> initialized);
  }

  @Override
  public dev.erst.fingrind.contract.OpenBookResult openBook(Instant initializedAt) {
    return withLock(
        () -> {
          if (initialized) {
            return new dev.erst.fingrind.contract.OpenBookResult.Rejected(
                new BookAdministrationRejection.BookAlreadyInitialized());
          }
          initialized = true;
          this.initializedAt = initializedAt;
          return new dev.erst.fingrind.contract.OpenBookResult.Opened(initializedAt);
        });
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return withLock(() -> Optional.ofNullable(accountsByCode.get(accountCode)));
  }

  @Override
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return withLock(
        () ->
            accountCodes.stream()
                .filter(accountsByCode::containsKey)
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        accountCode -> accountCode, accountsByCode::get)));
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
  public dev.erst.fingrind.contract.AccountPage listAccounts(ListAccountsQuery query) {
    return withLock(
        () -> {
          List<DeclaredAccount> accounts =
              accountsByCode.values().stream()
                  .sorted(Comparator.comparing(account -> account.accountCode().value()))
                  .filter(account -> matchesAccountCursor(account, query.cursor()))
                  .toList();
          int end = Math.min(query.limit(), accounts.size());
          List<DeclaredAccount> pageItems = accounts.subList(0, end);
          return new dev.erst.fingrind.contract.AccountPage(
              pageItems,
              query.limit(),
              end < accounts.size()
                  ? Optional.of(AccountPageCursor.fromAccount(pageItems.getLast()))
                  : Optional.empty());
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

          Optional<dev.erst.fingrind.core.ReversalReference> reversalReference =
              postingFact.postingLineage().reversalReference();
          if (reversalReference.isPresent()) {
            dev.erst.fingrind.core.ReversalReference postedReversal =
                reversalReference.orElseThrow();
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
                  ? Optional.of(
                      dev.erst.fingrind.contract.PostingPageCursor.fromPosting(pageItems.getLast()))
                  : Optional.empty());
        });
  }

  @Override
  public Optional<dev.erst.fingrind.contract.AccountBalanceSnapshot> accountBalance(
      AccountBalanceQuery query) {
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
          List<dev.erst.fingrind.contract.CurrencyBalance> balances =
              totalsByCurrency.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                  .map(entry -> balance(entry.getKey(), entry.getValue()))
                  .toList();
          return Optional.of(
              new dev.erst.fingrind.contract.AccountBalanceSnapshot(
                  account, query.effectiveDateFrom(), query.effectiveDateTo(), balances));
        });
  }

  @Override
  public TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    return withLock(
        () ->
            new TrialBalanceReport(
                query.effectiveDateTo(),
                accountsByCode.values().stream()
                    .sorted(Comparator.comparing(account -> account.accountCode().value()))
                    .flatMap(
                        account ->
                            balancesFor(
                                    account,
                                    postingsByPostingId.values().stream()
                                        .filter(
                                            posting ->
                                                query.effectiveDateTo().stream()
                                                    .allMatch(
                                                        date ->
                                                            !posting
                                                                .journalEntry()
                                                                .effectiveDate()
                                                                .isAfter(date)))
                                        .toList())
                                .stream()
                                .map(balance -> new TrialBalanceRow(account, balance)))
                    .toList()));
  }

  @Override
  public AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    return withLock(
        () -> {
          EffectiveDateRange range =
              EffectiveDateRange.of(
                  query.effectiveDateFrom().orElse(null), query.effectiveDateTo().orElse(null));
          List<PostingFact> orderedPostings =
              postingsByPostingId.values().stream()
                  .sorted(
                      Comparator.comparing(
                              (PostingFact posting) -> posting.journalEntry().effectiveDate())
                          .thenComparing(posting -> posting.provenance().recordedAt())
                          .thenComparing(posting -> posting.postingId().value()))
                  .filter(
                      posting ->
                          posting.journalEntry().lines().stream()
                              .anyMatch(line -> line.accountCode().equals(account.accountCode())))
                  .toList();
          List<CurrencyBalance> openingBalances =
              balancesFor(
                  account,
                  orderedPostings.stream()
                      .filter(
                          posting ->
                              query.effectiveDateFrom().stream()
                                  .allMatch(
                                      lowerBound ->
                                          posting
                                              .journalEntry()
                                              .effectiveDate()
                                              .isBefore(lowerBound)))
                      .toList());
          Map<CurrencyCode, Totals> runningTotals = totalsMap(openingBalances);
          List<AccountLedgerEntry> entries = new java.util.ArrayList<>();
          orderedPostings.stream()
              .filter(posting -> range.contains(posting.journalEntry().effectiveDate()))
              .forEach(
                  posting -> {
                    CurrencyBalance movement = movementFor(account, posting);
                    Totals totals =
                        runningTotals.computeIfAbsent(
                            movement.netAmount().currencyCode(), ignored -> new Totals());
                    totals.debit = totals.debit.add(movement.debitTotal().amount());
                    totals.credit = totals.credit.add(movement.creditTotal().amount());
                    dev.erst.fingrind.contract.CurrencyBalance runningBalance =
                        balance(movement.netAmount().currencyCode(), totals);
                    entries.add(
                        new AccountLedgerEntry(
                            posting,
                            movement,
                            runningBalance.netAmount(),
                            runningBalance.balanceSide()));
                  });
          List<CurrencyBalance> closingBalances = balancesFromTotals(runningTotals);
          return new AccountLedgerReport(account, range, openingBalances, entries, closingBalances);
        });
  }

  @Override
  public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    return withLock(
        () -> {
          List<PostingFact> postings =
              postingsByPostingId.values().stream()
                  .filter(
                      posting ->
                          !posting
                                  .journalEntry()
                                  .effectiveDate()
                                  .isBefore(query.effectiveDateFrom())
                              && !posting
                                  .journalEntry()
                                  .effectiveDate()
                                  .isAfter(query.effectiveDateTo()))
                  .toList();
          Map<CurrencyCode, Totals> currencyTotals = mutableMap();
          Map<AccountCode, Map<CurrencyCode, Totals>> accountTotals = mutableMap();
          postings.stream()
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .forEach(
                  line -> {
                    accumulate(currencyTotals, line);
                    accountTotals
                        .computeIfAbsent(line.accountCode(), ignored -> mutableMap())
                        .computeIfAbsent(line.amount().currencyCode(), ignored -> new Totals());
                    accumulate(accountTotals.get(line.accountCode()), line);
                  });
          List<PeriodCurrencySummary> currencySummaries =
              currencyTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                  .map(
                      entry -> new PeriodCurrencySummary(balance(entry.getKey(), entry.getValue())))
                  .toList();
          List<PeriodAccountActivityRow> accountActivity =
              accountTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                  .flatMap(
                      entry -> {
                        DeclaredAccount account =
                            Objects.requireNonNull(accountsByCode.get(entry.getKey()), "account");
                        return entry.getValue().entrySet().stream()
                            .sorted(Comparator.comparing(currency -> currency.getKey().value()))
                            .map(
                                currencyEntry ->
                                    new PeriodAccountActivityRow(
                                        account,
                                        balance(currencyEntry.getKey(), currencyEntry.getValue())));
                      })
                  .toList();
          long accountsTouched =
              postings.stream()
                  .flatMap(posting -> posting.journalEntry().lines().stream())
                  .map(JournalLine::accountCode)
                  .distinct()
                  .count();
          int postingLineCount =
              postings.stream().mapToInt(posting -> posting.journalEntry().lines().size()).sum();
          return new PeriodSummaryReport(
              query.effectiveDateFrom(),
              query.effectiveDateTo(),
              postings.size(),
              postingLineCount,
              Math.toIntExact(accountsTouched),
              currencySummaries,
              accountActivity);
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

  private static boolean matchesAccountCursor(
      DeclaredAccount account, Optional<AccountPageCursor> cursor) {
    return cursor.isEmpty()
        || account.accountCode().value().compareTo(cursor.orElseThrow().accountCode().value()) > 0;
  }

  private static boolean matchesCursor(
      PostingFact postingFact, Optional<dev.erst.fingrind.contract.PostingPageCursor> cursor) {
    if (cursor.isEmpty()) {
      return true;
    }
    dev.erst.fingrind.contract.PostingPageCursor pageCursor = cursor.orElseThrow();
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

  private static dev.erst.fingrind.contract.CurrencyBalance balance(
      CurrencyCode currencyCode, Totals totals) {
    BigDecimal net = totals.debit.subtract(totals.credit);
    BigDecimal absoluteNet = net.abs();
    BalanceSide balanceSide = net.signum() > 0 ? BalanceSide.DEBIT : BalanceSide.CREDIT;
    if (absoluteNet.signum() == 0) {
      balanceSide = BalanceSide.ZERO;
    }
    return new dev.erst.fingrind.contract.CurrencyBalance(
        new Money(currencyCode, totals.debit),
        new Money(currencyCode, totals.credit),
        new Money(currencyCode, absoluteNet),
        balanceSide);
  }

  private static List<dev.erst.fingrind.contract.CurrencyBalance> balancesFor(
      DeclaredAccount account, List<PostingFact> postings) {
    Map<CurrencyCode, Totals> totalsByCurrency = mutableMap();
    postings.stream()
        .flatMap(posting -> posting.journalEntry().lines().stream())
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return balancesFromTotals(totalsByCurrency);
  }

  private static List<dev.erst.fingrind.contract.CurrencyBalance> balancesFromTotals(
      Map<CurrencyCode, Totals> totalsByCurrency) {
    return totalsByCurrency.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().value()))
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static Map<CurrencyCode, Totals> totalsMap(
      List<dev.erst.fingrind.contract.CurrencyBalance> balances) {
    Map<CurrencyCode, Totals> totalsByCurrency = mutableMap();
    for (dev.erst.fingrind.contract.CurrencyBalance balance : balances) {
      totalsByCurrency.put(balance.netAmount().currencyCode(), totalsFrom(balance));
    }
    return totalsByCurrency;
  }

  private static Totals totalsFrom(dev.erst.fingrind.contract.CurrencyBalance balance) {
    Totals totals = new Totals();
    totals.debit = balance.debitTotal().amount();
    totals.credit = balance.creditTotal().amount();
    return totals;
  }

  private static dev.erst.fingrind.contract.CurrencyBalance movementFor(
      DeclaredAccount account, PostingFact postingFact) {
    Map<CurrencyCode, Totals> totalsByCurrency = mutableMap();
    postingFact.journalEntry().lines().stream()
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return totalsByCurrency.entrySet().stream()
        .findFirst()
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .orElseThrow();
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
    return new ConcurrentHashMap<>();
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
