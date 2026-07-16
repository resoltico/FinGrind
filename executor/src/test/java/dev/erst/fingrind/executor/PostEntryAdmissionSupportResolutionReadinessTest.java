package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryDisposal;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Locks executor readiness to every inventory resolution prerequisite. */
class PostEntryAdmissionSupportResolutionReadinessTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final TaxSelection INPUT_TAX =
      new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-input"));

  @Test
  void canResolveResolvedJournal_requiresEveryInventoryOwnedResolution() {
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(purchaseSettled(null, null, null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            purchaseSettled(acquisition(), null, null)));
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            purchaseSettled(acquisition(), INPUT_TAX, null)));
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            purchaseOnCredit(acquisition(), INPUT_TAX, null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            purchaseOnCredit(acquisition(), INPUT_TAX, appliedTax())));
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            capitalizationSettled(INPUT_TAX, null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(capitalizationSettled(null, null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            capitalizationSettled(INPUT_TAX, appliedTax())));
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            capitalizationOnCredit(INPUT_TAX, null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            capitalizationOnCredit(INPUT_TAX, appliedTax())));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(capitalizationOnCredit(null, null)));
    assertFalse(PostEntryAdmissionSupport.canResolveResolvedJournal(shrinkage(null)));
    assertTrue(PostEntryAdmissionSupport.canResolveResolvedJournal(shrinkage(disposal())));
    assertFalse(PostEntryAdmissionSupport.canResolveResolvedJournal(countIncrease(null)));
    assertTrue(PostEntryAdmissionSupport.canResolveResolvedJournal(countIncrease(acquisition())));
  }

  @Test
  void canResolveResolvedJournal_requiresAccrualCutoffApplicationResolution() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("cutoff-2026-04");
    ResolvedAccrualCutoffApplication recognitionResolution =
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.PREPAYMENT,
            AccrualCutoffApplicationKind.RECOGNITION,
            new AccountCode("expense"),
            new AccountCode("prepaid-expense"));
    ResolvedAccrualCutoffApplication settlementResolution =
        new ResolvedAccrualCutoffApplication(
            AccrualCutoffKind.ACCRUED_EXPENSE,
            AccrualCutoffApplicationKind.SETTLEMENT,
            new AccountCode("accrued-expense"),
            new AccountCode("cash"));

    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                EFFECTIVE_DATE, cutoffId, new MonetaryAmount("EUR", "1000"), null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
                EFFECTIVE_DATE,
                cutoffId,
                new MonetaryAmount("EUR", "1000"),
                recognitionResolution)));
    assertFalse(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                EFFECTIVE_DATE,
                cutoffId,
                new AccountCode("cash"),
                new MonetaryAmount("EUR", "1000"),
                null)));
    assertTrue(
        PostEntryAdmissionSupport.canResolveResolvedJournal(
            new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
                EFFECTIVE_DATE,
                cutoffId,
                new AccountCode("cash"),
                new MonetaryAmount("EUR", "1000"),
                settlementResolution)));
  }

  private static BookkeepingEntry.PurchaseSettled purchaseSettled(
      @Nullable ResolvedInventoryAcquisition acquisition,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    return new BookkeepingEntry.PurchaseSettled(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("cash"),
        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
        new MonetaryAmount("EUR", "1000"),
        acquisition,
        null,
        taxSelection,
        appliedTax);
  }

  private static BookkeepingEntry.PurchaseOnCredit purchaseOnCredit(
      @Nullable ResolvedInventoryAcquisition acquisition,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax) {
    return new BookkeepingEntry.PurchaseOnCredit(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("payable"),
        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
        new MonetaryAmount("EUR", "1000"),
        acquisition,
        null,
        taxSelection,
        appliedTax);
  }

  private static InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled
      capitalizationSettled(@Nullable TaxSelection taxSelection, @Nullable AppliedTax appliedTax) {
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("cash"),
        new MonetaryAmount("EUR", "1000"),
        null,
        taxSelection,
        appliedTax);
  }

  private static InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit
      capitalizationOnCredit(@Nullable TaxSelection taxSelection, @Nullable AppliedTax appliedTax) {
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("payable"),
        new MonetaryAmount("EUR", "1000"),
        null,
        taxSelection,
        appliedTax);
  }

  private static InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage(
      @Nullable ResolvedInventoryDisposal disposal) {
    return new InventoryBookkeepingEntryVariants.InventoryShrinkage(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("shrinkage-loss"),
        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
        disposal);
  }

  private static InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease(
      @Nullable ResolvedInventoryAcquisition acquisition) {
    return new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
        EFFECTIVE_DATE,
        new AccountCode("inventory"),
        new AccountCode("count-gain"),
        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
        new MonetaryAmount("EUR", "1000"),
        acquisition);
  }

  private static ResolvedInventoryAcquisition acquisition() {
    return new ResolvedInventoryAcquisition(
        Quantity.ofScaledUnits(0, 1),
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "1000"));
  }

  private static ResolvedInventoryDisposal disposal() {
    return new ResolvedInventoryDisposal(
        Money.parse("EUR", "10.00"), Quantity.ofScaledUnits(0, 1), Money.parse("EUR", "10.00"));
  }

  private static AppliedTax appliedTax() {
    return new AppliedTax(
        INPUT_TAX.taxRegistrationId(),
        INPUT_TAX.taxCode(),
        new TaxCodeName("VAT Input"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode("tax-recoverable"));
  }
}
