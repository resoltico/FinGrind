package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.InventoryMovementPrecedesAccountHorizon;
import dev.erst.fingrind.contract.bookkeeping.InventoryQuantityBelowZero;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingAccrualCutoffRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingInventoryRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingLatvianPayrollRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingRejection}. */
class PostingRejectionTest {
  private static final List<Class<?>> ENTRY_SEMANTICS_OWNERS =
      List.of(
          PostingAccrualCutoffRejectionSemantics.class,
          PostingInventoryRejectionSemantics.class,
          PostingLatvianPayrollRejectionSemantics.class,
          PostingRejectionSemantics.class);

  private static final List<String> ENTRY_SEMANTICS_FACTORY_NAMES =
      List.of(
          "accountRoleMismatch",
          "accountTypeMismatch",
          "applicationExceedsRemainingAmount",
          "applicationKindNotAdmitted",
          "applicationPrecedesHorizon",
          "cashFlowAssetClassificationMismatch",
          "distinctRoleAccountsRequired",
          "economicNullJournal",
          "employeeMonthAlreadyExists",
          "evidenceClassConflict",
          "financialPositionClassificationMismatch",
          "idAlreadyExists",
          "inventoryAcquisitionBreachesMinorUnitFloor",
          "inventoryAcquisitionCostNotExact",
          "inventoryAcquisitionForeignExchangeFunctionalAmountMismatch",
          "inventoryCapitalizationRequiresQuantityOnHand",
          "inventoryOpeningCarryingCostInvalid",
          "inventoryOpeningMustBeFirstMovement",
          "inventoryQuantityIncompatibleWithUnitOfMeasure",
          "inventoryReliefRequiresTradingBook",
          "ledgerAggregateMoneyRangeExceeded",
          "notFound",
          "openingInventoryRequiresQuantity",
          "openingQuantityRequiresInventory",
          "openingWindowAccountNotPermitted",
          "originReversalRequiresZeroApplications",
          "profileNotAdmitted",
          "rawJournalBundlesOperationalEvents",
          "rawJournalRequiresCashLine",
          "rawJournalShadowsTypedEvent",
          "rawJournalTouchesInventory",
          "recognitionOutsideInterval",
          "requiresAccrualBasis",
          "requiresEurBook",
          "reversalPrecedesHorizon",
          "runIdAlreadyExists",
          "runNotFound",
          "runReversalPrecedesRun",
          "runReversalRequiresSettlementsReversed",
          "runReversed",
          "settlementAlreadyExists",
          "settlementPrecedesRun",
          "settlementReversalPrecedesSettlement",
          "sourceDocumentTypeNotAccepted",
          "taxCompositionMoneyRangeExceeded",
          "tradingSaleRequiresInventoryRelief",
          "verbRequiresRole",
          "verbRequiresTradingTemplate");

  private static final List<String> TAX_ENTRY_SEMANTICS_FACTORY_NAMES =
      List.of(
          "taxApplicationKindMismatch",
          "taxCompositionMoneyRangeExceeded",
          "unknownTaxCode",
          "unknownTaxRegistration");

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
          "tax-composition-money-range-exceeded",
          "ledger-aggregate-money-range-exceeded",
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
          "fixed-asset-capitalization-reversal-requires-applications-reversed",
          "financing-arrangement-id-already-exists",
          "financing-arrangement-not-found",
          "financing-principal-repayment-exceeds-outstanding",
          "financing-interest-payment-exceeds-accrued",
          "financing-lifecycle-precedes-horizon",
          "financing-currency-mismatch",
          "financing-borrowing-reversal-requires-applications-reversed",
          "foreign-currency-obligation-id-already-exists",
          "foreign-currency-obligation-not-found",
          "foreign-currency-obligation-already-settled",
          "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
          "realized-foreign-exchange-settlement-transaction-amount-mismatch",
          "realized-foreign-exchange-settlement-functional-currency-mismatch",
          "foreign-currency-obligation-reversal-requires-settlement-reversed");
  private static final List<String> ACCOUNT_STATE_CANONICAL_CODES =
      List.of(
          "unknown-account",
          "inactive-account",
          "non-postable-account",
          "inventory-movement-precedes-account-horizon",
          "inventory-quantity-below-zero",
          "inventory-write-down-exceeds-carrying-cost");

  private static final List<String> ENTRY_SEMANTICS_DETAIL_FIELD_NAMES =
      List.of("code", "field", "message", "category", "repair");

  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "idempotency-key-conflict",
            "posting-effective-date-before-book-start",
            "posting-effective-date-in-future",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "opening-position-window-closed",
            "opening-position-touches-nominal-account",
            "reserved-result-classification",
            "reversal-target-not-found",
            "reversal-target-is-reversal",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
            PostingRejection.wireCode(
                new PostingRejection.EntrySemanticsViolations(
                    List.of(
                        PostingRejectionSemantics.accountTypeMismatch(
                            "SALE",
                            "cashAccountCode",
                            new AccountCode("1000"),
                            AccountType.ASSET,
                            AccountType.REVENUE)))),
            PostingRejection.wireCode(
                new PostingRejection.AccountStateViolations(
                    List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
            PostingRejection.wireCode(new PostingRejection.IdempotencyKeyConflict()),
            PostingRejection.wireCode(
                new PostingEffectiveDateBeforeBookStart(
                    java.time.LocalDate.parse("2025-12-31"),
                    java.time.LocalDate.parse("2026-01-01"))),
            PostingRejection.wireCode(
                new PostingRejection.PostingEffectiveDateInFuture(
                    java.time.LocalDate.parse("2026-05-02"),
                    java.time.LocalDate.parse("2026-05-01"))),
            PostingRejection.wireCode(
                new PostingRejection.BookFunctionalCurrencyMismatch(
                    CurrencyUnit.of("EUR"), CurrencyUnit.of("USD"))),
            PostingRejection.wireCode(
                new PostingRejection.SweptInterimResultViolation(
                    java.time.LocalDate.parse("2026-04-30"),
                    java.time.LocalDate.parse("2026-05-01"))),
            PostingRejection.wireCode(
                new PostingRejection.OpeningPositionWindowClosed(
                    PostingKind.STANDARD, java.time.LocalDate.parse("2026-05-02"))),
            PostingRejection.wireCode(
                new PostingRejection.OpeningPositionTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE)),
            PostingRejection.wireCode(
                new PostingRejection.ReservedResultClassification(
                    new AccountCode("3000"), FinancialPositionLineClassification.RESULT_HOLDING)),
            PostingRejection.wireCode(
                new PostingRejection.ReversalTargetNotFound(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
            PostingRejection.wireCode(
                new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                    new PostingId("d66e4aa4-9992-3220-9ea1-17b11ccaee61"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(
                    new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(
                    new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "unknown-account",
            "inactive-account",
            "non-postable-account",
            "inventory-movement-precedes-account-horizon",
            "inventory-quantity-below-zero",
            "inventory-write-down-exceeds-carrying-cost"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
            PostingRejection.wireCode(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), dev.erst.fingrind.core.AccountNodeKind.HEADER)),
            PostingRejection.wireCode(
                new InventoryMovementPrecedesAccountHorizon(
                    new AccountCode("1400"),
                    "inventoryRelief.quantity",
                    java.time.LocalDate.parse("2026-04-07"),
                    java.time.LocalDate.parse("2026-04-08"))),
            PostingRejection.wireCode(
                new InventoryQuantityBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.quantity",
                    java.time.LocalDate.parse("2026-04-07"),
                    Quantity.ofScaledUnits(0, 10),
                    Quantity.ofScaledUnits(0, 50),
                    Quantity.ofScaledUnits(0, 40))),
            PostingRejection.wireCode(
                new InventoryWriteDownExceedsCarryingCost(
                    new AccountCode("1400"),
                    "inventoryWriteDown.amount",
                    java.time.LocalDate.parse("2026-04-07"),
                    Money.parse("EUR", "10.00"),
                    Money.parse("EUR", "50.00"),
                    Money.parse("EUR", "40.00")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "idempotency-key-conflict",
            "posting-effective-date-before-book-start",
            "posting-effective-date-in-future",
            "book-functional-currency-mismatch",
            "closed-period-violation",
            "opening-position-window-closed",
            "opening-position-touches-nominal-account",
            "reserved-result-classification",
            "reversal-target-not-found",
            "reversal-target-is-reversal",
            "reversal-already-exists",
            "reversal-does-not-negate-target"),
        PostingRejection.descriptors().stream().map(RejectionDescriptor::code).toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        PostingRejection.wireCode(new PostingRejection.BookNotInitialized()),
        PostingRejection.bookNotInitializedCode());
  }

  @Test
  void entrySemanticsOwner_remainsExhaustiveAndPublishesCanonicalDetailDescriptors() {
    assertEquals(
        ENTRY_SEMANTICS_FACTORY_NAMES,
        ENTRY_SEMANTICS_OWNERS.stream()
            .flatMap(owner -> Arrays.stream(owner.getDeclaredMethods()))
            .filter(
                method ->
                    Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == PostingRejection.EntrySemanticsViolation.class)
            .map(Method::getName)
            .distinct()
            .sorted()
            .toList());
    assertEquals(
        TAX_ENTRY_SEMANTICS_FACTORY_NAMES,
        Arrays.stream(postingRejectionTaxSemanticsType().getDeclaredMethods())
            .filter(
                method ->
                    Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == PostingRejection.EntrySemanticsViolation.class)
            .map(Method::getName)
            .distinct()
            .sorted()
            .toList());

    RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "entry-semantics-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        descriptor.detailRejections().stream().map(RejectionDescriptor::code).toList());
    assertTrue(
        descriptor.detailFields().getFirst().description().contains("category"),
        descriptor.detailFields().toString());
    assertTrue(
        descriptor.detailFields().getFirst().description().contains("repair"),
        descriptor.detailFields().toString());
    assertEquals(
        ENTRY_SEMANTICS_DETAIL_FIELD_NAMES,
        recordComponentNames(PostingRejection.EntrySemanticsViolation.class));
    assertTrue(
        descriptor.detailRejections().stream()
            .allMatch(
                detail ->
                    detail.detailFields().stream()
                        .map(FieldDescriptor::name)
                        .toList()
                        .equals(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES)),
        descriptor.toString());
  }

  @Test
  void accountStateOwner_isPreparedForTheUniformRepairableViolationCore() {
    RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "account-state-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ACCOUNT_STATE_CANONICAL_CODES,
        descriptor.detailRejections().stream().map(RejectionDescriptor::code).toList());
    for (RejectionDescriptor detailDescriptor : descriptor.detailRejections()) {
      List<String> fieldNames =
          detailDescriptor.detailFields().stream().map(FieldDescriptor::name).toList();
      assertTrue(fieldNames.containsAll(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES), fieldNames.toString());
      assertTrue(fieldNames.contains("accountCode"), fieldNames.toString());
    }
  }

  @Test
  void singletonPostingRejectionFamiliesRemainSingleIssueEnvelopes() {
    RejectionDescriptor duplicateIdempotencyKey =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "idempotency-key-conflict".equals(rejection.code()))
            .findFirst()
            .orElseThrow();
    RejectionDescriptor functionalCurrencyMismatch =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "book-functional-currency-mismatch".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertTrue(duplicateIdempotencyKey.detailRejections().isEmpty());
    assertTrue(functionalCurrencyMismatch.detailRejections().isEmpty());
    assertTrue(
        duplicateIdempotencyKey.detailFields().stream()
            .noneMatch(field -> "violations".equals(field.name())));
    assertTrue(
        functionalCurrencyMismatch.detailFields().stream()
            .noneMatch(field -> "violations".equals(field.name())));
  }

  @Test
  void accountEvidenceAndTaxRejectionFactories_exposeStableEntrySemanticsDetails() {
    PostingRejection.EntrySemanticsViolation accountTypeViolation =
        PostingRejectionSemantics.accountTypeMismatch(
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            AccountType.ASSET,
            AccountType.REVENUE);
    assertEquals("account-type-mismatch", accountTypeViolation.code());
    assertEquals("cashAccountCode", accountTypeViolation.field());

    PostingRejection.EntrySemanticsViolation classificationViolation =
        PostingRejectionSemantics.financialPositionClassificationMismatch(
            "OWNER_CONTRIBUTION",
            "equityAccountCode",
            new AccountCode("3000"),
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            null);
    assertEquals("financial-position-classification-mismatch", classificationViolation.code());
    assertEquals("equityAccountCode", classificationViolation.field());
    assertTrue(classificationViolation.message().contains("<absent>"));

    PostingRejection.EntrySemanticsViolation classifiedMismatch =
        PostingRejectionSemantics.financialPositionClassificationMismatch(
            "OWNER_WITHDRAWAL",
            "equityAccountCode",
            new AccountCode("3100"),
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
            FinancialPositionLineClassification.RESULT_HOLDING);
    assertTrue(classifiedMismatch.message().contains("RESULT_HOLDING"));

    PostingRejection.EntrySemanticsViolation absentCashFlowClassification =
        PostingRejectionSemantics.cashFlowAssetClassificationMismatch(
            "SALE",
            "cashAccountCode",
            new AccountCode("1000"),
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
            null);
    PostingRejection.EntrySemanticsViolation presentCashFlowClassification =
        invokeAccountEntrySemantics(
            "cashFlowAssetClassificationMismatch",
            new Class<?>[] {
              String.class,
              String.class,
              String.class,
              AccountCode.class,
              CashFlowAssetClassification.class,
              CashFlowAssetClassification.class
            },
            "requestType",
            "record-sale-settled",
            "cashAccountCode",
            new AccountCode("1000"),
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT,
            CashFlowAssetClassification.NON_CASH);
    assertEquals("cash-flow-asset-classification-mismatch", absentCashFlowClassification.code());
    assertEquals("cashAccountCode", absentCashFlowClassification.field());
    assertTrue(absentCashFlowClassification.message().contains("<absent>"));
    assertTrue(
        presentCashFlowClassification
            .message()
            .contains(CashFlowAssetClassification.NON_CASH.wireValue()));
    assertTrue(
        presentCashFlowClassification.message().contains("requestType 'record-sale-settled'"));

    PostingRejection.EntrySemanticsViolation evidenceViolation =
        PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
            "EXPENSE",
            new SourceDocumentType("invoice"),
            List.of("expense-receipt", "cash-disbursement"));
    PostingRejection.EntrySemanticsViolation unknownTaxRegistration =
        invokeTaxEntrySemantics(
            "unknownTaxRegistration",
            new Class<?>[] {String.class, TaxRegistrationId.class},
            "SALE",
            new TaxRegistrationId("tax-reg-1"));
    PostingRejection.EntrySemanticsViolation unknownTaxCode =
        invokeTaxEntrySemantics(
            "unknownTaxCode",
            new Class<?>[] {String.class, TaxRegistrationId.class, TaxCode.class},
            "SALE",
            new TaxRegistrationId("tax-reg-1"),
            new TaxCode("output-std"));
    PostingRejection.EntrySemanticsViolation taxApplicationKindMismatch =
        invokeTaxEntrySemantics(
            "taxApplicationKindMismatch",
            new Class<?>[] {
              String.class, TaxCode.class, TaxApplicationKind.class, TaxApplicationKind.class
            },
            "SALE",
            new TaxCode("output-std"),
            TaxApplicationKind.OUTPUT_SALE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
    assertEquals("source-document-type-not-accepted", evidenceViolation.code());
    assertEquals("evidence.sourceDocuments[].sourceDocumentType", evidenceViolation.field());
    assertTrue(evidenceViolation.message().contains("expense-receipt, cash-disbursement"));
    assertEquals("unknown-tax-registration", unknownTaxRegistration.code());
    assertEquals("tax.taxRegistrationId", unknownTaxRegistration.field());
    assertTrue(unknownTaxRegistration.message().contains("tax-reg-1"));
    assertEquals("unknown-tax-code", unknownTaxCode.code());
    assertEquals("tax.taxCode", unknownTaxCode.field());
    assertTrue(unknownTaxCode.message().contains("output-std"));
    assertEquals("tax-application-kind-mismatch", taxApplicationKindMismatch.code());
    assertEquals("tax.taxCode", taxApplicationKindMismatch.field());
    assertTrue(taxApplicationKindMismatch.message().contains("OUTPUT_SALE"));
    assertTrue(
        taxApplicationKindMismatch
            .message()
            .contains(TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE.wireValue()));
  }

  @Test
  void entryModeRejectionFactories_exposeStableEntrySemanticsDetails() {
    PostingRejection.EntrySemanticsViolation distinctRoleAccountsViolation =
        PostingRejectionSemantics.distinctRoleAccountsRequired(
            "SALE", "cashAccountCode", "revenueAccountCode", new AccountCode("1000"));
    PostingRejection.EntrySemanticsViolation economicNullJournal =
        PostingRejectionSemantics.economicNullJournal("DIRECT_JOURNAL");
    PostingRejection.EntrySemanticsViolation rawJournalRequiresCashLine =
        PostingRejectionSemantics.rawJournalRequiresCashLine("DIRECT_JOURNAL");
    PostingRejection.EntrySemanticsViolation accountRoleMismatch =
        PostingRejectionSemantics.accountRoleMismatch(
            "RECEIPT",
            "settlementAdjunct.accountCode",
            new AccountCode("6100"),
            AccountRole.SETTLEMENT_ADJUNCT,
            AccountRole.EXPENSE);
    PostingRejection.EntrySemanticsViolation verbRequiresReceivableRole =
        PostingRejectionSemantics.verbRequiresRole("SALE_ON_CREDIT", AccountRole.RECEIVABLE);
    PostingRejection.EntrySemanticsViolation verbRequiresPayableRole =
        PostingRejectionSemantics.verbRequiresRole("EXPENSE_ON_CREDIT", AccountRole.PAYABLE);
    PostingRejection.EntrySemanticsViolation verbRequiresTradingTemplate =
        PostingInventoryRejectionSemantics.verbRequiresTradingTemplate(
            "PURCHASE_ON_CREDIT", BookTemplateId.OWNER_MANAGED_SERVICE);
    PostingRejection.EntrySemanticsViolation tradingSaleRequiresInventoryRelief =
        PostingInventoryRejectionSemantics.tradingSaleRequiresInventoryRelief("SALE_SETTLED");
    PostingRejection.EntrySemanticsViolation inventoryReliefRequiresTradingBook =
        PostingInventoryRejectionSemantics.inventoryReliefRequiresTradingBook(
            "SALE_SETTLED", BookTemplateId.OWNER_MANAGED_SERVICE);
    PostingRejection.EntrySemanticsViolation inventoryQuantityIncompatibleWithUnitOfMeasure =
        PostingInventoryRejectionSemantics.inventoryQuantityIncompatibleWithUnitOfMeasure(
            "inventoryRelief.quantity",
            "0.5",
            new AccountCode("inventory"),
            new UnitOfMeasure("unit", 0),
            "Quantity must not contain fractional digits at scale 0.");
    PostingRejection.EntrySemanticsViolation inventoryAcquisitionCostNotExact =
        PostingInventoryRejectionSemantics.inventoryAcquisitionCostNotExact(
            "0.25",
            Money.parse("EUR", "0.02"),
            new AccountCode("inventory"),
            new UnitOfMeasure("kg", 2));
    PostingRejection.EntrySemanticsViolation inventoryAcquisitionBreachesMinorUnitFloor =
        PostingInventoryRejectionSemantics.inventoryAcquisitionBreachesMinorUnitFloor(
            "0.25",
            Money.parse("EUR", "0.04"),
            new AccountCode("inventory"),
            new UnitOfMeasure("kg", 2),
            25L,
            Money.parse("EUR", "0.01"));
    PostingRejection.EntrySemanticsViolation foreignExchangeFunctionalAmountMismatch =
        PostingInventoryRejectionSemantics
            .inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
                "PURCHASE_SETTLED",
                new MonetaryAmount("EUR", "1000"),
                new MonetaryAmount("EUR", "120"));
    PostingRejection.EntrySemanticsViolation evidenceClassConflict =
        PostingRejectionSemantics.evidenceClassConflict(
            "SALE_SETTLED", EvidenceClass.INVOICE, EconomicEventClass.SETTLED_SALE);
    PostingRejection.EntrySemanticsViolation rawJournalShadowsTypedEvent =
        PostingRejectionSemantics.rawJournalShadowsTypedEvent(
            "DIRECT_JOURNAL", EconomicEventClass.CREDIT_SALE, "record-sale-on-credit");
    PostingRejection.EntrySemanticsViolation rawJournalBundlesOperationalEvents =
        PostingRejectionSemantics.rawJournalBundlesOperationalEvents(
            "DIRECT_JOURNAL",
            java.util.Set.of(EconomicEventClass.CREDIT_SALE, EconomicEventClass.AP_SETTLEMENT));
    PostingRejection.EntrySemanticsViolation rawJournalTouchesInventory =
        PostingInventoryRejectionSemantics.rawJournalTouchesInventory(
            "DIRECT_JOURNAL", new AccountCode("inventory"));
    PostingRejection.EntrySemanticsViolation openingWindowAccountNotPermitted =
        PostingRejectionSemantics.openingWindowAccountNotPermitted(
            "OPENING_POSITION", new AccountCode("4100"));
    PostingRejection.EntrySemanticsViolation openingInventoryRequiresQuantity =
        PostingInventoryRejectionSemantics.openingInventoryRequiresQuantity(
            "OPENING_POSITION", new AccountCode("inventory"));
    assertEquals("distinct-role-accounts-required", distinctRoleAccountsViolation.code());
    assertEquals(null, distinctRoleAccountsViolation.field());
    assertTrue(distinctRoleAccountsViolation.message().contains("cashAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("revenueAccountCode"));
    assertTrue(distinctRoleAccountsViolation.message().contains("1000"));
    assertEquals("economic-null-journal", economicNullJournal.code());
    assertEquals("lines", economicNullJournal.field());
    assertTrue(economicNullJournal.message().contains("reduces every referenced account to zero"));
    assertTrue(economicNullJournal.message().contains("DIRECT_JOURNAL"));
    assertEquals("raw-journal-requires-cash-line", rawJournalRequiresCashLine.code());
    assertEquals("lines[].accountCode", rawJournalRequiresCashLine.field());
    assertTrue(
        rawJournalRequiresCashLine.message().contains("declared cash account"),
        rawJournalRequiresCashLine.message());
    assertEquals("account-role-mismatch", accountRoleMismatch.code());
    assertEquals("settlementAdjunct.accountCode", accountRoleMismatch.field());
    assertTrue(accountRoleMismatch.message().contains("SETTLEMENT_ADJUNCT"));
    assertTrue(accountRoleMismatch.message().contains("EXPENSE"));
    assertEquals("verb-requires-receivable-role", verbRequiresReceivableRole.code());
    assertEquals("entryKind", verbRequiresReceivableRole.field());
    assertTrue(verbRequiresReceivableRole.message().contains("trade-receivable semantics"));
    assertTrue(verbRequiresReceivableRole.message().contains("SALE_ON_CREDIT"));
    assertEquals("verb-requires-payable-role", verbRequiresPayableRole.code());
    assertEquals("entryKind", verbRequiresPayableRole.field());
    assertTrue(verbRequiresPayableRole.message().contains("trade-payable semantics"));
    assertTrue(verbRequiresPayableRole.message().contains("EXPENSE_ON_CREDIT"));
    assertEquals("verb-requires-trading-template", verbRequiresTradingTemplate.code());
    assertEquals("entryKind", verbRequiresTradingTemplate.field());
    assertTrue(verbRequiresTradingTemplate.message().contains("PURCHASE_ON_CREDIT"));
    assertTrue(
        verbRequiresTradingTemplate
            .message()
            .contains(BookTemplateId.OWNER_MANAGED_SERVICE.wireValue()));
    assertEquals(
        "requiredRole must be RECEIVABLE or PAYABLE.",
        assertThrows(
                IllegalArgumentException.class,
                () -> PostingRejectionSemantics.verbRequiresRole("SALE_SETTLED", AccountRole.CASH))
            .getMessage());
    assertEquals(
        "trading-sale-requires-inventory-relief", tradingSaleRequiresInventoryRelief.code());
    assertEquals("inventoryRelief", tradingSaleRequiresInventoryRelief.field());
    assertTrue(tradingSaleRequiresInventoryRelief.message().contains("trading-template book"));
    assertEquals(
        "Add inventoryRelief with declared non-cash inventory, cost-of-sales, and quantity facts.",
        tradingSaleRequiresInventoryRelief.repair());
    assertEquals(
        "inventory-relief-requires-trading-book", inventoryReliefRequiresTradingBook.code());
    assertEquals("inventoryRelief", inventoryReliefRequiresTradingBook.field());
    assertTrue(
        inventoryReliefRequiresTradingBook
            .message()
            .contains(BookTemplateId.OWNER_MANAGED_SERVICE.wireValue()));
    assertEquals(
        "inventory-quantity-incompatible-with-unit-of-measure",
        inventoryQuantityIncompatibleWithUnitOfMeasure.code());
    assertEquals(
        "inventoryRelief.quantity", inventoryQuantityIncompatibleWithUnitOfMeasure.field());
    assertTrue(
        inventoryQuantityIncompatibleWithUnitOfMeasure.message().contains("0.5"),
        inventoryQuantityIncompatibleWithUnitOfMeasure.message());
    assertTrue(
        inventoryQuantityIncompatibleWithUnitOfMeasure.message().contains("quantityScale 0"),
        inventoryQuantityIncompatibleWithUnitOfMeasure.message());
    assertEquals("inventory-acquisition-cost-not-exact", inventoryAcquisitionCostNotExact.code());
    assertEquals("unitCost", inventoryAcquisitionCostNotExact.field());
    assertTrue(inventoryAcquisitionCostNotExact.message().contains("EUR 0.02"));
    assertTrue(inventoryAcquisitionCostNotExact.message().contains("0.25"));
    assertEquals(
        "inventory-acquisition-breaches-minor-unit-floor",
        inventoryAcquisitionBreachesMinorUnitFloor.code());
    assertEquals("unitCost", inventoryAcquisitionBreachesMinorUnitFloor.field());
    assertTrue(
        inventoryAcquisitionBreachesMinorUnitFloor.message().contains("EUR 0.01"),
        inventoryAcquisitionBreachesMinorUnitFloor.message());
    assertTrue(
        inventoryAcquisitionBreachesMinorUnitFloor.message().contains("EUR 0.25"),
        inventoryAcquisitionBreachesMinorUnitFloor.message());
    assertEquals(
        "inventory-acquisition-foreign-exchange-functional-amount-mismatch",
        foreignExchangeFunctionalAmountMismatch.code());
    assertEquals(
        "foreignExchange.functionalAmount", foreignExchangeFunctionalAmountMismatch.field());
    assertTrue(foreignExchangeFunctionalAmountMismatch.message().contains("EUR 10.00"));
    assertTrue(foreignExchangeFunctionalAmountMismatch.message().contains("EUR 1.20"));
    assertEquals("evidence-class-conflict", evidenceClassConflict.code());
    assertEquals("evidence.sourceDocuments[].sourceDocumentType", evidenceClassConflict.field());
    assertTrue(evidenceClassConflict.message().contains("SETTLED_SALE"));
    assertTrue(evidenceClassConflict.message().contains("INVOICE"));
    assertEquals("raw-journal-shadows-typed-event", rawJournalShadowsTypedEvent.code());
    assertEquals("lines", rawJournalShadowsTypedEvent.field());
    assertTrue(rawJournalShadowsTypedEvent.message().contains("CREDIT_SALE"));
    assertTrue(rawJournalShadowsTypedEvent.message().contains("record-sale-on-credit"));
    assertEquals(
        "raw-journal-bundles-operational-events", rawJournalBundlesOperationalEvents.code());
    assertEquals("lines", rawJournalBundlesOperationalEvents.field());
    assertTrue(
        rawJournalBundlesOperationalEvents
            .message()
            .contains("multiple operational event classes"));
    assertTrue(rawJournalBundlesOperationalEvents.message().contains("Split it into"));
    assertEquals("raw-journal-touches-inventory", rawJournalTouchesInventory.code());
    assertEquals("lines[].accountCode", rawJournalTouchesInventory.field());
    assertTrue(rawJournalTouchesInventory.message().contains("DIRECT_JOURNAL"));
    assertTrue(rawJournalTouchesInventory.message().contains("inventory"));
    assertEquals("opening-window-account-not-permitted", openingWindowAccountNotPermitted.code());
    assertEquals("openingBalances[].accountCode", openingWindowAccountNotPermitted.field());
    assertTrue(openingWindowAccountNotPermitted.message().contains("OPENING_POSITION"));
    assertTrue(openingWindowAccountNotPermitted.message().contains("4100"));
    assertEquals("opening-inventory-requires-quantity", openingInventoryRequiresQuantity.code());
    assertEquals("openingBalances[].quantity", openingInventoryRequiresQuantity.field());
    assertTrue(openingInventoryRequiresQuantity.message().contains("OPENING_POSITION"));
    assertTrue(openingInventoryRequiresQuantity.message().contains("quantity"));
    assertTrue(openingInventoryRequiresQuantity.message().contains("inventory"));
  }

  @Test
  void inventoryOpeningAndCapitalizationRejections_exposeStableDetails() {
    AccountCode inventoryAccountCode = new AccountCode("inventory");
    PostingRejection.EntrySemanticsViolation openingQuantityRequiresInventory =
        PostingInventoryRejectionSemantics.openingQuantityRequiresInventory(
            "OPENING_POSITION", inventoryAccountCode);
    PostingRejection.EntrySemanticsViolation capitalizationRequiresQuantity =
        PostingInventoryRejectionSemantics.inventoryCapitalizationRequiresQuantityOnHand(
            inventoryAccountCode);
    PostingRejection.EntrySemanticsViolation openingMustBeFirstMovement =
        PostingInventoryRejectionSemantics.inventoryOpeningMustBeFirstMovement(
            inventoryAccountCode);
    PostingRejection.EntrySemanticsViolation openingCarryingCostInvalid =
        PostingInventoryRejectionSemantics.inventoryOpeningCarryingCostInvalid(
            inventoryAccountCode);

    assertEquals("opening-quantity-requires-inventory", openingQuantityRequiresInventory.code());
    assertEquals("openingBalances[].quantity", openingQuantityRequiresInventory.field());
    assertTrue(openingQuantityRequiresInventory.message().contains("non-inventory"));
    assertEquals(
        "inventory-capitalization-requires-quantity-on-hand",
        capitalizationRequiresQuantity.code());
    assertEquals("inventoryAccountCode", capitalizationRequiresQuantity.field());
    assertTrue(capitalizationRequiresQuantity.message().contains("no quantity on hand"));
    assertEquals("inventory-opening-must-be-first-movement", openingMustBeFirstMovement.code());
    assertEquals("openingBalances[].accountCode", openingMustBeFirstMovement.field());
    assertTrue(openingMustBeFirstMovement.message().contains("movement history"));
    assertEquals("inventory-opening-carrying-cost-invalid", openingCarryingCostInvalid.code());
    assertEquals("openingBalances[].amount", openingCarryingCostInvalid.field());
    assertTrue(openingCarryingCostInvalid.message().contains("exact inventory pool"));
  }

  @Test
  void referencedAccountSet_preservesInsertionOrderAndRejectsNulls() {
    assertIterableEquals(
        List.of(new AccountCode("1000"), new AccountCode("2000")),
        List.copyOf(
            referencedAccountSet(
                new AccountCode("1000"), new AccountCode("1000"), new AccountCode("2000"))));
    assertThrows(
        NullPointerException.class, () -> referencedAccountSet(new AccountCode("1000"), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> referencedAccountSet(NullTestSupport.<AccountCode[]>nullOf()));
  }

  @Test
  void violationFamilies_copyInputsAndRejectEmptyCollections() {
    List<PostingRejection.AccountStateViolation> accountViolations = new ArrayList<>();
    accountViolations.add(
        new PostingRejection.NonPostableAccount(new AccountCode("3000"), AccountNodeKind.HEADER));
    PostingRejection.AccountStateViolations copiedAccountViolations =
        new PostingRejection.AccountStateViolations(accountViolations);
    accountViolations.clear();
    assertEquals(1, copiedAccountViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.AccountStateViolations(List.of()));

    List<PostingRejection.EntrySemanticsViolation> entryViolations = new ArrayList<>();
    entryViolations.add(PostingRejectionSemantics.economicNullJournal("DIRECT_JOURNAL"));
    PostingRejection.EntrySemanticsViolations copiedEntryViolations =
        new PostingRejection.EntrySemanticsViolations(entryViolations);
    entryViolations.clear();
    assertEquals(1, copiedEntryViolations.violations().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostingRejection.EntrySemanticsViolations(List.of()));
  }

  @Test
  void entrySemanticsViolation_rejectsBlankStructuredFields() {
    assertEquals(
        ENTRY_SEMANTICS_DETAIL_FIELD_NAMES,
        recordComponentNames(PostingRejection.EntrySemanticsViolation.class));

    IllegalArgumentException blankCode =
        assertEntrySemanticsViolationValidationFailure(
            "", null, "message", "classification", "repair");
    IllegalArgumentException blankField =
        assertEntrySemanticsViolationValidationFailure(
            "code", " ", "message", "classification", "repair");
    IllegalArgumentException blankMessage =
        assertEntrySemanticsViolationValidationFailure(
            "code", null, "", "classification", "repair");
    IllegalArgumentException blankCategory =
        assertEntrySemanticsViolationValidationFailure("code", null, "message", " ", "repair");
    IllegalArgumentException blankRepair =
        assertEntrySemanticsViolationValidationFailure(
            "code", null, "message", "classification", "");

    assertEquals("code must not be blank.", blankCode.getMessage());
    assertEquals("field must not be blank.", blankField.getMessage());
    assertEquals("message must not be blank.", blankMessage.getMessage());
    assertEquals("category must not be blank.", blankCategory.getMessage());
    assertEquals("repair must not be blank.", blankRepair.getMessage());
  }

  @Test
  void entrySemanticsViolation_rejectsUnsupportedCodesAndKnownMetadataDrift() {
    IllegalArgumentException unsupportedCode =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation("unsupported-code", null, "message"));
    assertEquals(
        "Unsupported entry semantics violation code: 'unsupported-code'.",
        unsupportedCode.getMessage());

    assertDoesNotThrow(
        () ->
            new PostingRejection.EntrySemanticsViolation(
                "code", null, "message", "classification", "repair"));

    IllegalArgumentException wrongCategory =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation(
                    "economic-null-journal",
                    "lines",
                    "message",
                    "wrong-category",
                    "Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting."));
    IllegalArgumentException wrongRepair =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingRejection.EntrySemanticsViolation(
                    "economic-null-journal", "lines", "message", "journal-lines", "wrong repair"));

    assertEquals(
        "Entry semantics violation category for code 'economic-null-journal' must be 'journal-lines'.",
        wrongCategory.getMessage());
    assertEquals(
        "Entry semantics violation repair for code 'economic-null-journal' must be 'Adjust the journal lines so at least one referenced account retains non-zero movement after debit-credit netting.'.",
        wrongRepair.getMessage());
  }

  private static List<String> recordComponentNames(Class<?> recordType) {
    return List.of(recordType.getRecordComponents()).stream()
        .map(RecordComponent::getName)
        .toList();
  }

  private static Class<?> postingRejectionTaxSemanticsType() {
    return assertDoesNotThrow(
        () -> Class.forName("dev.erst.fingrind.contract.bookkeeping.PostingRejectionTaxSemantics"));
  }

  private static Class<?> postingAccountRejectionSemanticsType() {
    return assertDoesNotThrow(
        () ->
            Class.forName(
                "dev.erst.fingrind.contract.bookkeeping.PostingAccountRejectionSemantics"));
  }

  private static PostingRejection.EntrySemanticsViolation invokeTaxEntrySemantics(
      String methodName, Class<?>[] parameterTypes, Object... arguments) {
    MethodType methodType =
        assertDoesNotThrow(
            () ->
                MethodType.methodType(
                    PostingRejection.EntrySemanticsViolation.class, parameterTypes));
    MethodHandle methodHandle =
        assertDoesNotThrow(
            () ->
                MethodHandles.privateLookupIn(
                        postingRejectionTaxSemanticsType(), MethodHandles.lookup())
                    .findStatic(postingRejectionTaxSemanticsType(), methodName, methodType));
    return assertDoesNotThrow(
        () ->
            (PostingRejection.EntrySemanticsViolation) methodHandle.invokeWithArguments(arguments));
  }

  private static PostingRejection.EntrySemanticsViolation invokeAccountEntrySemantics(
      String methodName, Class<?>[] parameterTypes, Object... arguments) {
    MethodType methodType =
        assertDoesNotThrow(
            () ->
                MethodType.methodType(
                    PostingRejection.EntrySemanticsViolation.class, parameterTypes));
    MethodHandle methodHandle =
        assertDoesNotThrow(
            () ->
                MethodHandles.privateLookupIn(
                        postingAccountRejectionSemanticsType(), MethodHandles.lookup())
                    .findStatic(postingAccountRejectionSemanticsType(), methodName, methodType));
    return assertDoesNotThrow(
        () ->
            (PostingRejection.EntrySemanticsViolation) methodHandle.invokeWithArguments(arguments));
  }

  private static IllegalArgumentException assertEntrySemanticsViolationValidationFailure(
      String code,
      @org.jspecify.annotations.Nullable String field,
      String message,
      String category,
      String repair) {
    Constructor<PostingRejection.EntrySemanticsViolation> constructor =
        assertDoesNotThrow(
            () ->
                PostingRejection.EntrySemanticsViolation.class.getDeclaredConstructor(
                    String.class, String.class, String.class, String.class, String.class));
    InvocationTargetException invocationTargetException =
        assertThrows(
            InvocationTargetException.class,
            () -> constructor.newInstance(code, field, message, category, repair));
    return assertInstanceOf(IllegalArgumentException.class, invocationTargetException.getCause());
  }

  private static Set<AccountCode> referencedAccountSet(AccountCode... accountCodes) {
    Objects.requireNonNull(accountCodes, "accountCodes");
    Set<AccountCode> referencedAccounts = new LinkedHashSet<>();
    for (AccountCode accountCode : accountCodes) {
      referencedAccounts.add(Objects.requireNonNull(accountCode, "accountCode"));
    }
    return referencedAccounts;
  }
}
