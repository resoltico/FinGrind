package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal in-memory statement book for targeted read-side edge cases. */
final class StatementBookStore implements BookkeepingReadStore {
  private final List<RegisteredAccount> accounts;
  private final List<CommittedPosting> postings;
  private final List<InventoryValuationMovementRecord> inventoryValuationMovements;

  StatementBookStore(List<RegisteredAccount> accounts, List<CommittedPosting> postings) {
    this(accounts, postings, List.of());
  }

  StatementBookStore(
      List<RegisteredAccount> accounts,
      List<CommittedPosting> postings,
      List<InventoryValuationMovementRecord> inventoryValuationMovements) {
    this.accounts = List.copyOf(accounts);
    this.postings = List.copyOf(postings);
    this.inventoryValuationMovements = List.copyOf(inventoryValuationMovements);
  }

  @Override
  public BookLifecycleInspection inspectBook() {
    return initializedLifecycleInspection(
        BookFormatContract.APPLICATION_ID,
        BookFormatContract.FORMAT_VERSION,
        BookFormatContract.FORMAT_VERSION,
        FIXED_INSTANT);
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return accounts.stream()
        .filter(account -> account.accountCode().equals(accountCode))
        .findFirst();
  }

  @Override
  public Optional<dev.erst.fingrind.executor.spi.StoredRequestPosting> findExistingPosting(
      dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
    return Optional.empty();
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return postings.stream().filter(posting -> posting.postingId().equals(postingId)).findFirst();
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return Optional.empty();
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return accounts;
  }

  @Override
  public List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf) {
    return inventoryValuationMovements.stream()
        .filter(
            movement ->
                effectiveDateAsOf
                    .map(effectiveDate -> !movement.effectiveDate().isAfter(effectiveDate))
                    .orElse(true))
        .toList();
  }

  @Override
  public List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    Map<AccountTotalsKey, AccountTotalsAccumulator> groupedTotals = new ConcurrentHashMap<>();
    for (CommittedPosting posting : postings) {
      if (!effectiveDateRange.contains(posting.journalEntry().effectiveDate())
          || !postingCoverage.includes(posting.postingKind())) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        Optional<RegisteredAccount> account = findAccount(line.accountCode());
        if (account.isEmpty()) {
          continue;
        }
        AccountTotalsAccumulator totals =
            accountTotalsAccumulator(
                groupedTotals, account.orElseThrow(), line.amount().currencyUnit());
        if (line.side() == JournalLine.EntrySide.DEBIT) {
          totals.debitMinor = Math.addExact(totals.debitMinor, line.amount().minorUnits());
        } else {
          totals.creditMinor = Math.addExact(totals.creditMinor, line.amount().minorUnits());
        }
      }
    }
    return groupedTotals.entrySet().stream()
        .sorted(
            java.util.Comparator.comparing(
                    (Map.Entry<AccountTotalsKey, AccountTotalsAccumulator> entry) ->
                        entry.getKey().account().accountCode().value())
                .thenComparing(entry -> entry.getKey().currencyUnit().code()))
        .map(
            entry ->
                new AccountCurrencyTotals(
                    entry.getKey().account(),
                    entry.getKey().currencyUnit(),
                    entry.getValue().debitMinor,
                    entry.getValue().creditMinor))
        .toList();
  }

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    return postings.stream()
        .filter(posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
        .toList();
  }

  @Override
  public Optional<LocalDate> latestPostingEffectiveDate() {
    return postings.stream()
        .map(posting -> posting.journalEntry().effectiveDate())
        .max(LocalDate::compareTo);
  }

  @Override
  public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
      dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
    throw unsupported();
  }

  @Override
  public dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage listPostings(
      dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery query) {
    throw unsupported();
  }

  @Override
  public Optional<dev.erst.fingrind.executor.bookkeeping.AccountBalanceView> accountBalance(
      dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria query) {
    throw unsupported();
  }

  @Override
  public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
      dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria query) {
    throw unsupported();
  }

  @Override
  public dev.erst.fingrind.executor.bookkeeping.AccountLedgerView accountLedger(
      dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria query,
      RegisteredAccount account) {
    throw unsupported();
  }

  @Override
  public dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView periodSummary(
      dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria query) {
    throw unsupported();
  }

  private static AccountTotalsAccumulator accountTotalsAccumulator(
      Map<AccountTotalsKey, AccountTotalsAccumulator> groupedTotals,
      RegisteredAccount account,
      CurrencyUnit currencyUnit) {
    AccountTotalsKey key = new AccountTotalsKey(account, currencyUnit);
    AccountTotalsAccumulator existing = groupedTotals.get(key);
    if (existing != null) {
      return existing;
    }
    AccountTotalsAccumulator created = new AccountTotalsAccumulator();
    groupedTotals.put(key, created);
    return created;
  }

  private static AssertionError unsupported() {
    return new AssertionError("This statement test double does not support that seam.");
  }

  /** One deterministic grouping key for test-only per-account per-currency totals. */
  private record AccountTotalsKey(RegisteredAccount account, CurrencyUnit currencyUnit) {}

  /** Mutable debit and credit totals for one grouped test account/currency bucket. */
  private static final class AccountTotalsAccumulator {
    private long debitMinor;
    private long creditMinor;
  }
}
