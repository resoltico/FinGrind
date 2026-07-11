package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for canonical protocol request-field ownership and field inventories. */
class ProtocolRequestFieldSetsTest {
  @Test
  void planExecutionFactsAndLedgerPlanFieldConstantsValidateTheirShape() {
    PlanExecutionFacts facts =
        new PlanExecutionFacts(
            PlanTransactionMode.ATOMIC,
            PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
            "complete journal",
            List.of("limit"));
    assertEquals(List.of("limit"), facts.hardLimitations());
    assertEquals(List.of("planId", "steps"), ProtocolLedgerPlanFields.planFields());
    assertEquals(
        List.of(
            "stepId",
            "kind",
            "ensureBook",
            "posting",
            "declareAccount",
            "query",
            "assertion",
            "postingId"),
        ProtocolLedgerPlanFields.stepFields());
    assertEquals(
        List.of(
            "accountCode",
            "effectiveDateFrom",
            "effectiveDateTo",
            "postingCoverage",
            "limit",
            "cursor"),
        ProtocolLedgerPlanFields.queryFields());
    assertEquals(
        List.of(
            "kind",
            "accountCode",
            "postingId",
            "effectiveDateFrom",
            "effectiveDateTo",
            "netAmount",
            "balanceSide"),
        ProtocolLedgerPlanFields.assertionFields());
    assertEquals("planId", ProtocolLedgerPlanFields.Plan.PLAN_ID);
    assertEquals("steps", ProtocolLedgerPlanFields.Plan.STEPS);
    assertEquals("stepId", ProtocolLedgerPlanFields.Step.STEP_ID);
    assertEquals("kind", ProtocolLedgerPlanFields.Step.KIND);
    assertEquals("ensureBook", ProtocolLedgerPlanFields.Step.ENSURE_BOOK);
    assertEquals("posting", ProtocolLedgerPlanFields.Step.POSTING);
    assertEquals("declareAccount", ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT);
    assertEquals("query", ProtocolLedgerPlanFields.Step.QUERY);
    assertEquals("assertion", ProtocolLedgerPlanFields.Step.ASSERTION);
    assertEquals("postingId", ProtocolLedgerPlanFields.Step.POSTING_ID);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Query.ACCOUNT_CODE);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
    assertEquals("limit", ProtocolLedgerPlanFields.Query.LIMIT);
    assertEquals("cursor", ProtocolLedgerPlanFields.Query.CURSOR);
    assertEquals("kind", ProtocolLedgerPlanFields.Assertion.KIND);
    assertEquals("accountCode", ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
    assertEquals("postingId", ProtocolLedgerPlanFields.Assertion.POSTING_ID);
    assertEquals("effectiveDateFrom", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO);
    assertEquals("netAmount", ProtocolLedgerPlanFields.Assertion.NET_AMOUNT);
    assertEquals("balanceSide", ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE);
    assertThrows(
        NullPointerException.class,
        () ->
            new PlanExecutionFacts(
                nullOf(), PlanFailurePolicy.HALT_ON_FIRST_FAILURE, "journal", List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new PlanExecutionFacts(PlanTransactionMode.ATOMIC, nullOf(), "journal", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PlanExecutionFacts(
                PlanTransactionMode.ATOMIC,
                PlanFailurePolicy.HALT_ON_FIRST_FAILURE,
                " ",
                List.of()));
  }

  @Test
  void postEntryFieldInventoriesFollowTheirCanonicalOwners() {
    assertEquals(
        List.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "receivableAccountCode",
            "payableAccountCode",
            "revenueAccountCode",
            "inventoryAccountCode",
            "expenseAccountCode",
            "writeDownLossAccountCode",
            "shrinkageLossAccountCode",
            "countGainAccountCode",
            "equityAccountCode",
            "amount",
            "quantity",
            "unitCost",
            "inventoryRelief",
            "settlementAdjunct",
            "foreignExchange",
            "tax",
            "lines",
            "openingBalances",
            "evidence",
            "provenance",
            "reversal"),
        ProtocolPostEntryFields.topLevelFields());
    assertEquals(
        List.of("accountCode", "side", "amount"), ProtocolPostEntryFields.journalLineFields());
    assertEquals(
        List.of("accountCode", "side", "amount", "quantity"),
        ProtocolPostEntryFields.openingBalanceFields());
    assertEquals(
        List.of("transactionAmount", "functionalAmount", "quotedRate", "treatmentKind"),
        ProtocolPostEntryFields.foreignExchangeFields());
    assertEquals(
        List.of("transactionCurrencyAmount", "functionalCurrencyAmount", "quotedOn", "quoteSource"),
        ProtocolPostEntryFields.quotedRateFields());
    assertEquals(List.of("sourceDocuments", "approvals"), ProtocolPostEntryFields.evidenceFields());
    assertEquals(
        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
        ProtocolPostEntryFields.sourceDocumentFields());
    assertEquals(
        List.of(
            "approvalId", "approvalType", "approverId", "approverType", "decision", "approvedAt"),
        ProtocolPostEntryFields.approvalFields());
    assertEquals(
        List.of(
            "actorId", "actorType", "commandId", "idempotencyKey", "causationId", "correlationId"),
        ProtocolPostEntryFields.provenanceFields());
    assertEquals(List.of("priorPostingId", "reason"), ProtocolPostEntryFields.reversalFields());
    assertEquals(List.of("taxRegistrationId", "taxCode"), ProtocolPostEntryFields.taxFields());
    assertEquals(
        List.of("accountCode", "amount"), ProtocolPostEntryFields.settlementAdjunctFields());
    assertEquals(
        List.of("inventoryAccountCode", "costOfSalesAccountCode", "quantity"),
        ProtocolPostEntryFields.inventoryReliefFields());
    assertEquals("transactionAmount", ProtocolPostEntryFields.ForeignExchange.TRANSACTION_AMOUNT);
    assertEquals("quotedRate", ProtocolPostEntryFields.ForeignExchange.QUOTED_RATE);
    assertEquals(
        "transactionCurrencyAmount",
        ProtocolPostEntryFields.QuotedRate.TRANSACTION_CURRENCY_AMOUNT);
    assertEquals("quoteSource", ProtocolPostEntryFields.QuotedRate.QUOTE_SOURCE);
    assertEquals("accountCode", ProtocolSharedRequestFields.ACCOUNT_CODE);
    assertEquals("currencyCode", ProtocolSharedRequestFields.CURRENCY_CODE);
    assertEquals("effectiveDateFrom", ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM);
    assertEquals("effectiveDateTo", ProtocolSharedRequestFields.EFFECTIVE_DATE_TO);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolDeclareAccountFields.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolLedgerPlanFields.Query.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM,
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_TO,
        ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO);
    assertEquals(
        ProtocolSharedRequestFields.ACCOUNT_CODE, ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_FROM,
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM);
    assertEquals(
        ProtocolSharedRequestFields.EFFECTIVE_DATE_TO,
        ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO);
    assertEquals(List.of("currencyCode", "minorUnits"), ProtocolMoneyFields.fields());
    assertEquals(ProtocolSharedRequestFields.CURRENCY_CODE, ProtocolMoneyFields.CURRENCY_CODE);
    assertEquals("minorUnits", ProtocolMoneyFields.MINOR_UNITS);
    assertEquals(
        Set.of("token", "quantityScale"), ProtocolDeclareAccountFields.UnitOfMeasure.fields());
  }

  @Test
  void postingRequestFieldSetsFollowCanonicalRequestFieldOwners() {
    assertEquals(
        Set.of(
            "accountCode",
            "accountName",
            "accountType",
            "accountNodeKind",
            "parentAccountCode",
            "financialPositionLineClassification",
            "profitAndLossLineClassification",
            "cashFlowAssetClassification",
            "unitOfMeasure"),
        ProtocolBookRequestFieldSets.declareAccountFields());
    assertEquals(
        Set.of(
            "taxRegistrationId",
            "taxRegistrationName",
            "jurisdiction",
            "registrationNumber",
            "payableAccountCode",
            "recoverableAccountCode",
            "obligationFrequency",
            "dueDaysAfterPeriodEnd",
            "taxCodes"),
        ProtocolBookRequestFieldSets.declareTaxRegistrationFields());
    assertEquals(
        Set.of(
            "entityName",
            "bookTemplateId",
            "accountingBasis",
            "inventoryCosting",
            "functionalCurrency",
            "fiscalYearStart"),
        ProtocolBookRequestFieldSets.openBookFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.topLevelFields()),
        ProtocolPostingRequestFieldSets.postEntryTopLevelFields());
    assertEquals(
        Set.of("entryKind", "effectiveDate", "lines", "foreignExchange", "evidence", "provenance"),
        ProtocolPostingRequestFieldSets.journalDirectFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "revenueAccountCode",
            "amount",
            "inventoryRelief",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.saleSettledFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "receivableAccountCode",
            "revenueAccountCode",
            "amount",
            "inventoryRelief",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.saleOnCreditFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "cashAccountCode",
            "quantity",
            "unitCost",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.purchaseSettledFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "payableAccountCode",
            "quantity",
            "unitCost",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.purchaseOnCreditFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "cashAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.inventoryCapitalizationSettledFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "payableAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.inventoryCapitalizationOnCreditFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "writeDownLossAccountCode",
            "amount",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.inventoryWriteDownFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "shrinkageLossAccountCode",
            "quantity",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.inventoryShrinkageFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "inventoryAccountCode",
            "countGainAccountCode",
            "quantity",
            "unitCost",
            "evidence",
            "provenance"),
        ProtocolInventoryPostingRequestFieldSets.inventoryCountIncreaseFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "expenseAccountCode",
            "cashAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.expenseSettledFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "expenseAccountCode",
            "payableAccountCode",
            "amount",
            "foreignExchange",
            "tax",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.expenseOnCreditFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "receivableAccountCode",
            "amount",
            "settlementAdjunct",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.receiptFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "payableAccountCode",
            "cashAccountCode",
            "amount",
            "settlementAdjunct",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.paymentFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "cashAccountCode",
            "equityAccountCode",
            "amount",
            "foreignExchange",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.ownerContributionFields());
    assertEquals(
        Set.of(
            "entryKind",
            "effectiveDate",
            "equityAccountCode",
            "cashAccountCode",
            "amount",
            "foreignExchange",
            "evidence",
            "provenance"),
        ProtocolPostingRequestFieldSets.ownerWithdrawalFields());
    assertEquals(
        Set.of("entryKind", "effectiveDate", "openingBalances", "evidence", "provenance"),
        ProtocolPostingRequestFieldSets.openingPositionFields());
    assertEquals(
        Set.of(
            "entryKind", "effectiveDate", "foreignExchange", "evidence", "provenance", "reversal"),
        ProtocolPostingRequestFieldSets.reversalEntryFields());
  }

  @Test
  void nestedPostingFieldSetsFollowCanonicalRequestFieldOwners() {
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.evidenceFields()),
        ProtocolPostingNestedFieldSets.evidenceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.sourceDocumentFields()),
        ProtocolPostingNestedFieldSets.sourceDocumentFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.approvalFields()),
        ProtocolPostingNestedFieldSets.approvalFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.provenanceFields()),
        ProtocolPostingNestedFieldSets.provenanceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.journalLineFields()),
        ProtocolPostingNestedFieldSets.journalLineFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.openingBalanceFields()),
        ProtocolPostingNestedFieldSets.openingBalanceFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.reversalFields()),
        ProtocolPostingNestedFieldSets.reversalFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.taxFields()),
        ProtocolPostingNestedFieldSets.taxFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.settlementAdjunctFields()),
        ProtocolPostingNestedFieldSets.settlementAdjunctFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.inventoryReliefFields()),
        ProtocolPostingNestedFieldSets.inventoryReliefFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.foreignExchangeFields()),
        ProtocolPostingNestedFieldSets.foreignExchangeFields());
    assertEquals(
        Set.copyOf(ProtocolPostEntryFields.quotedRateFields()),
        ProtocolPostingNestedFieldSets.quotedRateFields());
  }

  @Test
  void ledgerPlanRequestFieldSetsFollowCanonicalRequestFieldOwners() {
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.planFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerPlanFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.stepFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerStepFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.queryFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerQueryFields());
    assertEquals(
        Set.of(ProtocolLedgerPlanFields.Query.LIMIT, ProtocolLedgerPlanFields.Query.CURSOR),
        ProtocolLedgerPlanRequestFieldSets.listAccountsQueryFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
            ProtocolLedgerPlanFields.Query.LIMIT,
            ProtocolLedgerPlanFields.Query.CURSOR),
        ProtocolLedgerPlanRequestFieldSets.listPostingsQueryFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO),
        ProtocolLedgerPlanRequestFieldSets.accountBalanceQueryFields());
    assertEquals(
        Set.copyOf(ProtocolLedgerPlanFields.assertionFields()),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields());
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_DECLARED));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_ACTIVE));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND, ProtocolLedgerPlanFields.Assertion.POSTING_ID),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.POSTING_EXISTS));
    assertEquals(
        Set.of(
            ProtocolLedgerPlanFields.Assertion.KIND,
            ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
            ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
            ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
            ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
            ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE),
        ProtocolLedgerPlanRequestFieldSets.ledgerAssertionFields(
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
  }
}
