package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Locks executor entry-semantics translators to the canonical published rejection owner. */
class BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest {
  private static final List<String> ENTRY_SEMANTICS_CANONICAL_CODES =
      List.of(
          "economic-null-journal",
          "distinct-role-accounts-required",
          "account-type-mismatch",
          "cash-flow-asset-classification-mismatch",
          "financial-position-classification-mismatch",
          "account-role-mismatch",
          "source-document-type-not-accepted",
          "unknown-tax-registration",
          "unknown-tax-code",
          "tax-application-kind-mismatch",
          "verb-requires-receivable-role",
          "verb-requires-payable-role",
          "verb-requires-trading-template",
          "trading-sale-requires-inventory-relief",
          "inventory-relief-requires-trading-book",
          "inventory-quantity-incompatible-with-unit-of-measure",
          "inventory-acquisition-cost-not-exact",
          "inventory-acquisition-breaches-minor-unit-floor",
          "inventory-acquisition-foreign-exchange-functional-amount-mismatch",
          "evidence-class-conflict",
          "raw-journal-shadows-typed-event",
          "raw-journal-bundles-operational-events",
          "raw-journal-requires-cash-line",
          "raw-journal-touches-inventory",
          "opening-window-account-not-permitted",
          "opening-inventory-requires-quantity",
          "opening-quantity-requires-inventory",
          "inventory-capitalization-requires-quantity-on-hand",
          "inventory-opening-carrying-cost-invalid",
          "inventory-opening-must-be-first-movement",
          "accrual-cutoff-requires-accrual-basis",
          "accrual-cutoff-id-already-exists",
          "accrual-cutoff-not-found",
          "accrual-cutoff-application-kind-not-admitted",
          "accrual-cutoff-application-outside-recognition-interval",
          "accrual-cutoff-application-precedes-horizon",
          "accrual-cutoff-application-exceeds-remaining-amount",
          "accrual-cutoff-reversal-precedes-horizon",
          "accrual-cutoff-origin-reversal-requires-zero-applications",
          "latvian-payroll-requires-eur-book",
          "latvian-payroll-profile-not-admitted",
          "latvian-payroll-run-id-already-exists",
          "latvian-payroll-employee-month-already-exists",
          "latvian-payroll-run-not-found",
          "latvian-payroll-run-reversed",
          "latvian-payroll-settlement-precedes-run",
          "latvian-payroll-settlement-already-exists",
          "latvian-payroll-run-reversal-requires-settlements-reversed",
          "latvian-payroll-settlement-reversal-precedes-settlement",
          "latvian-payroll-run-reversal-precedes-run",
          "fixed-asset-id-already-exists",
          "fixed-asset-not-found",
          "fixed-asset-already-disposed",
          "fixed-asset-depreciation-precedes-in-service-date",
          "fixed-asset-lifecycle-precedes-horizon",
          "fixed-asset-fully-depreciated",
          "fixed-asset-disposal-currency-mismatch",
          "financing-arrangement-id-already-exists",
          "financing-arrangement-not-found",
          "financing-principal-repayment-exceeds-outstanding",
          "financing-interest-payment-exceeds-accrued",
          "financing-lifecycle-precedes-horizon",
          "financing-currency-mismatch",
          "foreign-currency-obligation-id-already-exists",
          "foreign-currency-obligation-not-found",
          "foreign-currency-obligation-already-settled",
          "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
          "realized-foreign-exchange-settlement-transaction-amount-mismatch",
          "realized-foreign-exchange-settlement-functional-currency-mismatch");
  private static final List<Class<?>> ENTRY_SEMANTICS_OWNERS =
      List.of(
          BookkeepingAccountSemanticsViolations.class,
          AccrualCutoffEntrySemanticsViolations.class,
          BookkeepingEvidenceSemanticsViolations.class,
          BookkeepingEntryModeSemanticsViolations.class,
          InventoryEntrySemanticsViolations.class,
          BookkeepingTaxSemanticsViolations.class);
  private static final List<String> NON_FACTORY_OWNED_CODES =
      List.of(
          "inventory-capitalization-requires-quantity-on-hand",
          "inventory-opening-carrying-cost-invalid",
          "inventory-opening-must-be-first-movement",
          "accrual-cutoff-id-already-exists",
          "accrual-cutoff-not-found",
          "accrual-cutoff-application-kind-not-admitted",
          "accrual-cutoff-application-outside-recognition-interval",
          "accrual-cutoff-application-precedes-horizon",
          "accrual-cutoff-application-exceeds-remaining-amount",
          "accrual-cutoff-reversal-precedes-horizon",
          "accrual-cutoff-origin-reversal-requires-zero-applications",
          "latvian-payroll-requires-eur-book",
          "latvian-payroll-profile-not-admitted",
          "latvian-payroll-run-id-already-exists",
          "latvian-payroll-employee-month-already-exists",
          "latvian-payroll-run-not-found",
          "latvian-payroll-run-reversed",
          "latvian-payroll-settlement-precedes-run",
          "latvian-payroll-settlement-already-exists",
          "latvian-payroll-run-reversal-requires-settlements-reversed",
          "latvian-payroll-settlement-reversal-precedes-settlement",
          "latvian-payroll-run-reversal-precedes-run",
          "fixed-asset-id-already-exists",
          "fixed-asset-not-found",
          "fixed-asset-already-disposed",
          "fixed-asset-depreciation-precedes-in-service-date",
          "fixed-asset-lifecycle-precedes-horizon",
          "fixed-asset-fully-depreciated",
          "fixed-asset-disposal-currency-mismatch",
          "financing-arrangement-id-already-exists",
          "financing-arrangement-not-found",
          "financing-principal-repayment-exceeds-outstanding",
          "financing-interest-payment-exceeds-accrued",
          "financing-lifecycle-precedes-horizon",
          "financing-currency-mismatch",
          "foreign-currency-obligation-id-already-exists",
          "foreign-currency-obligation-not-found",
          "foreign-currency-obligation-already-settled",
          "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
          "realized-foreign-exchange-settlement-transaction-amount-mismatch",
          "realized-foreign-exchange-settlement-functional-currency-mismatch");
  private static final Map<String, EntrySemanticsMethodInvoker> ENTRY_SEMANTICS_METHOD_INVOKERS =
      Map.ofEntries(
          Map.entry(
              "accountRoleMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeAccountRoleMismatch),
          Map.entry(
              "requiresAccrualBasis",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeRequiresAccrualBasis),
          Map.entry(
              "accountTypeMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeAccountTypeMismatch),
          Map.entry(
              "cashFlowAssetClassificationMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeCashFlowAssetClassificationMismatch),
          Map.entry(
              "distinctRoleAccountsRequired",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeDistinctRoleAccountsRequired),
          Map.entry(
              "economicNullJournal",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeEconomicNullJournal),
          Map.entry(
              "evidenceClassConflict",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeEvidenceClassConflict),
          Map.entry(
              "financialPositionClassificationMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeFinancialPositionClassificationMismatch),
          Map.entry(
              "openingInventoryRequiresQuantity",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeOpeningInventoryRequiresQuantity),
          Map.entry(
              "openingWindowAccountNotPermitted",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeOpeningWindowAccountNotPermitted),
          Map.entry(
              "rawJournalBundlesOperationalEvents",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeRawJournalBundlesOperationalEvents),
          Map.entry(
              "rawJournalTouchesInventory",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeRawJournalTouchesInventory),
          Map.entry(
              "rawJournalRequiresCashLine",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeRawJournalRequiresCashLine),
          Map.entry(
              "rawJournalShadowsTypedEvent",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeRawJournalShadowsTypedEvent),
          Map.entry(
              "sourceDocumentTypeNotAccepted",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeSourceDocumentTypeNotAccepted),
          Map.entry(
              "taxApplicationKindMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeTaxApplicationKindMismatch),
          Map.entry(
              "unknownTaxCode",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeUnknownTaxCode),
          Map.entry(
              "unknownTaxRegistration",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeUnknownTaxRegistration),
          Map.entry(
              "verbRequiresPayableRole",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeVerbRequiresPayableRole),
          Map.entry(
              "verbRequiresReceivableRole",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeVerbRequiresReceivableRole),
          Map.entry(
              "tradingSaleRequiresInventoryRelief",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeTradingSaleRequiresInventoryRelief),
          Map.entry(
              "verbRequiresTradingTemplate",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeVerbRequiresTradingTemplate),
          Map.entry(
              "inventoryReliefRequiresTradingBook",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeInventoryReliefRequiresTradingBook),
          Map.entry(
              "inventoryQuantityIncompatibleWithUnitOfMeasure",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeInventoryQuantityIncompatibleWithUnitOfMeasure),
          Map.entry(
              "inventoryAcquisitionCostNotExact",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeInventoryAcquisitionCostNotExact),
          Map.entry(
              "inventoryAcquisitionBreachesMinorUnitFloor",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeInventoryAcquisitionBreachesMinorUnitFloor),
          Map.entry(
              "inventoryAcquisitionForeignExchangeFunctionalAmountMismatch",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeInventoryAcquisitionForeignExchangeFunctionalAmountMismatch),
          Map.entry(
              "openingQuantityRequiresInventory",
              BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest
                  ::invokeOpeningQuantityRequiresInventory));

  @Test
  void bookkeepingEntrySemanticsCodes_matchThePublishedCanonicalDescriptorOwner() {
    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        PostingRejection.descriptors().stream()
            .filter(descriptor -> "entry-semantics-violations".equals(descriptor.code()))
            .findFirst()
            .orElseThrow()
            .detailRejections()
            .stream()
            .map(dev.erst.fingrind.contract.runtime.ContractResponse.RejectionDescriptor::code)
            .toList());
    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES.stream()
            .filter(code -> !NON_FACTORY_OWNED_CODES.contains(code))
            .toList(),
        ENTRY_SEMANTICS_OWNERS.stream()
            .flatMap(owner -> Arrays.stream(owner.getDeclaredMethods()))
            .filter(
                method ->
                    Modifier.isStatic(method.getModifiers())
                        && method.getReturnType()
                            == BookkeepingPostingRejection.EntrySemanticsViolation.class
                        && method.getParameterCount() > 0
                        && method.getParameterTypes()[0] == String.class)
            .map(BookkeepingPublishedLanguageTranslatorSemanticsOwnerTest::invokeFactory)
            .map(BookkeepingPostingRejection.EntrySemanticsViolation::code)
            .distinct()
            .sorted(java.util.Comparator.comparingInt(ENTRY_SEMANTICS_CANONICAL_CODES::indexOf))
            .toList());
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeFactory(Method method) {
    try {
      EntrySemanticsMethodInvoker invoker = ENTRY_SEMANTICS_METHOD_INVOKERS.get(method.getName());
      if (invoker == null) {
        throw new AssertionError("Unexpected entry-semantics factory: " + method.getName());
      }
      return invoker.invoke(method);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(exception.getMessage(), exception);
    }
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeAccountTypeMismatch(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            AccountType.ASSET,
            AccountType.REVENUE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeRequiresAccrualBasis(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "PREPAYMENT");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeAccountRoleMismatch(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "RECEIPT",
            "settlementAdjunct.accountCode",
            new AccountCode("6100"),
            AccountRole.SETTLEMENT_ADJUNCT,
            AccountRole.EXPENSE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeCashFlowAssetClassificationMismatch(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
            CashFlowAssetClassification.NON_CASH);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeFinancialPositionClassificationMismatch(Method method)
          throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "OWNER_CONTRIBUTION",
            "equityAccountCode",
            new AccountCode("3000"),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            null);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeSourceDocumentTypeNotAccepted(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new SourceDocumentType("invoice"),
            List.of("cash-receipt", "cash-sale"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeDistinctRoleAccountsRequired(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            "cashAccountCode",
            "revenueAccountCode",
            new AccountCode("1000"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeUnknownTaxRegistration(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "SALE", new TaxRegistrationId("tax-reg-1"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeUnknownTaxCode(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new TaxRegistrationId("tax-reg-1"),
            new TaxCode("output-std"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeTaxApplicationKindMismatch(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE",
            new TaxCode("output-std"),
            TaxApplicationKind.OUTPUT_SALE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeEconomicNullJournal(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "DIRECT_JOURNAL");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeVerbRequiresReceivableRole(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "SALE_ON_CREDIT");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeVerbRequiresPayableRole(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "EXPENSE_ON_CREDIT");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeVerbRequiresTradingTemplate(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null, "entryKind", "PURCHASE_ON_CREDIT", BookTemplateId.OWNER_MANAGED_SERVICE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeTradingSaleRequiresInventoryRelief(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "SALE_SETTLED");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeInventoryReliefRequiresTradingBook(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "SALE_SETTLED", BookTemplateId.OWNER_MANAGED_SERVICE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeInventoryQuantityIncompatibleWithUnitOfMeasure(Method method)
          throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "inventoryRelief.quantity",
            "0.5",
            new AccountCode("inventory"),
            new UnitOfMeasure("unit", 0),
            "Quantity must not contain fractional digits at scale 0.");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeInventoryAcquisitionCostNotExact(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "0.25",
            Money.parse("EUR", "0.02"),
            new AccountCode("inventory"),
            new UnitOfMeasure("kg", 2));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeInventoryAcquisitionBreachesMinorUnitFloor(Method method)
          throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "0.25",
            Money.parse("EUR", "0.04"),
            new AccountCode("inventory"),
            new UnitOfMeasure("kg", 2),
            25L,
            Money.parse("EUR", "0.01"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeInventoryAcquisitionForeignExchangeFunctionalAmountMismatch(Method method)
          throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "PURCHASE_SETTLED",
            new MonetaryAmount("EUR", "1000"),
            new MonetaryAmount("EUR", "120"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation invokeEvidenceClassConflict(
      Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "SALE_SETTLED",
            EvidenceClass.INVOICE,
            EconomicEventClass.SETTLED_SALE);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeRawJournalShadowsTypedEvent(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "DIRECT_JOURNAL",
            EconomicEventClass.SETTLED_SALE,
            "record-sale-settled");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeRawJournalBundlesOperationalEvents(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(
            null,
            "entryKind",
            "DIRECT_JOURNAL",
            java.util.Set.of(EconomicEventClass.AR_SETTLEMENT, EconomicEventClass.SETTLED_SALE));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeRawJournalRequiresCashLine(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "DIRECT_JOURNAL");
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeRawJournalTouchesInventory(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "DIRECT_JOURNAL", new AccountCode("inventory"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeOpeningInventoryRequiresQuantity(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "OPENING_POSITION", new AccountCode("inventory"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeOpeningQuantityRequiresInventory(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "OPENING_POSITION", new AccountCode("cash"));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation
      invokeOpeningWindowAccountNotPermitted(Method method) throws ReflectiveOperationException {
    return (BookkeepingPostingRejection.EntrySemanticsViolation)
        method.invoke(null, "entryKind", "OPENING_POSITION", new AccountCode("4000"));
  }

  /** Reflection bridge for invoking one static entry-semantics helper under test. */
  @FunctionalInterface
  private interface EntrySemanticsMethodInvoker {
    BookkeepingPostingRejection.EntrySemanticsViolation invoke(Method method)
        throws ReflectiveOperationException;
  }
}
