package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.InventoryBalanceBelowZero;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.runtime.ContractResponse;
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
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentType;
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
  private static final List<String> ENTRY_SEMANTICS_FACTORY_NAMES =
      List.of(
          "accountRoleMismatch",
          "accountTypeMismatch",
          "cashFlowAssetClassificationMismatch",
          "distinctRoleAccountsRequired",
          "economicNullJournal",
          "evidenceClassConflict",
          "financialPositionClassificationMismatch",
          "inventoryReliefRequiresTradingBook",
          "openingWindowAccountNotPermitted",
          "rawJournalBundlesOperationalEvents",
          "rawJournalRequiresCashLine",
          "rawJournalShadowsTypedEvent",
          "sourceDocumentTypeNotAccepted",
          "tradingSaleRequiresInventoryRelief",
          "verbRequiresRole",
          "verbRequiresTradingTemplate");

  private static final List<String> TAX_ENTRY_SEMANTICS_FACTORY_NAMES =
      List.of("taxApplicationKindMismatch", "unknownTaxCode", "unknownTaxRegistration");

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
          "evidence-class-conflict",
          "raw-journal-shadows-typed-event",
          "raw-journal-bundles-operational-events",
          "raw-journal-requires-cash-line",
          "opening-window-account-not-permitted");
  private static final List<String> ACCOUNT_STATE_CANONICAL_CODES =
      List.of(
          "unknown-account",
          "inactive-account",
          "non-postable-account",
          "inventory-balance-below-zero");

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
                new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))),
            PostingRejection.wireCode(
                new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                    new PostingId("posting-1b"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2"))),
            PostingRejection.wireCode(
                new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")))));
  }

  @Test
  void accountStateViolationWireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "unknown-account",
            "inactive-account",
            "non-postable-account",
            "inventory-balance-below-zero"),
        List.of(
            PostingRejection.wireCode(new PostingRejection.UnknownAccount(new AccountCode("1000"))),
            PostingRejection.wireCode(
                new PostingRejection.InactiveAccount(new AccountCode("2000"))),
            PostingRejection.wireCode(
                new PostingRejection.NonPostableAccount(
                    new AccountCode("3000"), dev.erst.fingrind.core.AccountNodeKind.HEADER)),
            PostingRejection.wireCode(
                new InventoryBalanceBelowZero(
                    new AccountCode("1400"),
                    "inventoryRelief.amount",
                    java.time.LocalDate.parse("2026-04-07"),
                    dev.erst.fingrind.core.BalanceSide.DEBIT,
                    dev.erst.fingrind.core.Money.parse("EUR", "10.00"),
                    dev.erst.fingrind.core.Money.parse("EUR", "50.00"),
                    dev.erst.fingrind.core.Money.parse("EUR", "40.00")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "posting-book-not-initialized",
            "entry-semantics-violations",
            "account-state-violations",
            "idempotency-key-conflict",
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
        PostingRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
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
        Arrays.stream(PostingRejectionSemantics.class.getDeclaredMethods())
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

    ContractResponse.RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "entry-semantics-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ENTRY_SEMANTICS_CANONICAL_CODES,
        descriptor.detailRejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
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
                        .map(ContractResponse.FieldDescriptor::name)
                        .toList()
                        .equals(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES)),
        descriptor.toString());
  }

  @Test
  void accountStateOwner_isPreparedForTheUniformRepairableViolationCore() {
    ContractResponse.RejectionDescriptor descriptor =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "account-state-violations".equals(rejection.code()))
            .findFirst()
            .orElseThrow();

    assertEquals(
        ACCOUNT_STATE_CANONICAL_CODES,
        descriptor.detailRejections().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
    for (ContractResponse.RejectionDescriptor detailDescriptor : descriptor.detailRejections()) {
      List<String> fieldNames =
          detailDescriptor.detailFields().stream()
              .map(ContractResponse.FieldDescriptor::name)
              .toList();
      assertTrue(fieldNames.containsAll(ENTRY_SEMANTICS_DETAIL_FIELD_NAMES), fieldNames.toString());
      assertTrue(fieldNames.contains("accountCode"), fieldNames.toString());
    }
  }

  @Test
  void singletonPostingRejectionFamiliesRemainSingleIssueEnvelopes() {
    ContractResponse.RejectionDescriptor duplicateIdempotencyKey =
        PostingRejection.descriptors().stream()
            .filter(rejection -> "idempotency-key-conflict".equals(rejection.code()))
            .findFirst()
            .orElseThrow();
    ContractResponse.RejectionDescriptor functionalCurrencyMismatch =
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
  void rejectionFactories_exposeStableEntrySemanticsDetails() {
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
        PostingRejectionSemantics.verbRequiresTradingTemplate(
            "PURCHASE_ON_CREDIT", BookTemplateId.OWNER_MANAGED_SERVICE);
    PostingRejection.EntrySemanticsViolation tradingSaleRequiresInventoryRelief =
        PostingRejectionSemantics.tradingSaleRequiresInventoryRelief("SALE_SETTLED");
    PostingRejection.EntrySemanticsViolation inventoryReliefRequiresTradingBook =
        PostingRejectionSemantics.inventoryReliefRequiresTradingBook(
            "SALE_SETTLED", BookTemplateId.OWNER_MANAGED_SERVICE);
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
    PostingRejection.EntrySemanticsViolation openingWindowAccountNotPermitted =
        PostingRejectionSemantics.openingWindowAccountNotPermitted(
            "OPENING_POSITION", new AccountCode("4100"));
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
        "inventory-relief-requires-trading-book", inventoryReliefRequiresTradingBook.code());
    assertEquals("inventoryRelief", inventoryReliefRequiresTradingBook.field());
    assertTrue(
        inventoryReliefRequiresTradingBook
            .message()
            .contains(BookTemplateId.OWNER_MANAGED_SERVICE.wireValue()));
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
    assertEquals("opening-window-account-not-permitted", openingWindowAccountNotPermitted.code());
    assertEquals("openingBalances[].accountCode", openingWindowAccountNotPermitted.field());
    assertTrue(openingWindowAccountNotPermitted.message().contains("OPENING_POSITION"));
    assertTrue(openingWindowAccountNotPermitted.message().contains("4100"));

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
