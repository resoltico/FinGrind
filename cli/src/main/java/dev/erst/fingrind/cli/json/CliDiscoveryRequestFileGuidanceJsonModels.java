package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.ContractPlanTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareTaxRegistrationTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.TemplateDescriptorType;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import org.jspecify.annotations.Nullable;

/** JSON record family for request-file guidance in command-help payloads. */
public interface CliDiscoveryRequestFileGuidanceJsonModels {
  record RequestFileGuidancePayload(
      String description,
      DiscoveryDetail detail,
      @Nullable PostingRequestTemplateDescriptor postingTemplate,
      @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate,
      @Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistrationTemplate,
      @Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplate,
      @Nullable TemplateDescriptorType attestationTemplate,
      @Nullable RequestShapesDescriptor requestShapes,
      @Nullable String shortcutCommand)
      implements ProtocolSuccessPayload {
    public RequestFileGuidancePayload {
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      shortcutCommand = requireOptionalText(shortcutCommand, "shortcutCommand");
      if (postingTemplate == null
          && declareAccountTemplate == null
          && declareTaxRegistrationTemplate == null
          && ledgerPlanTemplate == null
          && attestationTemplate == null
          && requestShapes == null
          && shortcutCommand == null) {
        throw new IllegalArgumentException(
            "At least one request-file guidance artifact must be present.");
      }
    }
  }
}
