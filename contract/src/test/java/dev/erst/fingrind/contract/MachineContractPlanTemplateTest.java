package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for machine-contract ledger-plan template publication. */
class MachineContractPlanTemplateTest {
  @Test
  void planTemplatePublishesCanonicalAgentWorkflowMetadata() {
    ContractTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();
    ContractTemplates.LedgerPlanStepTemplateDescriptor initializeBook = template.steps().get(0);
    ContractTemplates.OpenBookTemplateDescriptor initializeBookTemplate = initializeBook.openBook();
    ContractTemplates.LedgerPlanStepTemplateDescriptor postJournal = template.steps().get(1);
    ContractTemplates.PostingRequestTemplateDescriptor postJournalTemplate = postJournal.posting();
    ContractTemplates.LedgerPlanStepTemplateDescriptor assertCashBalance = template.steps().get(2);
    ContractTemplates.LedgerAssertionTemplateDescriptor assertCashBalanceTemplate =
        assertCashBalance.assertion();
    assertNotNull(initializeBookTemplate);
    assertNotNull(postJournalTemplate);
    assertNotNull(assertCashBalanceTemplate);
    assertEquals("plan-1", template.planId());
    assertEquals(3, template.steps().size());
    assertEquals("initialize-book", initializeBook.stepId());
    assertEquals(LedgerStepKind.OPEN_BOOK, initializeBook.kind());
    assertEquals("Acme Studio", initializeBookTemplate.entityName());
    assertEquals("EUR", initializeBookTemplate.functionalCurrency());
    assertEquals("01-01", initializeBookTemplate.fiscalYearStart());
    assertEquals("post-journal", postJournal.stepId());
    assertEquals(LedgerStepKind.POST_ENTRY, postJournal.kind());
    assertEquals("2026-01-15", postJournalTemplate.effectiveDate());
    assertEquals(
        dev.erst.fingrind.core.BookkeepingEntryKind.CASH_REVENUE, postJournalTemplate.entryKind());
    assertEquals("cash", postJournalTemplate.cashAccountCode());
    assertEquals("service-revenue", postJournalTemplate.revenueAccountCode());
    assertEquals("replace-with-actor-id", postJournalTemplate.provenance().actorId());
    assertEquals(ActorType.PERSON, postJournalTemplate.provenance().actorType());
    assertEquals("assert-cash-balance", assertCashBalance.stepId());
    assertEquals(LedgerStepKind.ASSERT, assertCashBalance.kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, assertCashBalanceTemplate.kind());
    assertEquals("cash", assertCashBalanceTemplate.accountCode());
    assertEquals(new MonetaryAmount("EUR", "1000"), assertCashBalanceTemplate.netAmount());
    assertEquals(BalanceSide.DEBIT, assertCashBalanceTemplate.balanceSide());
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.52.0", "test"));
    assertEquals(PlanTransactionMode.ATOMIC, capabilities.planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE, capabilities.planExecution().failurePolicy());
    assertNotNull(capabilities.requestShapes());
    var requestShapes = Objects.requireNonNull(capabilities.requestShapes());
    assertNotNull(requestShapes.ledgerPlan());
    var ledgerPlan = Objects.requireNonNull(requestShapes.ledgerPlan());
    assertTrue(ledgerPlan.assertionKinds().contains(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
    assertEquals(LedgerStepKind.ASSERT, ledgerPlan.assertStepKind());
  }
}
