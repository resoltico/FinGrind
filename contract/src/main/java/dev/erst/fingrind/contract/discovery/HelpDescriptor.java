package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;

/** Descriptor for the help payload. */
public record HelpDescriptor(
    String application,
    String version,
    String description,
    List<String> usage,
    ContractResponse.BookModelDescriptor bookModel,
    ContractRequestShapes.RequestShapesDescriptor requestShapes,
    ContractTemplates.PostingRequestTemplateDescriptor requestTemplate,
    ContractTemplates.DeclareAccountTemplateDescriptor declareAccountTemplate,
    ContractTemplates.LedgerPlanTemplateDescriptor planTemplate,
    List<CommandDescriptor> commands,
    List<WorkflowDescriptor> quickStart,
    List<ExitCodeDescriptor> exitCodes,
    ContractResponse.PreflightDescriptor preflight,
    ContractResponse.CurrencyDescriptor currencyModel,
    EnvironmentDescriptor environment)
    implements ContractDiscoveryDescriptor {
  /** Validates one help descriptor payload. */
  public HelpDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    description = ContractDescriptorValidation.requireText(description, "description");
    usage = ContractDescriptorValidation.copyList(usage, "usage");
    bookModel = ContractDescriptorValidation.requireValue(bookModel, "bookModel");
    requestShapes = ContractDescriptorValidation.requireValue(requestShapes, "requestShapes");
    requestTemplate = ContractDescriptorValidation.requireValue(requestTemplate, "requestTemplate");
    declareAccountTemplate =
        ContractDescriptorValidation.requireValue(declareAccountTemplate, "declareAccountTemplate");
    planTemplate = ContractDescriptorValidation.requireValue(planTemplate, "planTemplate");
    commands = ContractDescriptorValidation.copyList(commands, "commands");
    quickStart = ContractDescriptorValidation.copyList(quickStart, "quickStart");
    exitCodes = ContractDescriptorValidation.copyList(exitCodes, "exitCodes");
    preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
    currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
    environment = ContractDescriptorValidation.requireValue(environment, "environment");
  }
}
