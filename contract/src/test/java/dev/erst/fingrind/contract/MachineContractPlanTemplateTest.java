package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for machine-contract ledger-plan template publication. */
class MachineContractPlanTemplateTest {
  @Test
  void planTemplatePublishesCanonicalAgentWorkflowMetadata() {
    ContractTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();
    ContractTemplates.LedgerPlanStepTemplateDescriptor declareCash = template.steps().get(1);
    ContractTemplates.DeclareAccountTemplateDescriptor declareCashTemplate =
        declareCash.declareAccount();
    ContractTemplates.LedgerPlanStepTemplateDescriptor declareRevenue = template.steps().get(2);
    ContractTemplates.DeclareAccountTemplateDescriptor declareRevenueTemplate =
        declareRevenue.declareAccount();
    ContractTemplates.LedgerPlanStepTemplateDescriptor postJournal = template.steps().get(3);
    ContractTemplates.PostingRequestTemplateDescriptor postJournalTemplate = postJournal.posting();
    ContractTemplates.LedgerPlanStepTemplateDescriptor assertCashBalance = template.steps().get(4);
    ContractTemplates.LedgerAssertionTemplateDescriptor assertCashBalanceTemplate =
        assertCashBalance.assertion();
    assertNotNull(declareCashTemplate);
    assertNotNull(declareRevenueTemplate);
    assertNotNull(postJournalTemplate);
    assertNotNull(assertCashBalanceTemplate);
    assertEquals("plan-1", template.planId());
    assertEquals(5, template.steps().size());
    assertEquals("initialize-book", template.steps().get(0).stepId());
    assertEquals(LedgerStepKind.OPEN_BOOK, template.steps().get(0).kind());
    assertEquals("declare-cash", declareCash.stepId());
    assertEquals(LedgerStepKind.DECLARE_ACCOUNT, declareCash.kind());
    assertEquals("1000", declareCashTemplate.accountCode());
    assertEquals("Cash", declareCashTemplate.accountName());
    assertEquals(NormalBalance.DEBIT, declareCashTemplate.normalBalance());
    assertEquals("declare-revenue", declareRevenue.stepId());
    assertEquals("2000", declareRevenueTemplate.accountCode());
    assertEquals(NormalBalance.CREDIT, declareRevenueTemplate.normalBalance());
    assertEquals("post-journal", postJournal.stepId());
    assertEquals(LedgerStepKind.POST_ENTRY, postJournal.kind());
    assertEquals(ScaffoldPlaceholders.EFFECTIVE_DATE, postJournalTemplate.effectiveDate());
    assertEquals("1000", postJournalTemplate.lines().get(0).accountCode());
    assertEquals("2000", postJournalTemplate.lines().get(1).accountCode());
    assertEquals(ScaffoldPlaceholders.ACTOR_ID, postJournalTemplate.provenance().actorId());
    assertEquals(ActorType.AGENT, postJournalTemplate.provenance().actorType());
    assertEquals("assert-cash-balance", assertCashBalance.stepId());
    assertEquals(LedgerStepKind.ASSERT, assertCashBalance.kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, assertCashBalanceTemplate.kind());
    assertEquals("1000", assertCashBalanceTemplate.accountCode());
    assertEquals("EUR", assertCashBalanceTemplate.currencyCode());
    assertEquals("10.00", assertCashBalanceTemplate.netAmount());
    assertEquals(BalanceSide.DEBIT, assertCashBalanceTemplate.balanceSide());
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ApplicationIdentity("FinGrind", "0.32.0", "test"),
            ContractFixtures.environmentDescriptor(),
            Instant.parse("2026-04-17T09:10:11Z"));
    assertEquals(PlanTransactionMode.ATOMIC, capabilities.planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE, capabilities.planExecution().failurePolicy());
    assertTrue(
        capabilities
            .requestShapes()
            .ledgerPlan()
            .assertionKinds()
            .contains(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
    assertEquals(LedgerStepKind.ASSERT, capabilities.requestShapes().ledgerPlan().assertStepKind());
  }
}
