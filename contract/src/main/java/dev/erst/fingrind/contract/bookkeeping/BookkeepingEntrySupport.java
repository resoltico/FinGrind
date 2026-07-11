package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Derives journal entries from validated typed bookkeeping-entry facts. */
final class BookkeepingEntrySupport {
  private BookkeepingEntrySupport() {}

  static JournalEntry pairedEntry(
      LocalDate effectiveDate,
      AccountCode debitAccountCode,
      AccountCode creditAccountCode,
      MonetaryAmount amount) {
    return new JournalEntry(
        effectiveDate,
        List.of(
            new JournalLine(debitAccountCode, JournalLine.EntrySide.DEBIT, amount.toMoney()),
            new JournalLine(creditAccountCode, JournalLine.EntrySide.CREDIT, amount.toMoney())));
  }

  static JournalEntry saleEntry(
      LocalDate effectiveDate,
      AccountCode settlementAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount operatorAmount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting,
      @Nullable AppliedTax appliedTax) {
    if (appliedTax == null) {
      return saleEntry(
          effectiveDate,
          settlementAccountCode,
          revenueAccountCode,
          operatorAmount,
          inventoryRelief,
          resolvedInventoryCosting);
    }
    if (appliedTax.applicationKind() != TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException("Resolved sale tax must use applicationKind OUTPUT_SALE.");
    }
    List<JournalLine> lines = new ArrayList<>();
    lines.add(
        new JournalLine(
            settlementAccountCode,
            JournalLine.EntrySide.DEBIT,
            appliedTax.grossAmount().toMoney()));
    lines.add(
        new JournalLine(
            revenueAccountCode,
            JournalLine.EntrySide.CREDIT,
            appliedTax.taxableAmount().toMoney()));
    if (appliedTax.taxAmount().toMoney().isPositive()) {
      lines.add(
          new JournalLine(
              BookkeepingEntryTaxValidationSupport.requireTaxAccountCode(appliedTax, "sale"),
              JournalLine.EntrySide.CREDIT,
              appliedTax.taxAmount().toMoney()));
    }
    appendInventoryRelief(lines, inventoryRelief, resolvedInventoryCosting);
    return new JournalEntry(effectiveDate, lines);
  }

  static JournalEntry saleEntry(
      LocalDate effectiveDate,
      AccountCode settlementAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount operatorAmount,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting) {
    List<JournalLine> lines = new ArrayList<>();
    lines.add(
        new JournalLine(
            settlementAccountCode, JournalLine.EntrySide.DEBIT, operatorAmount.toMoney()));
    lines.add(
        new JournalLine(
            revenueAccountCode, JournalLine.EntrySide.CREDIT, operatorAmount.toMoney()));
    appendInventoryRelief(lines, inventoryRelief, resolvedInventoryCosting);
    return new JournalEntry(effectiveDate, lines);
  }

  private static void appendInventoryRelief(
      List<JournalLine> lines,
      @Nullable InventoryRelief inventoryRelief,
      @Nullable ResolvedInventoryCosting resolvedInventoryCosting) {
    if (inventoryRelief == null) {
      return;
    }
    ResolvedInventoryCosting requiredResolvedInventoryCosting =
        BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryCosting(
            resolvedInventoryCosting, "sale");
    lines.add(
        new JournalLine(
            inventoryRelief.costOfSalesAccountCode(),
            JournalLine.EntrySide.DEBIT,
            requiredResolvedInventoryCosting.costOfSales()));
    lines.add(
        new JournalLine(
            inventoryRelief.inventoryAccountCode(),
            JournalLine.EntrySide.CREDIT,
            requiredResolvedInventoryCosting.costOfSales()));
  }

  static JournalEntry saleEntry(
      LocalDate effectiveDate,
      AccountCode settlementAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount operatorAmount,
      AppliedTax appliedTax) {
    return saleEntry(
        effectiveDate,
        settlementAccountCode,
        revenueAccountCode,
        operatorAmount,
        null,
        null,
        appliedTax);
  }

  static JournalEntry expenseEntry(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode settlementAccountCode,
      AppliedTax appliedTax) {
    List<JournalLine> lines = new ArrayList<>();
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
      lines.add(
          new JournalLine(
              expenseAccountCode,
              JournalLine.EntrySide.DEBIT,
              appliedTax.taxableAmount().toMoney()));
      if (appliedTax.taxAmount().toMoney().isPositive()) {
        lines.add(
            new JournalLine(
                BookkeepingEntryTaxValidationSupport.requireTaxAccountCode(appliedTax, "expense"),
                JournalLine.EntrySide.DEBIT,
                appliedTax.taxAmount().toMoney()));
      }
      lines.add(
          new JournalLine(
              settlementAccountCode,
              JournalLine.EntrySide.CREDIT,
              appliedTax.grossAmount().toMoney()));
      return new JournalEntry(effectiveDate, lines);
    }
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE) {
      return pairedEntry(
          effectiveDate, expenseAccountCode, settlementAccountCode, appliedTax.grossAmount());
    }
    throw new IllegalArgumentException(
        "Resolved expense tax cannot use applicationKind OUTPUT_SALE.");
  }

  static JournalEntry inventoryCostEntry(
      LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      AccountCode settlementAccountCode,
      AppliedTax appliedTax) {
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
      List<JournalLine> lines = new ArrayList<>();
      lines.add(
          new JournalLine(
              inventoryAccountCode,
              JournalLine.EntrySide.DEBIT,
              appliedTax.taxableAmount().toMoney()));
      if (appliedTax.taxAmount().toMoney().isPositive()) {
        lines.add(
            new JournalLine(
                BookkeepingEntryTaxValidationSupport.requireTaxAccountCode(appliedTax, "inventory"),
                JournalLine.EntrySide.DEBIT,
                appliedTax.taxAmount().toMoney()));
      }
      lines.add(
          new JournalLine(
              settlementAccountCode,
              JournalLine.EntrySide.CREDIT,
              appliedTax.grossAmount().toMoney()));
      return new JournalEntry(effectiveDate, lines);
    }
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE) {
      return pairedEntry(
          effectiveDate, inventoryAccountCode, settlementAccountCode, appliedTax.grossAmount());
    }
    throw new IllegalArgumentException(
        "Resolved inventory tax cannot use applicationKind OUTPUT_SALE.");
  }

  static JournalEntry receiptEntry(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      MonetaryAmount settlementAmount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    MonetaryAmount cashAmount = cashPortion(settlementAmount, settlementAdjunct, "receipt");
    List<JournalLine> lines = new ArrayList<>();
    lines.add(new JournalLine(cashAccountCode, JournalLine.EntrySide.DEBIT, cashAmount.toMoney()));
    if (settlementAdjunct != null) {
      lines.add(
          new JournalLine(
              settlementAdjunct.accountCode(),
              JournalLine.EntrySide.DEBIT,
              settlementAdjunct.amount().toMoney()));
    }
    lines.add(
        new JournalLine(
            receivableAccountCode, JournalLine.EntrySide.CREDIT, settlementAmount.toMoney()));
    return new JournalEntry(effectiveDate, lines);
  }

  static JournalEntry paymentEntry(
      LocalDate effectiveDate,
      AccountCode payableAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount settlementAmount,
      @Nullable SettlementAdjunct settlementAdjunct) {
    MonetaryAmount cashAmount = cashPortion(settlementAmount, settlementAdjunct, "payment");
    List<JournalLine> lines = new ArrayList<>();
    lines.add(
        new JournalLine(
            payableAccountCode, JournalLine.EntrySide.DEBIT, settlementAmount.toMoney()));
    lines.add(new JournalLine(cashAccountCode, JournalLine.EntrySide.CREDIT, cashAmount.toMoney()));
    if (settlementAdjunct != null) {
      lines.add(
          new JournalLine(
              settlementAdjunct.accountCode(),
              JournalLine.EntrySide.CREDIT,
              settlementAdjunct.amount().toMoney()));
    }
    return new JournalEntry(effectiveDate, lines);
  }

  private static MonetaryAmount cashPortion(
      MonetaryAmount settlementAmount,
      @Nullable SettlementAdjunct settlementAdjunct,
      String entryKind) {
    long settlementMinor = settlementAmount.toMoney().minorUnits();
    long adjunctMinor =
        settlementAdjunct == null ? 0L : settlementAdjunct.amount().toMoney().minorUnits();
    long cashMinor = Math.subtractExact(settlementMinor, adjunctMinor);
    if (cashMinor <= 0L) {
      throw new IllegalArgumentException(
          entryKind
              + " must retain one positive cash line after subtracting the settlement adjunct.");
    }
    return MonetaryAmount.of(
        dev.erst.fingrind.core.Money.ofMinorUnits(
            settlementAmount.toMoney().currencyUnit(), cashMinor));
  }
}
