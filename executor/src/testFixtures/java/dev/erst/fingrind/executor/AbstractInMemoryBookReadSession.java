package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerEntryView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared in-memory read-model and reporting fixture state for executor tests. */
abstract class AbstractInMemoryBookReadSession extends AbstractInMemoryReportingPeriodCloseSession
    implements BookkeepingReadStore {
  @Override
  public PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> matchingPostings =
              postingsByPostingId.values().stream()
                  .filter(
                      posting ->
                          InMemoryBookSessionSupport.matchesAccountFilter(
                              posting, query.accountCode()))
                  .filter(
                      posting ->
                          InMemoryBookSessionSupport.matchesDateRange(
                              posting,
                              query.effectiveDateRange().effectiveDateFrom(),
                              query.effectiveDateRange().effectiveDateTo()))
                  .filter(
                      posting -> InMemoryBookSessionSupport.matchesCursor(posting, query.cursor()))
                  .sorted(
                      Comparator.comparing(
                              (dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) ->
                                  posting.journalEntry().effectiveDate())
                          .reversed()
                          .thenComparing(
                              posting -> posting.provenance().recordedAt(),
                              Comparator.reverseOrder())
                          .thenComparing(
                              posting -> posting.postingId().value(), Comparator.reverseOrder()))
                  .toList();
          int end = Math.min(query.limit(), matchingPostings.size());
          List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> pageItems =
              matchingPostings.subList(0, end);
          return new PostingHistoryPage(
              pageItems,
              query.limit(),
              end < matchingPostings.size()
                  ? Optional.of(
                      InMemoryBookSessionSupport.postingHistoryCursor(pageItems.getLast()))
                  : Optional.empty());
        });
  }

  @Override
  public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          RegisteredAccount account = accountsByCode.get(query.accountCode());
          if (account == null) {
            return Optional.empty();
          }
          Map<CurrencyUnit, InMemoryBookSessionSupport.Totals> totalsByCurrency =
              InMemoryBookSessionSupport.mutableMap();
          postingsByPostingId.values().stream()
              .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
              .filter(
                  posting ->
                      InMemoryBookSessionSupport.matchesDateRange(
                          posting,
                          query.effectiveDateRange().effectiveDateFrom(),
                          query.effectiveDateRange().effectiveDateTo()))
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .filter(line -> line.accountCode().equals(query.accountCode()))
              .forEach(line -> InMemoryBookSessionSupport.accumulate(totalsByCurrency, line));
          List<CurrencyBalance> balances =
              totalsByCurrency.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().code()))
                  .map(
                      entry -> InMemoryBookSessionSupport.balance(entry.getKey(), entry.getValue()))
                  .toList();
          return Optional.of(
              new AccountBalanceView(
                  account, query.effectiveDateRange(), query.postingCoverage(), balances));
        });
  }

  @Override
  public List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          Map<AccountCode, Map<CurrencyUnit, InMemoryBookSessionSupport.Totals>> totalsByAccount =
              InMemoryBookSessionSupport.mutableMap();
          postingsByPostingId.values().stream()
              .filter(posting -> postingCoverage.includes(posting.postingKind()))
              .filter(
                  posting ->
                      InMemoryBookSessionSupport.matchesDateRange(
                          posting,
                          effectiveDateRange.effectiveDateFrom(),
                          effectiveDateRange.effectiveDateTo()))
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .forEach(
                  line ->
                      InMemoryBookSessionSupport.accumulate(
                          totalsByAccount.computeIfAbsent(
                              line.accountCode(),
                              ignored -> InMemoryBookSessionSupport.mutableMap()),
                          line));
          return totalsByAccount.entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.comparing(AccountCode::value)))
              .flatMap(
                  accountEntry ->
                      accountEntry.getValue().entrySet().stream()
                          .sorted(
                              Map.Entry.comparingByKey(Comparator.comparing(CurrencyUnit::code)))
                          .map(
                              currencyEntry ->
                                  new AccountCurrencyTotals(
                                      Objects.requireNonNull(
                                          accountsByCode.get(accountEntry.getKey()), "account"),
                                      currencyEntry.getKey(),
                                      currencyEntry.getValue().debit,
                                      currencyEntry.getValue().credit)))
              .toList();
        });
  }

  @Override
  public Optional<LocalDate> latestPostingEffectiveDate() {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () ->
            postingsByPostingId.values().stream()
                .map(posting -> posting.journalEntry().effectiveDate())
                .max(LocalDate::compareTo));
  }

  @Override
  public List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          Map<AccountCode, Long> nextSequenceByAccount = InMemoryBookSessionSupport.mutableMap();
          List<InventoryValuationMovementRecord> rows = new ArrayList<>();
          postingsByPostingId.values().stream()
              .filter(
                  posting ->
                      effectiveDateAsOf.stream()
                          .allMatch(date -> !posting.journalEntry().effectiveDate().isAfter(date)))
              .sorted(
                  Comparator.comparing(
                          (dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) ->
                              posting.journalEntry().effectiveDate())
                      .thenComparing(posting -> posting.provenance().recordedAt())
                      .thenComparing(posting -> posting.postingId().value()))
              .forEach(
                  posting ->
                      inventoryMovementsByPostingId
                          .getOrDefault(posting.postingId(), List.of())
                          .forEach(
                              movement ->
                                  rows.add(
                                      valuationMovement(
                                          posting.postingId(),
                                          movement,
                                          nextSequenceByAccount.merge(
                                              movement.inventoryAccount(), 1L, Math::addExact)))));
          return List.copyOf(rows);
        });
  }

  private static InventoryValuationMovementRecord valuationMovement(
      dev.erst.fingrind.core.PostingId postingId,
      InventoryMovementRecord movement,
      long accountSequence) {
    return new InventoryValuationMovementRecord(
        movement.inventoryAccount(),
        movement.effectiveDate(),
        accountSequence,
        movement.kind(),
        movement.quantityDelta(),
        movement.costDeltaMinor(),
        postingId);
  }

  @Override
  public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          Optional<LocalDate> resolvedEffectiveDateAsOf =
              query.effectiveDateAsOf().isPresent()
                  ? query.effectiveDateAsOf()
                  : postingsByPostingId.values().stream()
                      .map(posting -> posting.journalEntry().effectiveDate())
                      .max(LocalDate::compareTo);
          return new TrialBalanceView(
              bookIdentity,
              query.effectiveDateAsOf(),
              resolvedEffectiveDateAsOf,
              EffectiveDateRange.of(null, null),
              query.postingCoverage(),
              accountsByCode.values().stream()
                  .sorted(Comparator.comparing(account -> account.accountCode().value()))
                  .flatMap(
                      account ->
                          InMemoryBookSessionSupport.balancesFor(
                                  account,
                                  postingsByPostingId.values().stream()
                                      .filter(
                                          posting ->
                                              query
                                                  .postingCoverage()
                                                  .includes(posting.postingKind()))
                                      .filter(
                                          posting ->
                                              query.effectiveDateAsOf().stream()
                                                  .allMatch(
                                                      date ->
                                                          !posting
                                                              .journalEntry()
                                                              .effectiveDate()
                                                              .isAfter(date)))
                                      .toList())
                              .stream()
                              .map(balance -> new TrialBalanceRowView(account, balance)))
                  .toList(),
              List.of());
        });
  }

  @Override
  public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          EffectiveDateRange range = query.effectiveDateRange();
          List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> orderedPostings =
              postingsByPostingId.values().stream()
                  .sorted(
                      Comparator.comparing(
                              (dev.erst.fingrind.executor.bookkeeping.CommittedPosting posting) ->
                                  posting.journalEntry().effectiveDate())
                          .thenComparing(posting -> posting.provenance().recordedAt())
                          .thenComparing(posting -> posting.postingId().value()))
                  .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
                  .filter(
                      posting ->
                          posting.journalEntry().lines().stream()
                              .anyMatch(line -> line.accountCode().equals(account.accountCode())))
                  .toList();
          List<CurrencyBalance> openingBalances =
              InMemoryBookSessionSupport.balancesFor(
                  account,
                  orderedPostings.stream()
                      .filter(
                          posting ->
                              query.effectiveDateRange().effectiveDateFrom().stream()
                                  .allMatch(
                                      lowerBound ->
                                          posting
                                              .journalEntry()
                                              .effectiveDate()
                                              .isBefore(lowerBound)))
                      .toList());
          Map<CurrencyUnit, InMemoryBookSessionSupport.Totals> runningTotals =
              InMemoryBookSessionSupport.totalsMap(openingBalances);
          List<AccountLedgerEntryView> entries = new java.util.ArrayList<>();
          orderedPostings.stream()
              .filter(posting -> range.contains(posting.journalEntry().effectiveDate()))
              .forEach(
                  posting -> {
                    CurrencyBalance movement =
                        InMemoryBookSessionSupport.movementFor(account, posting);
                    InMemoryBookSessionSupport.Totals totals =
                        runningTotals.computeIfAbsent(
                            movement.netAmount().currencyUnit(),
                            ignored -> new InMemoryBookSessionSupport.Totals());
                    totals.debit = Math.addExact(totals.debit, movement.debitTotal().minorUnits());
                    totals.credit =
                        Math.addExact(totals.credit, movement.creditTotal().minorUnits());
                    CurrencyBalance runningBalance =
                        InMemoryBookSessionSupport.balance(
                            movement.netAmount().currencyUnit(), totals);
                    entries.add(
                        new AccountLedgerEntryView(
                            posting,
                            movement,
                            runningBalance.netAmount(),
                            runningBalance.balanceSide()));
                  });
          List<CurrencyBalance> closingBalances =
              InMemoryBookSessionSupport.balancesFromTotals(runningTotals);
          return new AccountLedgerView(
              account, range, query.postingCoverage(), openingBalances, entries, closingBalances);
        });
  }

  @Override
  public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings =
              postingsByPostingId.values().stream()
                  .filter(posting -> query.postingCoverage().includes(posting.postingKind()))
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
          Map<CurrencyUnit, InMemoryBookSessionSupport.Totals> currencyTotals =
              InMemoryBookSessionSupport.mutableMap();
          Map<AccountCode, Map<CurrencyUnit, InMemoryBookSessionSupport.Totals>> accountTotals =
              InMemoryBookSessionSupport.mutableMap();
          postings.stream()
              .flatMap(posting -> posting.journalEntry().lines().stream())
              .forEach(
                  line -> {
                    InMemoryBookSessionSupport.accumulate(currencyTotals, line);
                    accountTotals
                        .computeIfAbsent(
                            line.accountCode(), ignored -> InMemoryBookSessionSupport.mutableMap())
                        .computeIfAbsent(
                            line.amount().currencyUnit(),
                            ignored -> new InMemoryBookSessionSupport.Totals());
                    InMemoryBookSessionSupport.accumulate(
                        accountTotals.get(line.accountCode()), line);
                  });
          List<PeriodCurrencySummaryView> currencySummaries =
              currencyTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().code()))
                  .map(
                      entry ->
                          new PeriodCurrencySummaryView(
                              InMemoryBookSessionSupport.balance(entry.getKey(), entry.getValue())))
                  .toList();
          List<PeriodAccountActivityView> accountActivity =
              accountTotals.entrySet().stream()
                  .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                  .flatMap(
                      entry -> {
                        RegisteredAccount account =
                            Objects.requireNonNull(accountsByCode.get(entry.getKey()), "account");
                        return entry.getValue().entrySet().stream()
                            .sorted(Comparator.comparing(currency -> currency.getKey().code()))
                            .map(
                                currencyEntry ->
                                    new PeriodAccountActivityView(
                                        account,
                                        InMemoryBookSessionSupport.balance(
                                            currencyEntry.getKey(), currencyEntry.getValue())));
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
          return new PeriodSummaryView(
              query.effectiveDateFrom(),
              query.effectiveDateTo(),
              query.postingCoverage(),
              postings.size(),
              postingLineCount,
              Math.toIntExact(accountsTouched),
              currencySummaries,
              accountActivity);
        });
  }
}
