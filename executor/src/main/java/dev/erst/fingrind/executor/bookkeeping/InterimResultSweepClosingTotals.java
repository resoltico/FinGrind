package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Ordered close totals keyed first by currency and then by account code. */
final class InterimResultSweepClosingTotals {
  private InterimResultSweepClosingTotals() {}

  /** Creates one mutable close-total accumulator keyed by currency. */
  static ByCurrency byCurrency() {
    return new ByCurrency();
  }

  /** Ordered close totals keyed by one reporting currency. */
  static final class ByCurrency {
    private final Map<CurrencyUnit, ByAccount> totalsByCurrency = new ConcurrentHashMap<>();

    void record(JournalLine line) {
      accountTotals(line.amount().currencyUnit())
          .record(line.accountCode(), line.side(), line.amount().minorUnits());
    }

    java.util.List<Map.Entry<CurrencyUnit, Map<AccountCode, Totals>>> orderedEntries() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Comparator.comparing(entry -> entry.getKey().code()))
          .map(entry -> Map.entry(entry.getKey(), entry.getValue().snapshotTotals()))
          .toList();
    }

    private ByAccount accountTotals(CurrencyUnit currencyUnit) {
      return totalsByCurrency.computeIfAbsent(currencyUnit, ignored -> new ByAccount());
    }
  }

  /** Ordered debit/credit close totals keyed by account code. */
  static final class ByAccount {
    private final Map<AccountCode, Totals> totalsByAccount = new ConcurrentHashMap<>();

    void record(AccountCode accountCode, JournalLine.EntrySide side, long amountMinor) {
      totalsByAccount.compute(
          accountCode,
          (ignored, existing) ->
              (existing == null ? Totals.ZERO : existing).plus(side, amountMinor));
    }

    Map<AccountCode, Totals> snapshotTotals() {
      return Map.copyOf(totalsByAccount);
    }
  }

  /** Exact debit/credit totals for one account/currency close bucket. */
  record Totals(long debit, long credit) {
    private static final Totals ZERO = new Totals(0L, 0L);

    private Totals plus(JournalLine.EntrySide side, long amountMinor) {
      return switch (Objects.requireNonNull(side, "side")) {
        case DEBIT -> new Totals(Math.addExact(debit, amountMinor), credit);
        case CREDIT -> new Totals(debit, Math.addExact(credit, amountMinor));
      };
    }
  }
}
