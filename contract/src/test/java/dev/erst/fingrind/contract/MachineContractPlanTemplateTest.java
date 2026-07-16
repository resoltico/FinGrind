package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for machine-contract tax-setup plan publication. */
class MachineContractPlanTemplateTest {
  @Test
  void planTemplatePublishesAtomicTaxSetupInDependencyOrder() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();

    assertEquals("tax-setup", template.planId());
    assertEquals(
        List.of(
            LedgerStepKind.ENSURE_BOOK,
            LedgerStepKind.DECLARE_ACCOUNT,
            LedgerStepKind.DECLARE_ACCOUNT,
            LedgerStepKind.DECLARE_TAX_REGISTRATION),
        template.steps().stream()
            .map(ContractPlanTemplates.LedgerPlanStepTemplateDescriptor::kind)
            .toList());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor ensureBook = template.steps().getFirst();
    assertEquals("ensure-book", ensureBook.stepId());
    assertEquals(
        "OWNER_MANAGED_SERVICE", Objects.requireNonNull(ensureBook.ensureBook()).bookTemplateId());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor payable = template.steps().get(1);
    assertEquals("declare-tax-payable", payable.stepId());
    assertEquals("tax-payable-vat", Objects.requireNonNull(payable.declareAccount()).accountCode());
    assertEquals(
        FinancialPositionLineClassification.CURRENT_LIABILITY,
        payable.declareAccount().financialPositionLineClassification());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor recoverable = template.steps().get(2);
    assertEquals("declare-tax-recoverable", recoverable.stepId());
    assertEquals(
        "tax-recoverable-vat", Objects.requireNonNull(recoverable.declareAccount()).accountCode());
    assertEquals(
        FinancialPositionLineClassification.CURRENT_ASSET,
        recoverable.declareAccount().financialPositionLineClassification());
    assertEquals(
        CashFlowAssetClassification.NON_CASH,
        recoverable.declareAccount().cashFlowAssetClassification());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor registration =
        template.steps().getLast();
    assertEquals("declare-tax-registration", registration.stepId());
    assertNotNull(registration.declareTaxRegistration());
    assertEquals("tax-payable-vat", registration.declareTaxRegistration().payableAccountCode());
    assertEquals(
        "tax-recoverable-vat", registration.declareTaxRegistration().recoverableAccountCode());
  }

  @Test
  void planTemplateDoesNotInventARequiredPostingScaffold() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();

    assertFalse(template.steps().stream().anyMatch(step -> step.posting() != null));
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.57.0", "test"));
    assertEquals(PlanTransactionMode.ATOMIC, capabilities.planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE, capabilities.planExecution().failurePolicy());
    assertNotNull(capabilities.requestShapes());
    var ledgerPlan = Objects.requireNonNull(capabilities.requestShapes()).ledgerPlan();
    assertNotNull(ledgerPlan);
    assertTrue(ledgerPlan.assertionKinds().contains(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
    assertEquals(LedgerStepKind.ASSERT, ledgerPlan.assertStepKind());
  }
}
