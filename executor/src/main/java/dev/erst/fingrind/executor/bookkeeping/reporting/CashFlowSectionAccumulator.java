package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.CashFlowRowView;
import dev.erst.fingrind.executor.bookkeeping.CashFlowSectionView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable accumulator that groups classified cash-flow row movements into section views. */
final class CashFlowSectionAccumulator {
  private static final List<CashFlowSectionKind> SECTION_ORDER =
      List.of(
          CashFlowSectionKind.OPERATING,
          CashFlowSectionKind.INVESTING,
          CashFlowSectionKind.FINANCING);
  private static final Comparator<RowKey> ROW_KEY_ORDER =
      CashFlowSectionAccumulator::compareRowKeys;

  private final Map<RowKey, RowBucket> rowsByKey = new ConcurrentHashMap<>();

  void add(CashFlowPostingMovementClassifier.CashFlowRowMovement movement) {
    RowKey key =
        new RowKey(
            movement.sectionKind(),
            movement.account().accountCode(),
            movement.movement().netAmount().currencyUnit());
    rowsByKey.merge(key, RowBucket.of(movement.account(), movement.movement()), RowBucket::plus);
  }

  List<CashFlowSectionView> sections() {
    return SECTION_ORDER.stream()
        .map(
            sectionKind ->
                ReportingRowViewFactory.toCashFlowSection(
                    sectionKind,
                    rowsByKey.entrySet().stream()
                        .filter(entry -> entry.getKey().sectionKind() == sectionKind)
                        .sorted(Map.Entry.comparingByKey(ROW_KEY_ORDER))
                        .map(entry -> entry.getValue().toRow())
                        .toList()))
        .toList();
  }

  static record RowKey(
      CashFlowSectionKind sectionKind, AccountCode accountCode, CurrencyUnit currencyUnit) {
    RowKey {
      Objects.requireNonNull(sectionKind, "sectionKind");
      Objects.requireNonNull(accountCode, "accountCode");
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }
  }

  static int compareRowKeys(RowKey left, RowKey right) {
    int account = left.accountCode().value().compareTo(right.accountCode().value());
    if (account != 0) {
      return account;
    }
    return left.currencyUnit().code().compareTo(right.currencyUnit().code());
  }

  private record RowBucket(
      RegisteredAccount account,
      CurrencyUnit currencyUnit,
      long debitTotalMinor,
      long creditTotalMinor) {
    private RowBucket {
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }

    private static RowBucket of(RegisteredAccount account, CurrencyBalance movement) {
      return new RowBucket(
          account,
          movement.netAmount().currencyUnit(),
          movement.debitTotal().minorUnits(),
          movement.creditTotal().minorUnits());
    }

    private RowBucket plus(RowBucket other) {
      return new RowBucket(
          account,
          currencyUnit,
          Math.addExact(debitTotalMinor, other.debitTotalMinor()),
          Math.addExact(creditTotalMinor, other.creditTotalMinor()));
    }

    private CashFlowRowView toRow() {
      return ReportingRowViewFactory.cashFlowRow(
          account, BalanceMath.currencyBalance(currencyUnit, debitTotalMinor, creditTotalMinor));
    }
  }
}
