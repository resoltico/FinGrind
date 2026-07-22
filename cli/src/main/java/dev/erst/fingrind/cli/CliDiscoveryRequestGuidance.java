package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolAttestationRegistryRequestFields;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds request and starter-template guidance from the canonical discovery contract. */
final class CliDiscoveryRequestGuidance {
  private static final Map<OperationId, String> ATTESTATION_REGISTRY_FIELD_GUIDANCE =
      Map.of(
          OperationId.ENROLL_KEY,
          "Required fields: "
              + String.join(
                  ", ",
                  List.of(
                      ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID,
                      ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI,
                      ProtocolAttestationRegistryRequestFields.CREDENTIAL_PURPOSE))
              + ". credentialPurpose is one of: operator, system.",
          OperationId.ROLLOVER_KEY,
          "Required fields: "
              + String.join(
                  ", ",
                  List.of(
                      ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID,
                      ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI,
                      ProtocolAttestationRegistryRequestFields.CREDENTIAL_PURPOSE,
                      ProtocolAttestationRegistryRequestFields.PREDECESSOR_CREDENTIAL_SPKI))
              + ". credentialPurpose is one of: operator, system.",
          OperationId.REVOKE_KEY,
          "Required fields: "
              + String.join(
                  ", ",
                  List.of(
                      ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID,
                      ProtocolAttestationRegistryRequestFields.CREDENTIAL_SPKI,
                      ProtocolAttestationRegistryRequestFields.REASON))
              + ". reason is optional.",
          OperationId.ALTER_POLICY,
          "Supply one or more of policyRules, capabilityGrants, or systemWorkflowPolicies; "
              + "each array uses only the fields shown by the request template.");

  private CliDiscoveryRequestGuidance() {}

  static String render(HelpDescriptor helpDescriptor, OperationId operationId) {
    if (CliDiscoveryOperationFamilies.isEntryRequest(operationId)) {
      return renderPostingRequestGuidance(helpDescriptor, operationId);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT || operationId == OperationId.AMEND_ACCOUNT) {
      return renderAccountDefinitionRequestGuidance(helpDescriptor, operationId);
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return renderDeclareTaxRegistrationRequestGuidance(helpDescriptor);
    }
    if (operationId == OperationId.RETIRE_ACCOUNT) {
      return renderRetireAccountRequestGuidance(helpDescriptor);
    }
    if (isAttestationRegistryMutation(operationId)) {
      return renderAttestationRegistryRequestGuidance(operationId);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return renderLedgerPlanRequestGuidance(helpDescriptor);
    }
    return "";
  }

  static CliDiscoveryCommandHelpSupport.SupportEntry requestTemplateHint(OperationId operationId) {
    if (CliDiscoveryOperationFamilies.isEntryRequest(operationId)
        || operationId == OperationId.DECLARE_ACCOUNT
        || operationId == OperationId.AMEND_ACCOUNT
        || operationId == OperationId.RETIRE_ACCOUNT
        || isAttestationRegistryMutation(operationId)) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template",
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName());
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template",
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + OperationId.DECLARE_TAX_REGISTRATION.wireName());
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return CliDiscoveryCommandHelpSupport.SupportEntry.command(
          "Request template", CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE));
    }
    return CliDiscoveryCommandHelpSupport.SupportEntry.note("Request template", "(not applicable)");
  }

  private static String renderPostingRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().bookkeepingEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return "";
    }
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        helpDescriptor.requestShapes().bookkeepingEntry();
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor requestTemplate =
        helpDescriptor.requestTemplate();
    return CliDiscoveryTextSupport.joinSections(
        CliDiscoveryTextSupport.section(
            "Input Contract",
            requestFileGuidance(
                "Pass a JSON object through --request-file <path|->.",
                CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                    + " "
                    + operationId.wireName())),
        CliDiscoveryTextSupport.section(
            "Posting model",
            CliDiscoveryPostingModelGuidance.renderPostingModel(postEntryShape, requestTemplate)),
        CliDiscoveryTextSupport.section(
            "Entry semantics",
            CliDiscoveryPostingModelGuidance.renderEntrySemantics(
                postEntryShape, requestTemplate)));
  }

  private static String renderAccountDefinitionRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static String renderDeclareTaxRegistrationRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareTaxRegistration() == null
        || helpDescriptor.declareTaxRegistrationTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_TAX_REGISTRATION.wireName()));
  }

  private static String renderRetireAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().retireAccount() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object containing the declared accountCode through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.RETIRE_ACCOUNT.wireName()));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.joinSections(
        CliDiscoveryTextSupport.section(
            "Input Contract",
            requestFileGuidance(
                "Pass a ledger plan JSON object through --request-file <path|->.",
                CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE))),
        CliDiscoveryTextSupport.section(
            "Plan structure",
            CliDiscoveryPlanStructureGuidance.render(helpDescriptor.requestShapes().ledgerPlan())),
        CliDiscoveryTextSupport.section(
            "Starter plan",
            CliDiscoveryPlanTemplateGuidance.render(helpDescriptor.planTemplate())));
  }

  private static String renderAttestationRegistryRequestGuidance(OperationId operationId) {
    return CliDiscoveryTextSupport.section(
        "Input Contract",
        requestFileGuidance(
            "Pass a JSON object through --request-file <path|->. "
                + attestationRegistryFieldGuidance(operationId),
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static String attestationRegistryFieldGuidance(OperationId operationId) {
    return Objects.requireNonNull(
        ATTESTATION_REGISTRY_FIELD_GUIDANCE.get(operationId), "attestation registry operationId");
  }

  private static boolean isAttestationRegistryMutation(OperationId operationId) {
    return ATTESTATION_REGISTRY_FIELD_GUIDANCE.containsKey(operationId);
  }

  private static String requestFileGuidance(String introduction, String shortcutCommand) {
    return String.join(
        System.lineSeparator() + System.lineSeparator(),
        CliTextFormat.wrap(introduction, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH),
        "Starter file command"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
  }
}
