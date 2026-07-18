package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Objects;

/** Assertions for discovery scaffolds owned by retained lifecycle contexts. */
final class MachineContractLifecyclePostEntryTemplateAssertions {
  private MachineContractLifecyclePostEntryTemplateAssertions() {}

  static void assertTemplate(
      BookkeepingEntryKind entryKind,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template) {
    switch (entryKind) {
      case FIXED_ASSET_CAPITALIZATION, FIXED_ASSET_DEPRECIATION, FIXED_ASSET_DISPOSAL ->
          assertFixedAssetTemplate(entryKind, template);
      case FINANCING_BORROWING,
          FINANCING_PRINCIPAL_REPAYMENT,
          FINANCING_INTEREST_ACCRUAL,
          FINANCING_INTEREST_PAYMENT ->
          assertFinancingTemplate(entryKind, template);
      case FOREIGN_CURRENCY_OBLIGATION, REALIZED_FOREIGN_EXCHANGE_SETTLEMENT ->
          assertRealizedForeignExchangeTemplate(entryKind, template);
      default -> throw new IllegalArgumentException("Expected retained lifecycle entry kind.");
    }
  }

  private static void assertFixedAssetTemplate(
      BookkeepingEntryKind entryKind,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template) {
    ContractFixedAssetTemplates.FixedAssetTemplateDescriptor fixedAsset =
        Objects.requireNonNull(template.fixedAsset());
    assertEquals("delivery-van-001", fixedAsset.fixedAssetId());
    switch (entryKind) {
      case FIXED_ASSET_CAPITALIZATION -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("delivery-van", fixedAsset.assetAccountCode());
        assertEquals(
            "delivery-van-accumulated-depreciation",
            fixedAsset.accumulatedDepreciationAccountCode());
        assertEquals("depreciation-expense", fixedAsset.depreciationExpenseAccountCode());
        assertEquals(new MonetaryAmount("EUR", "1200000"), fixedAsset.cost());
        assertNotNull(fixedAsset.depreciationSchedule());
      }
      case FIXED_ASSET_DEPRECIATION -> {
        assertNull(template.cashAccountCode());
        assertNull(fixedAsset.cost());
        assertNull(fixedAsset.depreciationSchedule());
      }
      case FIXED_ASSET_DISPOSAL -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals(new MonetaryAmount("EUR", "800000"), fixedAsset.proceeds());
      }
      default -> throw new IllegalArgumentException("Expected fixed-asset entry kind.");
    }
  }

  private static void assertFinancingTemplate(
      BookkeepingEntryKind entryKind,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template) {
    ContractFinancingTemplates.FinancingTemplateDescriptor financing =
        Objects.requireNonNull(template.financing());
    assertEquals("term-loan-001", financing.financingArrangementId());
    switch (entryKind) {
      case FINANCING_BORROWING -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("term-loan-principal", financing.principalLiabilityAccountCode());
        assertEquals(new MonetaryAmount("EUR", "1000000"), financing.principalAmount());
      }
      case FINANCING_PRINCIPAL_REPAYMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals(new MonetaryAmount("EUR", "100000"), financing.principalAmount());
      }
      case FINANCING_INTEREST_ACCRUAL -> {
        assertNull(template.cashAccountCode());
        assertEquals("interest-expense", financing.interestExpenseAccountCode());
        assertEquals(new MonetaryAmount("EUR", "12000"), financing.interestAmount());
      }
      case FINANCING_INTEREST_PAYMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals(new MonetaryAmount("EUR", "12000"), financing.interestAmount());
      }
      default -> throw new IllegalArgumentException("Expected financing entry kind.");
    }
  }

  private static void assertRealizedForeignExchangeTemplate(
      BookkeepingEntryKind entryKind,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template) {
    ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor context =
        Objects.requireNonNull(template.realizedForeignExchange());
    assertEquals("customer-invoice-usd-001", context.foreignCurrencyObligationId());
    assertNotNull(template.foreignExchange());
    switch (entryKind) {
      case FOREIGN_CURRENCY_OBLIGATION -> {
        assertEquals("accounts-receivable", template.receivableAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertEquals("realized-fx-gain", context.realizedGainAccountCode());
        assertEquals("realized-fx-loss", context.realizedLossAccountCode());
      }
      case REALIZED_FOREIGN_EXCHANGE_SETTLEMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertNull(context.realizedGainAccountCode());
        assertNull(context.realizedLossAccountCode());
      }
      default ->
          throw new IllegalArgumentException("Expected realized foreign-exchange entry kind.");
    }
  }
}
