package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Shared low-level helpers for the in-memory executor book fixtures. */
final class InMemoryBookSessionSupport {
  private InMemoryBookSessionSupport() {}

  static boolean matchesAccountFilter(
      CommittedPosting postingFact, Optional<AccountCode> accountCode) {
    return accountCode.isEmpty()
        || postingFact.journalEntry().lines().stream()
            .anyMatch(line -> line.accountCode().equals(accountCode.orElseThrow()));
  }

  static boolean matchesDateRange(
      CommittedPosting postingFact,
      Optional<LocalDate> effectiveDateFrom,
      Optional<LocalDate> effectiveDateTo) {
    LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    return effectiveDateFrom.stream().allMatch(date -> !effectiveDate.isBefore(date))
        && effectiveDateTo.stream().allMatch(date -> !effectiveDate.isAfter(date));
  }

  static boolean matchesAccountCursor(
      RegisteredAccount account, Optional<AccountRegistryCursor> cursor) {
    return cursor.isEmpty()
        || account.accountCode().value().compareTo(cursor.orElseThrow().accountCode().value()) > 0;
  }

  static boolean matchesCursor(
      CommittedPosting postingFact, Optional<PostingHistoryCursor> cursor) {
    if (cursor.isEmpty()) {
      return true;
    }
    PostingHistoryCursor pageCursor = cursor.orElseThrow();
    LocalDate effectiveDate = postingFact.journalEntry().effectiveDate();
    Instant recordedAt = postingFact.provenance().recordedAt();
    String postingId = postingFact.postingId().value();
    return effectiveDate.isBefore(pageCursor.effectiveDate())
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.isBefore(pageCursor.recordedAt()))
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.equals(pageCursor.recordedAt())
            && postingId.compareTo(pageCursor.postingId().value()) < 0);
  }

  static boolean matchesAccountLedgerCursor(
      CommittedPosting posting, Optional<AccountLedgerCursor> cursor) {
    if (cursor.isEmpty()) {
      return true;
    }
    AccountLedgerCursor pageCursor = cursor.orElseThrow();
    LocalDate effectiveDate = posting.journalEntry().effectiveDate();
    Instant recordedAt = posting.provenance().recordedAt();
    String postingId = posting.postingId().value();
    return effectiveDate.isAfter(pageCursor.effectiveDate())
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.isAfter(pageCursor.recordedAt()))
        || (effectiveDate.equals(pageCursor.effectiveDate())
            && recordedAt.equals(pageCursor.recordedAt())
            && postingId.compareTo(pageCursor.postingId().value()) > 0);
  }

  static void accumulate(Map<CurrencyUnit, Totals> totalsByCurrency, JournalLine line) {
    Totals totals =
        totalsByCurrency.computeIfAbsent(
            line.amount().currencyUnit(), ignoredCurrencyCode -> new Totals());
    if (line.side() == JournalLine.EntrySide.DEBIT) {
      totals.debit = Math.addExact(totals.debit, line.amount().minorUnits());
      return;
    }
    totals.credit = Math.addExact(totals.credit, line.amount().minorUnits());
  }

  static CurrencyBalance balance(CurrencyUnit currencyUnit, Totals totals) {
    return CurrencyBalance.ofTotals(
        Money.ofMinorUnits(currencyUnit, totals.debit),
        Money.ofMinorUnits(currencyUnit, totals.credit));
  }

  static List<CurrencyBalance> balancesFor(
      RegisteredAccount account, List<CommittedPosting> postings) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    postings.stream()
        .flatMap(posting -> posting.journalEntry().lines().stream())
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return balancesFromTotals(totalsByCurrency);
  }

  static List<CurrencyBalance> balancesFromTotals(Map<CurrencyUnit, Totals> totalsByCurrency) {
    return totalsByCurrency.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().code()))
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .toList();
  }

  static Map<CurrencyUnit, Totals> totalsMap(List<CurrencyBalance> balances) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    for (CurrencyBalance balance : balances) {
      totalsByCurrency.put(balance.netAmount().currencyUnit(), totalsFrom(balance));
    }
    return totalsByCurrency;
  }

  static Totals totalsFrom(CurrencyBalance balance) {
    Totals totals = new Totals();
    totals.debit = balance.debitTotal().minorUnits();
    totals.credit = balance.creditTotal().minorUnits();
    return totals;
  }

  static CurrencyBalance movementFor(RegisteredAccount account, CommittedPosting postingFact) {
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableMap();
    postingFact.journalEntry().lines().stream()
        .filter(line -> line.accountCode().equals(account.accountCode()))
        .forEach(line -> accumulate(totalsByCurrency, line));
    return totalsByCurrency.entrySet().stream()
        .findFirst()
        .map(entry -> balance(entry.getKey(), entry.getValue()))
        .orElseThrow();
  }

  static PostingHistoryCursor postingHistoryCursor(CommittedPosting posting) {
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  static <T> T withLock(ReentrantLock lock, Supplier<T> action) {
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }

  static void withLock(ReentrantLock lock, Runnable action) {
    lock.lock();
    try {
      action.run();
    } finally {
      lock.unlock();
    }
  }

  static <K, V> Map<K, V> mutableMap() {
    return new ConcurrentHashMap<>();
  }

  /** Mutable debit and credit accumulator for one currency bucket. */
  static final class Totals {
    long debit;
    long credit;
  }
}
