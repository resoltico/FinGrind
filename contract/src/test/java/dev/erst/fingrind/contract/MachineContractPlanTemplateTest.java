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
import dev.erst.fingrind.core.JournalLine;
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
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor postJournal =
        template.canonicalPostingScaffoldStep();
    ContractTemplates.PostingRequestTemplateDescriptor postJournalTemplate = postJournal.posting();
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor assertCashBalance =
        onlyStepOfKind(template, LedgerStepKind.ASSERT);
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor assertCashBalanceTemplate =
        assertCashBalance.assertion();
    assertNotNull(initializeBookTemplate);
    assertNotNull(postJournalTemplate);
    assertNotNull(assertCashBalanceTemplate);
    assertEquals("plan-1", template.planId());
    assertEquals(3, template.steps().size());
    assertEquals("ensure-book", initializeBook.stepId());
    assertEquals(LedgerStepKind.ENSURE_BOOK, initializeBook.kind());
    assertEquals("Acme Studio", initializeBookTemplate.entityName());
    assertEquals("EUR", initializeBookTemplate.functionalCurrency());
    assertEquals("01-01", initializeBookTemplate.fiscalYearStart());
    assertEquals("post-journal", postJournal.stepId());
    assertEquals(LedgerStepKind.POST_ENTRY, postJournal.kind());
    assertEquals("2026-01-15", postJournalTemplate.effectiveDate());
    assertEquals(
        dev.erst.fingrind.core.BookkeepingEntryKind.JOURNAL, postJournalTemplate.entryKind());
    assertNull(postJournalTemplate.recipeKind());
    assertNull(postJournalTemplate.cashAccountCode());
    assertNull(postJournalTemplate.revenueAccountCode());
    assertEquals(2, Objects.requireNonNull(postJournalTemplate.lines()).size());
    assertEquals("cash", postJournalTemplate.lines().getFirst().accountCode());
    assertEquals(JournalLine.EntrySide.DEBIT, postJournalTemplate.lines().getFirst().side());
    assertEquals(
        new MonetaryAmount("EUR", "1000"), postJournalTemplate.lines().getFirst().amount());
    assertEquals("service-revenue", postJournalTemplate.lines().get(1).accountCode());
    assertEquals(JournalLine.EntrySide.CREDIT, postJournalTemplate.lines().get(1).side());
    assertEquals(ScaffoldPlaceholders.ACTOR_ID, postJournalTemplate.provenance().actorId());
    assertEquals(ActorType.PERSON, postJournalTemplate.provenance().actorType());
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

    assertEquals("post-journal", postingStep.stepId());
    assertSame(postingStep.posting(), template.canonicalPostingTemplate());
  }

  @Test
  void canonicalPostingScaffoldStepRejectsMissingPostingStep() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor baseTemplate =
        MachineContract.planTemplate();
    ContractPlanTemplates.LedgerPlanTemplateDescriptor withoutPostingStep =
        new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
            baseTemplate.planId(),
            baseTemplate.steps().stream()
                .filter(step -> step.kind() != LedgerStepKind.POST_ENTRY)
                .toList());

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, withoutPostingStep::canonicalPostingScaffoldStep);

    assertTrue(
        Objects.requireNonNull(failure.getMessage()).contains("canonical POST_ENTRY scaffold"),
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
        Objects.requireNonNull(failure.getMessage()).contains("canonical POST_ENTRY scaffold"),
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
