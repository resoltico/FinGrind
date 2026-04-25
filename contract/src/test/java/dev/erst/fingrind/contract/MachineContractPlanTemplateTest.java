package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for machine-contract ledger-plan template publication. */
@NullUnmarked
class MachineContractPlanTemplateTest {
  @Test
  void planTemplatePublishesCanonicalAgentWorkflowMetadata() {
    ContractTemplates.LedgerPlanTemplateDescriptor template =
        MachineContract.planTemplate(
            Clock.fixed(Instant.parse("2026-04-17T09:10:11Z"), ZoneOffset.UTC));

    assertEquals("plan-1", template.planId());
    assertEquals(5, template.steps().size());
    assertEquals("initialize-book", template.steps().get(0).stepId());
    assertEquals(LedgerStepKind.OPEN_BOOK, template.steps().get(0).kind());
    assertEquals("declare-cash", template.steps().get(1).stepId());
    assertEquals(LedgerStepKind.DECLARE_ACCOUNT, template.steps().get(1).kind());
    assertEquals("1000", template.steps().get(1).declareAccount().accountCode());
    assertEquals("Cash", template.steps().get(1).declareAccount().accountName());
    assertEquals(NormalBalance.DEBIT, template.steps().get(1).declareAccount().normalBalance());
    assertEquals("declare-revenue", template.steps().get(2).stepId());
    assertEquals("2000", template.steps().get(2).declareAccount().accountCode());
    assertEquals(NormalBalance.CREDIT, template.steps().get(2).declareAccount().normalBalance());
    assertEquals("post-journal", template.steps().get(3).stepId());
    assertEquals(LedgerStepKind.POST_ENTRY, template.steps().get(3).kind());
    assertEquals("2026-04-17", template.steps().get(3).posting().effectiveDate());
    assertEquals("1000", template.steps().get(3).posting().lines().get(0).accountCode());
    assertEquals("2000", template.steps().get(3).posting().lines().get(1).accountCode());
    assertEquals("operator-1", template.steps().get(3).posting().provenance().actorId());
    assertEquals("assert-cash-balance", template.steps().get(4).stepId());
    assertEquals(LedgerStepKind.ASSERT, template.steps().get(4).kind());
    assertEquals(
        LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, template.steps().get(4).assertion().kind());
    assertEquals("1000", template.steps().get(4).assertion().accountCode());
    assertEquals("EUR", template.steps().get(4).assertion().currencyCode());
    assertEquals("10.00", template.steps().get(4).assertion().netAmount());
    assertEquals(BalanceSide.DEBIT, template.steps().get(4).assertion().balanceSide());

    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(
            new ApplicationIdentity("FinGrind", "0.25.0", "test"),
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
