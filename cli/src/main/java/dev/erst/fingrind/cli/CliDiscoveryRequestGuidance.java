package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Builds request and starter-template guidance from the canonical discovery contract. */
final class CliDiscoveryRequestGuidance {
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
    if (operationId == OperationId.EXECUTE_PLAN) {
      return renderLedgerPlanRequestGuidance(helpDescriptor);
    }
    return "";
  }

  static CliDiscoveryCommandHelpSupport.SupportEntry requestTemplateHint(OperationId operationId) {
    if (CliDiscoveryOperationFamilies.isEntryRequest(operationId)
        || operationId == OperationId.DECLARE_ACCOUNT
        || operationId == OperationId.AMEND_ACCOUNT) {
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
    ContractTemplates.PostingRequestTemplateDescriptor requestTemplate =
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

  private static String requestFileGuidance(String introduction, String shortcutCommand) {
    return String.join(
        System.lineSeparator() + System.lineSeparator(),
        CliTextFormat.wrap(introduction, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH),
        "Starter file command"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
  }
}
