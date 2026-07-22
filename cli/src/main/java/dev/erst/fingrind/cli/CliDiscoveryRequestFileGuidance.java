package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryRequestFileGuidanceJsonModels.RequestFileGuidancePayload;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Optional;
import java.util.Set;

/** Builds command-scoped discovery guidance for JSON request files. */
final class CliDiscoveryRequestFileGuidance {
  private static final Set<OperationId> POSTING_REQUEST_OPERATIONS =
      Set.of(
          OperationId.POST_ENTRY,
          OperationId.PREFLIGHT_ENTRY,
          OperationId.RECORD_SALE_SETTLED,
          OperationId.RECORD_SALE_ON_CREDIT,
          OperationId.RECORD_PURCHASE_SETTLED,
          OperationId.RECORD_PURCHASE_ON_CREDIT,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
          OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
          OperationId.RECORD_INVENTORY_WRITE_DOWN,
          OperationId.RECORD_INVENTORY_SHRINKAGE,
          OperationId.RECORD_INVENTORY_COUNT_INCREASE,
          OperationId.RECORD_PREPAYMENT,
          OperationId.RECORD_DEFERRED_REVENUE,
          OperationId.RECORD_ACCRUED_EXPENSE,
          OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
          OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
          OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
          OperationId.RECORD_EXPENSE_SETTLED,
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          OperationId.RECORD_RECEIPT,
          OperationId.RECORD_PAYMENT,
          OperationId.RECORD_OWNER_CONTRIBUTION,
          OperationId.RECORD_OWNER_WITHDRAWAL,
          OperationId.RECORD_OPENING_POSITION,
          OperationId.RECORD_REVERSAL);

  private CliDiscoveryRequestFileGuidance() {}

  static Optional<RequestFileGuidancePayload> forOperation(
      HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    if (POSTING_REQUEST_OPERATIONS.contains(operationId)) {
      return postingRequestGuidance(helpDescriptor, operationId, detail);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT || operationId == OperationId.AMEND_ACCOUNT) {
      return accountDefinitionRequestGuidance(helpDescriptor, operationId, detail);
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return declareTaxRegistrationRequestGuidance(helpDescriptor, detail);
    }
    if (operationId == OperationId.RETIRE_ACCOUNT) {
      return retireAccountRequestGuidance(helpDescriptor, detail);
    }
    if (isAttestationRegistryMutation(operationId)) {
      return attestationRegistryRequestGuidance(operationId, detail);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return ledgerPlanRequestGuidance(helpDescriptor, detail);
    }
    return Optional.empty();
  }

  private static Optional<RequestFileGuidancePayload> postingRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().bookkeepingEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide a posting JSON document through --request-file <path|->.",
            detail,
            detail == DiscoveryDetail.FULL ? helpDescriptor.requestTemplate() : null,
            null,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    helpDescriptor.requestShapes().bookkeepingEntry(),
                    null,
                    null,
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static Optional<RequestFileGuidancePayload> accountDefinitionRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide an account-definition JSON document through --request-file <path|->.",
            detail,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.declareAccountTemplate() : null,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    helpDescriptor.requestShapes().declareAccount(),
                    null,
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static Optional<RequestFileGuidancePayload> declareTaxRegistrationRequestGuidance(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareTaxRegistration() == null
        || helpDescriptor.declareTaxRegistrationTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide a tax-registration declaration JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.declareTaxRegistrationTemplate() : null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    null,
                    null,
                    helpDescriptor.requestShapes().declareTaxRegistration(),
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_TAX_REGISTRATION.wireName()));
  }

  private static Optional<RequestFileGuidancePayload> ledgerPlanRequestGuidance(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide a ledger plan JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.planTemplate() : null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    null,
                    null,
                    null,
                    helpDescriptor.requestShapes().ledgerPlan())
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
  }

  private static Optional<RequestFileGuidancePayload> retireAccountRequestGuidance(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().retireAccount() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide an account-retirement JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    null,
                    helpDescriptor.requestShapes().retireAccount(),
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.RETIRE_ACCOUNT.wireName()));
  }

  private static Optional<RequestFileGuidancePayload> attestationRegistryRequestGuidance(
      OperationId operationId, DiscoveryDetail detail) {
    return Optional.of(
        new RequestFileGuidancePayload(
            "Provide an attestation credential or policy JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? dev.erst.fingrind.contract.discovery.MachineContract.attestationRegistryTemplate(
                    operationId)
                : null,
            null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static boolean isAttestationRegistryMutation(OperationId operationId) {
    return operationId == OperationId.ENROLL_KEY
        || operationId == OperationId.ROLLOVER_KEY
        || operationId == OperationId.REVOKE_KEY
        || operationId == OperationId.ALTER_POLICY;
  }
}
