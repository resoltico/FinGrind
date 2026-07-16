package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared fixtures and assertions for posting-template discovery coverage tests. */
final class MachineContractPostEntryVariantTemplateTestSupport {
  private static final List<ContractTemplates.JournalLineTemplateDescriptor> JOURNAL_LINES =
      List.of(
          new ContractTemplates.JournalLineTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.JournalLineTemplateDescriptor(
              "service-revenue", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
  private static final List<ContractTemplates.OpeningBalanceTemplateDescriptor> OPENING_BALANCES =
      List.of(
          new ContractTemplates.OpeningBalanceTemplateDescriptor(
              "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
          new ContractTemplates.OpeningBalanceTemplateDescriptor(
              "owner-capital", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000")));
  private static final Map<BookkeepingEntryKind, TemplateFamily> TEMPLATE_FAMILIES =
      Map.ofEntries(
          Map.entry(BookkeepingEntryKind.DIRECT_JOURNAL, TemplateFamily.DIRECT_JOURNAL),
          Map.entry(BookkeepingEntryKind.SALE_SETTLED, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.PURCHASE_SETTLED, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.PURCHASE_ON_CREDIT, TemplateFamily.STANDARD),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED, TemplateFamily.INVENTORY),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT, TemplateFamily.INVENTORY),
          Map.entry(BookkeepingEntryKind.INVENTORY_WRITE_DOWN, TemplateFamily.INVENTORY),
          Map.entry(BookkeepingEntryKind.INVENTORY_SHRINKAGE, TemplateFamily.INVENTORY),
          Map.entry(BookkeepingEntryKind.INVENTORY_COUNT_INCREASE, TemplateFamily.INVENTORY),
          Map.entry(BookkeepingEntryKind.EXPENSE_SETTLED, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.EXPENSE_ON_CREDIT, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.RECEIPT, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.PAYMENT, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.OWNER_CONTRIBUTION, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.OWNER_WITHDRAWAL, TemplateFamily.STANDARD),
          Map.entry(BookkeepingEntryKind.PREPAYMENT, TemplateFamily.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.DEFERRED_REVENUE, TemplateFamily.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE, TemplateFamily.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION, TemplateFamily.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT, TemplateFamily.ACCRUAL_CUTOFF),
          Map.entry(BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION, TemplateFamily.FIXED_ASSET),
          Map.entry(BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION, TemplateFamily.FIXED_ASSET),
          Map.entry(BookkeepingEntryKind.FIXED_ASSET_DISPOSAL, TemplateFamily.FIXED_ASSET),
          Map.entry(BookkeepingEntryKind.FINANCING_BORROWING, TemplateFamily.FINANCING),
          Map.entry(BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT, TemplateFamily.FINANCING),
          Map.entry(BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL, TemplateFamily.FINANCING),
          Map.entry(BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT, TemplateFamily.FINANCING),
          Map.entry(
              BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
              TemplateFamily.REALIZED_FOREIGN_EXCHANGE),
          Map.entry(
              BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
              TemplateFamily.REALIZED_FOREIGN_EXCHANGE),
          Map.entry(BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL, TemplateFamily.LATVIAN_PAYROLL),
          Map.entry(
              BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
              TemplateFamily.LATVIAN_PAYROLL_SETTLEMENT),
          Map.entry(
              BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
              TemplateFamily.LATVIAN_PAYROLL_SETTLEMENT),
          Map.entry(BookkeepingEntryKind.OPENING_POSITION, TemplateFamily.OPENING_POSITION),
          Map.entry(BookkeepingEntryKind.REVERSAL, TemplateFamily.REVERSAL));

  private MachineContractPostEntryVariantTemplateTestSupport() {}

  static void assertCanonicalTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals(entryKind, template.entryKind());
    assertEquals(
        ProtocolCatalog.domain()
            .requestSurface()
            .bookkeepingEntryKind(entryKind)
            .sourceDocumentTypes()
            .scaffoldValue(),
        template.evidence().sourceDocuments().getFirst().sourceDocumentType());
    assertNotNull(template.provenance());
    switch (templateFamily(entryKind)) {
      case DIRECT_JOURNAL -> assertDirectJournalTemplate(template);
      case STANDARD -> assertStandardTemplate(entryKind, template);
      case INVENTORY -> assertInventoryTemplate(entryKind, template);
      case ACCRUAL_CUTOFF -> assertAccrualCutoffTemplate(entryKind, template);
      case FIXED_ASSET, FINANCING, REALIZED_FOREIGN_EXCHANGE ->
          MachineContractLifecyclePostEntryTemplateAssertions.assertTemplate(entryKind, template);
      case LATVIAN_PAYROLL -> assertLatvianPayrollTemplate(template);
      case LATVIAN_PAYROLL_SETTLEMENT -> assertLatvianPayrollSettlementTemplate(template);
      case OPENING_POSITION -> assertOpeningPositionTemplate(template);
      case REVERSAL -> assertReversalTemplate(template);
    }
  }

  private static void assertDirectJournalTemplate(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNotNull(template.lines());
    assertNull(template.amount());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertStandardTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    switch (entryKind) {
      case SALE_SETTLED -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertNull(template.inventoryRelief());
        assertAmountTemplateShape(template);
      }
      case SALE_ON_CREDIT -> {
        assertEquals("accounts-receivable", template.receivableAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertNull(template.inventoryRelief());
        assertAmountTemplateShape(template);
      }
      case PURCHASE_SETTLED -> {
        assertEquals("inventory", template.inventoryAccountCode());
        assertEquals("cash", template.cashAccountCode());
        assertNull(template.inventoryRelief());
        assertQuantityUnitCostTemplateShape(template);
      }
      case PURCHASE_ON_CREDIT -> {
        assertEquals("inventory", template.inventoryAccountCode());
        assertEquals("accounts-payable", template.payableAccountCode());
        assertNull(template.inventoryRelief());
        assertQuantityUnitCostTemplateShape(template);
      }
      case EXPENSE_SETTLED, EXPENSE_ON_CREDIT -> {
        assertEquals(
            entryKind == BookkeepingEntryKind.EXPENSE_SETTLED ? "cash" : "accounts-payable",
            entryKind == BookkeepingEntryKind.EXPENSE_SETTLED
                ? template.cashAccountCode()
                : template.payableAccountCode());
        assertEquals("operating-expense", template.expenseAccountCode());
        assertAmountTemplateShape(template);
      }
      case RECEIPT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("accounts-receivable", template.receivableAccountCode());
        assertAmountTemplateShape(template);
      }
      case PAYMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("accounts-payable", template.payableAccountCode());
        assertAmountTemplateShape(template);
      }
      case OWNER_CONTRIBUTION, OWNER_WITHDRAWAL -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals(ownerEquityAccount(entryKind), template.equityAccountCode());
        assertAmountTemplateShape(template);
      }
      default -> throw new IllegalArgumentException("Expected standard entry kind.");
    }
  }

  private static void assertInventoryTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals("inventory", template.inventoryAccountCode());
    switch (entryKind) {
      case INVENTORY_CAPITALIZATION_SETTLED -> {
        assertEquals("cash", template.cashAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_CAPITALIZATION_ON_CREDIT -> {
        assertEquals("accounts-payable", template.payableAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_WRITE_DOWN -> {
        assertEquals("inventory-write-down-loss", template.writeDownLossAccountCode());
        assertAmountTemplateShape(template);
      }
      case INVENTORY_SHRINKAGE -> {
        assertEquals("inventory-shrinkage-loss", template.shrinkageLossAccountCode());
        assertNull(template.amount());
        assertEquals("5", template.quantity());
        assertNull(template.unitCost());
      }
      case INVENTORY_COUNT_INCREASE -> {
        assertEquals("inventory-count-gain", template.countGainAccountCode());
        assertQuantityUnitCostTemplateShape(template);
      }
      default -> throw new IllegalArgumentException("Expected inventory entry kind.");
    }
  }

  private static void assertAccrualCutoffTemplate(
      BookkeepingEntryKind entryKind, ContractTemplates.PostingRequestTemplateDescriptor template) {
    switch (entryKind) {
      case PREPAYMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("operating-expense", template.expenseAccountCode());
        assertEquals("prepaid-expense", template.prepaymentAssetAccountCode());
        assertRecognitionInterval(template);
        assertAmountTemplateShape(template);
      }
      case DEFERRED_REVENUE -> {
        assertEquals("cash", template.cashAccountCode());
        assertEquals("service-revenue", template.revenueAccountCode());
        assertEquals("deferred-revenue", template.deferredRevenueAccountCode());
        assertRecognitionInterval(template);
        assertAmountTemplateShape(template);
      }
      case ACCRUED_EXPENSE -> {
        assertEquals("operating-expense", template.expenseAccountCode());
        assertEquals("accrued-expense", template.accruedExpenseLiabilityAccountCode());
        assertNull(template.recognitionInterval());
        assertAmountTemplateShape(template);
      }
      case ACCRUAL_CUTOFF_RECOGNITION -> {
        assertNull(template.recognitionInterval());
        assertAmountTemplateShape(template);
      }
      case ACCRUED_EXPENSE_SETTLEMENT -> {
        assertEquals("cash", template.cashAccountCode());
        assertNull(template.recognitionInterval());
        assertAmountTemplateShape(template);
      }
      default -> throw new IllegalArgumentException("Expected accrual cut-off entry kind.");
    }
  }

  private static void assertOpeningPositionTemplate(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNull(template.amount());
    assertNull(template.lines());
    assertEquals(2, Objects.requireNonNull(template.openingBalances()).size());
  }

  private static void assertLatvianPayrollTemplate(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor payroll =
        Objects.requireNonNull(template.latvianMonthlyPayroll());
    assertEquals("payroll-lv-2026-01-employee-001", payroll.payrollRunId());
    assertEquals("employee-001", payroll.employeeReference());
    assertEquals("2026-01", payroll.payrollMonth());
    assertEquals("wage-expense", payroll.wageExpenseAccountCode());
    assertEquals("employer-social-expense", payroll.employerSocialContributionExpenseAccountCode());
    assertEquals("net-wages-payable", payroll.netWagesPayableAccountCode());
    assertEquals("employee-social-payable", payroll.employeeSocialContributionPayableAccountCode());
    assertEquals("employer-social-payable", payroll.employerSocialContributionPayableAccountCode());
    assertEquals("personal-income-tax-payable", payroll.personalIncomeTaxPayableAccountCode());
    assertEquals(new MonetaryAmount("EUR", "200000"), payroll.grossWages());
    assertNull(template.amount());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertLatvianPayrollSettlementTemplate(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals("cash", template.cashAccountCode());
    assertEquals(
        "payroll-lv-2026-01-employee-001",
        Objects.requireNonNull(template.latvianPayrollSettlement()).payrollRunId());
    assertNull(template.amount());
    assertNull(template.quantity());
    assertNull(template.unitCost());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertReversalTemplate(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNull(template.lines());
    assertNull(template.amount());
    assertEquals(
        "018f0000-0000-7000-8000-000000000001",
        Objects.requireNonNull(template.reversal()).priorPostingId());
  }

  static Map<OperationId, BookkeepingEntryKind> scaffoldOperationEntryKinds() {
    return Map.ofEntries(
        Map.entry(OperationId.POST_ENTRY, BookkeepingEntryKind.DIRECT_JOURNAL),
        Map.entry(OperationId.PREFLIGHT_ENTRY, BookkeepingEntryKind.SALE_SETTLED),
        Map.entry(OperationId.RECORD_SALE_SETTLED, BookkeepingEntryKind.SALE_SETTLED),
        Map.entry(OperationId.RECORD_SALE_ON_CREDIT, BookkeepingEntryKind.SALE_ON_CREDIT),
        Map.entry(OperationId.RECORD_PURCHASE_SETTLED, BookkeepingEntryKind.PURCHASE_SETTLED),
        Map.entry(OperationId.RECORD_PURCHASE_ON_CREDIT, BookkeepingEntryKind.PURCHASE_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED),
        Map.entry(
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT),
        Map.entry(
            OperationId.RECORD_INVENTORY_WRITE_DOWN, BookkeepingEntryKind.INVENTORY_WRITE_DOWN),
        Map.entry(OperationId.RECORD_INVENTORY_SHRINKAGE, BookkeepingEntryKind.INVENTORY_SHRINKAGE),
        Map.entry(
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE),
        Map.entry(OperationId.RECORD_EXPENSE_SETTLED, BookkeepingEntryKind.EXPENSE_SETTLED),
        Map.entry(OperationId.RECORD_EXPENSE_ON_CREDIT, BookkeepingEntryKind.EXPENSE_ON_CREDIT),
        Map.entry(OperationId.RECORD_RECEIPT, BookkeepingEntryKind.RECEIPT),
        Map.entry(OperationId.RECORD_PAYMENT, BookkeepingEntryKind.PAYMENT),
        Map.entry(OperationId.RECORD_OWNER_CONTRIBUTION, BookkeepingEntryKind.OWNER_CONTRIBUTION),
        Map.entry(OperationId.RECORD_OWNER_WITHDRAWAL, BookkeepingEntryKind.OWNER_WITHDRAWAL),
        Map.entry(OperationId.RECORD_PREPAYMENT, BookkeepingEntryKind.PREPAYMENT),
        Map.entry(OperationId.RECORD_DEFERRED_REVENUE, BookkeepingEntryKind.DEFERRED_REVENUE),
        Map.entry(OperationId.RECORD_ACCRUED_EXPENSE, BookkeepingEntryKind.ACCRUED_EXPENSE),
        Map.entry(
            OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
            BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION),
        Map.entry(
            OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
            BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT),
        Map.entry(
            OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
            BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION),
        Map.entry(
            OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
            BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION),
        Map.entry(
            OperationId.RECORD_FIXED_ASSET_DISPOSAL, BookkeepingEntryKind.FIXED_ASSET_DISPOSAL),
        Map.entry(OperationId.RECORD_FINANCING_BORROWING, BookkeepingEntryKind.FINANCING_BORROWING),
        Map.entry(
            OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
            BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT),
        Map.entry(
            OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
            BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL),
        Map.entry(
            OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
            BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT),
        Map.entry(
            OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
            BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION),
        Map.entry(
            OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
            BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT),
        Map.entry(
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL),
        Map.entry(
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT),
        Map.entry(
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
            BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE),
        Map.entry(OperationId.RECORD_OPENING_POSITION, BookkeepingEntryKind.OPENING_POSITION),
        Map.entry(OperationId.RECORD_REVERSAL, BookkeepingEntryKind.REVERSAL));
  }

  static Map<BookkeepingEntryKind, String> settlementAdjunctForbiddenContexts() {
    return Map.ofEntries(
        Map.entry(BookkeepingEntryKind.DIRECT_JOURNAL, "journal"),
        Map.entry(BookkeepingEntryKind.SALE_SETTLED, "saleSettled"),
        Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, "saleOnCredit"),
        Map.entry(BookkeepingEntryKind.PURCHASE_SETTLED, "purchaseSettled"),
        Map.entry(BookkeepingEntryKind.PURCHASE_ON_CREDIT, "purchaseOnCredit"),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
            "inventoryCapitalizationSettled"),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
            "inventoryCapitalizationOnCredit"),
        Map.entry(BookkeepingEntryKind.INVENTORY_WRITE_DOWN, "inventoryWriteDown"),
        Map.entry(BookkeepingEntryKind.INVENTORY_SHRINKAGE, "inventoryShrinkage"),
        Map.entry(BookkeepingEntryKind.INVENTORY_COUNT_INCREASE, "inventoryCountIncrease"),
        Map.entry(BookkeepingEntryKind.EXPENSE_SETTLED, "expenseSettled"),
        Map.entry(BookkeepingEntryKind.EXPENSE_ON_CREDIT, "expenseOnCredit"),
        Map.entry(BookkeepingEntryKind.OWNER_CONTRIBUTION, "ownerContribution"),
        Map.entry(BookkeepingEntryKind.OWNER_WITHDRAWAL, "ownerWithdrawal"),
        Map.entry(BookkeepingEntryKind.PREPAYMENT, "prepayment"),
        Map.entry(BookkeepingEntryKind.DEFERRED_REVENUE, "deferredRevenue"),
        Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE, "accruedExpense"),
        Map.entry(BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION, "accrualCutoffRecognition"),
        Map.entry(BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT, "accruedExpenseSettlement"),
        Map.entry(BookkeepingEntryKind.OPENING_POSITION, "openingPosition"),
        Map.entry(BookkeepingEntryKind.REVERSAL, "reversal"));
  }

  static ContractPostingRequestTemplateValidators.PostingTemplateFields canonicalFields(
      BookkeepingEntryKind entryKind,
      ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return switch (templateFamily(entryKind)) {
      case DIRECT_JOURNAL -> emptyFields(settlementAdjunct, JOURNAL_LINES, null);
      case STANDARD -> standardCanonicalFields(entryKind, settlementAdjunct);
      case INVENTORY -> inventoryCanonicalFields(entryKind, settlementAdjunct);
      case ACCRUAL_CUTOFF -> accrualCutoffCanonicalFields(entryKind, settlementAdjunct);
      case FIXED_ASSET, FINANCING, REALIZED_FOREIGN_EXCHANGE -> lifecycleContextFields(entryKind);
      case LATVIAN_PAYROLL -> emptyFields(settlementAdjunct, null, null, payrollTemplate());
      case LATVIAN_PAYROLL_SETTLEMENT -> payrollSettlementFields();
      case OPENING_POSITION -> emptyFields(settlementAdjunct, null, OPENING_BALANCES);
      case REVERSAL -> emptyFields(settlementAdjunct, null, null);
    };
  }

  private static TemplateFamily templateFamily(BookkeepingEntryKind entryKind) {
    return Objects.requireNonNull(
        TEMPLATE_FAMILIES.get(entryKind),
        () -> "No posting-template fixture family is registered for " + entryKind + ".");
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      payrollSettlementFields() {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        "cash",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor(
            "payroll-lv-2026-01-employee-001"),
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      standardCanonicalFields(
          BookkeepingEntryKind entryKind,
          ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
              settlementAdjunct) {
    return switch (entryKind) {
      case SALE_SETTLED ->
          amountFields("cash", null, null, "service-revenue", null, null, null, settlementAdjunct);
      case SALE_ON_CREDIT ->
          amountFields(
              null,
              "accounts-receivable",
              null,
              "service-revenue",
              null,
              null,
              null,
              settlementAdjunct);
      case PURCHASE_SETTLED ->
          quantityUnitCostFields(
              "cash", null, null, null, "inventory", null, null, settlementAdjunct);
      case PURCHASE_ON_CREDIT ->
          quantityUnitCostFields(
              null, null, "accounts-payable", null, "inventory", null, null, settlementAdjunct);
      case EXPENSE_SETTLED ->
          amountFields(
              "cash", null, null, null, null, "operating-expense", null, settlementAdjunct);
      case EXPENSE_ON_CREDIT ->
          amountFields(
              null,
              null,
              "accounts-payable",
              null,
              null,
              "operating-expense",
              null,
              settlementAdjunct);
      case RECEIPT ->
          amountFields(
              "cash", "accounts-receivable", null, null, null, null, null, settlementAdjunct);
      case PAYMENT ->
          amountFields("cash", null, "accounts-payable", null, null, null, null, settlementAdjunct);
      case OWNER_CONTRIBUTION, OWNER_WITHDRAWAL ->
          amountFields(
              "cash",
              null,
              null,
              null,
              null,
              null,
              ownerEquityAccount(entryKind),
              settlementAdjunct);
      default -> throw new IllegalArgumentException("Expected standard entry kind.");
    };
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      lifecycleContextFields(BookkeepingEntryKind entryKind) {
    ContractTemplates.PostingRequestTemplateDescriptor template =
        MachineContractPostEntryVariantSchemas.template(entryKind);
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        template.cashAccountCode(),
        template.receivableAccountCode(),
        template.payableAccountCode(),
        template.revenueAccountCode(),
        template.inventoryAccountCode(),
        template.expenseAccountCode(),
        template.writeDownLossAccountCode(),
        template.shrinkageLossAccountCode(),
        template.countGainAccountCode(),
        template.equityAccountCode(),
        template.amount(),
        template.quantity(),
        template.unitCost(),
        template.inventoryRelief(),
        template.settlementAdjunct(),
        template.foreignExchange(),
        template.tax(),
        template.lines(),
        template.openingBalances(),
        template.accrualCutoffId(),
        template.prepaymentAssetAccountCode(),
        template.deferredRevenueAccountCode(),
        template.accruedExpenseLiabilityAccountCode(),
        template.recognitionInterval(),
        template.latvianMonthlyPayroll(),
        template.latvianPayrollSettlement(),
        template.fixedAsset(),
        template.financing(),
        template.realizedForeignExchange());
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      accrualCutoffCanonicalFields(
          BookkeepingEntryKind entryKind,
          ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
              settlementAdjunct) {
    return switch (entryKind) {
      case PREPAYMENT ->
          accrualCutoffFields(
              "cash",
              null,
              "operating-expense",
              "prepayment-2026-q1",
              "prepaid-expense",
              null,
              null,
              recognitionInterval(),
              settlementAdjunct);
      case DEFERRED_REVENUE ->
          accrualCutoffFields(
              "cash",
              "service-revenue",
              null,
              "deferred-revenue-2026-q1",
              null,
              "deferred-revenue",
              null,
              recognitionInterval(),
              settlementAdjunct);
      case ACCRUED_EXPENSE ->
          accrualCutoffFields(
              null,
              null,
              "operating-expense",
              "accrued-expense-2026-01",
              null,
              null,
              "accrued-expense",
              null,
              settlementAdjunct);
      case ACCRUAL_CUTOFF_RECOGNITION ->
          accrualCutoffFields(
              null, null, null, "prepayment-2026-q1", null, null, null, null, settlementAdjunct);
      case ACCRUED_EXPENSE_SETTLEMENT ->
          accrualCutoffFields(
              "cash",
              null,
              null,
              "accrued-expense-2026-01",
              null,
              null,
              null,
              null,
              settlementAdjunct);
      default -> throw new IllegalArgumentException("Expected accrual cut-off entry kind.");
    };
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      inventoryCanonicalFields(
          BookkeepingEntryKind entryKind,
          ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
              settlementAdjunct) {
    return switch (entryKind) {
      case INVENTORY_CAPITALIZATION_SETTLED ->
          amountFields("cash", null, null, null, "inventory", null, null, settlementAdjunct);
      case INVENTORY_CAPITALIZATION_ON_CREDIT ->
          amountFields(
              null, null, "accounts-payable", null, "inventory", null, null, settlementAdjunct);
      case INVENTORY_WRITE_DOWN ->
          inventoryMaintenanceFields(
              "inventory", "inventory-write-down-loss", null, null, null, settlementAdjunct);
      case INVENTORY_SHRINKAGE ->
          inventoryMaintenanceFields(
              "inventory", null, "inventory-shrinkage-loss", null, "5", settlementAdjunct);
      case INVENTORY_COUNT_INCREASE ->
          inventoryMaintenanceFields(
              "inventory", null, null, "inventory-count-gain", null, settlementAdjunct);
      default -> throw new IllegalArgumentException("Expected inventory entry kind.");
    };
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields accrualCutoffFields(
      @Nullable String cashAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String expenseAccountCode,
      String accrualCutoffId,
      @Nullable String prepaymentAssetAccountCode,
      @Nullable String deferredRevenueAccountCode,
      @Nullable String accruedExpenseLiabilityAccountCode,
      ContractTemplates.@Nullable RecognitionIntervalTemplateDescriptor recognitionInterval,
      ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        null,
        null,
        revenueAccountCode,
        null,
        expenseAccountCode,
        null,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null,
        accrualCutoffId,
        prepaymentAssetAccountCode,
        deferredRevenueAccountCode,
        accruedExpenseLiabilityAccountCode,
        recognitionInterval,
        null,
        null,
        null,
        null,
        null);
  }

  private static String ownerEquityAccount(BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.OWNER_CONTRIBUTION ? "owner-capital" : "owner-draws";
  }

  static ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
      settlementAdjunctIfOwned(BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.RECEIPT || entryKind == BookkeepingEntryKind.PAYMENT
        ? settlementAdjunct()
        : null;
  }

  static ContractReversalTemplates.@Nullable ReversalTemplateDescriptor reversalIfOwned(
      BookkeepingEntryKind entryKind) {
    return entryKind == BookkeepingEntryKind.REVERSAL
        ? new ContractReversalTemplates.ReversalTemplateDescriptor("posting-1", "correction")
        : null;
  }

  static ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor settlementAdjunct() {
    return new ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor(
        "settlement-clearing", new MonetaryAmount("EUR", "250"));
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields amountFields(
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String payableAccountCode,
      @Nullable String revenueAccountCode,
      @Nullable String inventoryAccountCode,
      @Nullable String expenseAccountCode,
      @Nullable String equityAccountCode,
      ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        null,
        null,
        null,
        equityAccountCode,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      quantityUnitCostFields(
          @Nullable String cashAccountCode,
          @Nullable String receivableAccountCode,
          @Nullable String payableAccountCode,
          @Nullable String revenueAccountCode,
          @Nullable String inventoryAccountCode,
          @Nullable String expenseAccountCode,
          @Nullable String equityAccountCode,
          ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
              settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        receivableAccountCode,
        payableAccountCode,
        revenueAccountCode,
        inventoryAccountCode,
        expenseAccountCode,
        null,
        null,
        null,
        equityAccountCode,
        null,
        "5",
        new MonetaryAmount("EUR", "120"),
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields
      inventoryMaintenanceFields(
          String inventoryAccountCode,
          @Nullable String writeDownLossAccountCode,
          @Nullable String shrinkageLossAccountCode,
          @Nullable String countGainAccountCode,
          @Nullable String quantity,
          ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor
              settlementAdjunct) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        null,
        null,
        null,
        null,
        inventoryAccountCode,
        null,
        writeDownLossAccountCode,
        shrinkageLossAccountCode,
        countGainAccountCode,
        null,
        countGainAccountCode == null && quantity == null ? new MonetaryAmount("EUR", "1000") : null,
        countGainAccountCode == null ? quantity : "5",
        countGainAccountCode == null ? null : new MonetaryAmount("EUR", "120"),
        null,
        settlementAdjunct,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields emptyFields(
      ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable List<ContractTemplates.JournalLineTemplateDescriptor> lines,
      @Nullable List<ContractTemplates.OpeningBalanceTemplateDescriptor> openingBalances) {
    return emptyFields(settlementAdjunct, lines, openingBalances, null);
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields emptyFields(
      ContractSettlementTemplates.@Nullable SettlementAdjunctTemplateDescriptor settlementAdjunct,
      @Nullable List<ContractTemplates.JournalLineTemplateDescriptor> lines,
      @Nullable List<ContractTemplates.OpeningBalanceTemplateDescriptor> openingBalances,
      @Nullable MonthlyPayrollTemplateDescriptor latvianMonthlyPayroll) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        settlementAdjunct,
        null,
        null,
        lines,
        openingBalances,
        null,
        null,
        null,
        null,
        null,
        latvianMonthlyPayroll,
        null,
        null,
        null,
        null);
  }

  private static ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor
      payrollTemplate() {
    return new ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor(
        "payroll-lv-2026-01-employee-001",
        "employee-001",
        "2026-01",
        "wage-expense",
        "employer-social-expense",
        "net-wages-payable",
        "employee-social-payable",
        "employer-social-payable",
        "personal-income-tax-payable",
        new MonetaryAmount("EUR", "200000"));
  }

  private static void assertAmountTemplateShape(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNotNull(template.amount());
    assertNull(template.quantity());
    assertNull(template.unitCost());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertQuantityUnitCostTemplateShape(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertNull(template.amount());
    assertEquals("5", template.quantity());
    assertEquals(new MonetaryAmount("EUR", "120"), template.unitCost());
    assertNull(template.lines());
    assertNull(template.openingBalances());
    assertNull(template.reversal());
  }

  private static void assertRecognitionInterval(
      ContractTemplates.PostingRequestTemplateDescriptor template) {
    assertEquals(
        new ContractTemplates.RecognitionIntervalTemplateDescriptor("2026-01-15", "2026-03-31"),
        template.recognitionInterval());
  }

  private static ContractTemplates.RecognitionIntervalTemplateDescriptor recognitionInterval() {
    return new ContractTemplates.RecognitionIntervalTemplateDescriptor("2026-01-15", "2026-03-31");
  }

  /** Groups canonical posting-template fixtures by their independent contract surface. */
  private enum TemplateFamily {
    DIRECT_JOURNAL,
    STANDARD,
    INVENTORY,
    ACCRUAL_CUTOFF,
    FIXED_ASSET,
    FINANCING,
    REALIZED_FOREIGN_EXCHANGE,
    LATVIAN_PAYROLL,
    LATVIAN_PAYROLL_SETTLEMENT,
    OPENING_POSITION,
    REVERSAL
  }
}
