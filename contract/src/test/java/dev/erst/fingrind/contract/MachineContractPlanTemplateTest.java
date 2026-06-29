package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
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
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor initializeBook =
        onlyStepOfKind(template, LedgerStepKind.ENSURE_BOOK);
    ContractPlanTemplates.EnsureBookTemplateDescriptor initializeBookTemplate =
        initializeBook.ensureBook();
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor recordSale =
        template.canonicalPostingScaffoldStep();
    ContractTemplates.PostingRequestTemplateDescriptor recordSaleTemplate = recordSale.posting();
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor assertCashBalance =
        onlyStepOfKind(template, LedgerStepKind.ASSERT);
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor assertCashBalanceTemplate =
        assertCashBalance.assertion();
    assertNotNull(initializeBookTemplate);
    assertNotNull(recordSaleTemplate);
    assertNotNull(assertCashBalanceTemplate);
    assertEquals("plan-1", template.planId());
    assertEquals(3, template.steps().size());
    assertEquals("ensure-book", initializeBook.stepId());
    assertEquals(LedgerStepKind.ENSURE_BOOK, initializeBook.kind());
    assertEquals("Acme Studio", initializeBookTemplate.entityName());
    assertEquals("EUR", initializeBookTemplate.functionalCurrency());
    assertEquals("01-01", initializeBookTemplate.fiscalYearStart());
    assertEquals("record-sale", recordSale.stepId());
    assertEquals(LedgerStepKind.RECORD_SALE, recordSale.kind());
    assertEquals("2026-01-15", recordSaleTemplate.effectiveDate());
    assertEquals(dev.erst.fingrind.core.BookkeepingEntryKind.SALE, recordSaleTemplate.entryKind());
    assertEquals("cash", recordSaleTemplate.cashAccountCode());
    assertEquals("service-revenue", recordSaleTemplate.revenueAccountCode());
    assertNull(recordSaleTemplate.expenseAccountCode());
    assertNull(recordSaleTemplate.equityAccountCode());
    assertNull(recordSaleTemplate.lines());
    assertEquals(new MonetaryAmount("EUR", "1000"), recordSaleTemplate.amount());
    assertEquals(ScaffoldPlaceholders.ACTOR_ID, recordSaleTemplate.provenance().actorId());
    assertEquals(ActorType.PERSON, recordSaleTemplate.provenance().actorType());
    assertEquals("assert-cash-balance", assertCashBalance.stepId());
    assertEquals(LedgerStepKind.ASSERT, assertCashBalance.kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, assertCashBalanceTemplate.kind());
    assertEquals("cash", assertCashBalanceTemplate.accountCode());
    assertEquals(new MonetaryAmount("EUR", "1000"), assertCashBalanceTemplate.netAmount());
    assertEquals(BalanceSide.DEBIT, assertCashBalanceTemplate.balanceSide());
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.57.0", "test"));
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

  @Test
  void planTemplateExposesCanonicalPostingTemplateThroughSemanticSelection() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor postingStep =
        template.canonicalPostingScaffoldStep();

    assertEquals("record-sale", postingStep.stepId());
    assertSame(postingStep.posting(), template.canonicalPostingTemplate());
  }

  @Test
  void canonicalPostingScaffoldStepRejectsMissingPostingStep() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor baseTemplate =
        MachineContract.planTemplate();
    ContractPlanTemplates.LedgerPlanTemplateDescriptor withoutPostingStep =
        new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
            baseTemplate.planId(),
            baseTemplate.steps().stream().filter(step -> !step.kind().commitsPosting()).toList());

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, withoutPostingStep::canonicalPostingScaffoldStep);

    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("canonical committed-posting scaffold"),
        failure::getMessage);
  }

  @Test
  void canonicalPostingScaffoldStepRejectsAmbiguousPostingSteps() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor baseTemplate =
        MachineContract.planTemplate();
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor canonicalPostingStep =
        baseTemplate.canonicalPostingScaffoldStep();
    ContractPlanTemplates.LedgerPlanTemplateDescriptor duplicatePostingSteps =
        new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
            baseTemplate.planId(),
            java.util.stream.Stream.concat(
                    baseTemplate.steps().stream(), java.util.stream.Stream.of(canonicalPostingStep))
                .toList());

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class, duplicatePostingSteps::canonicalPostingScaffoldStep);

    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("canonical committed-posting scaffold"),
        failure::getMessage);
  }

  private static ContractPlanTemplates.LedgerPlanStepTemplateDescriptor onlyStepOfKind(
      ContractPlanTemplates.LedgerPlanTemplateDescriptor template, LedgerStepKind kind) {
    return template.steps().stream()
        .filter(step -> step.kind() == kind)
        .reduce(
            (first, second) -> {
              throw new IllegalStateException(
                  "Expected exactly one %s scaffold step.".formatted(kind));
            })
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Expected exactly one %s scaffold step.".formatted(kind)));
  }
}
