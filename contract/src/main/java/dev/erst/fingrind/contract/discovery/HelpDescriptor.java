package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPlanTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareTaxRegistrationTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Descriptor for the help payload. */
public record HelpDescriptor(
    String application,
    String version,
    String protocolVersion,
    String description,
    List<String> usage,
    ContractResponse.BookModelDescriptor bookModel,
    ContractResponse.BookkeepingKernelDescriptor bookkeepingKernel,
    @Nullable RequestShapesDescriptor requestShapes,
    @Nullable PostingRequestTemplateDescriptor requestTemplate,
    @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate,
    @Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistrationTemplate,
    @Nullable LedgerPlanTemplateDescriptor planTemplate,
    List<CommandDescriptor> commands,
    List<WorkflowDescriptor> quickStart,
    List<ExitCodeDescriptor> exitCodes,
    ContractResponse.PreflightDescriptor preflight,
    ContractResponse.CurrencyDescriptor currencyModel)
    implements ContractDiscoveryDescriptor {
  /** Validates one help descriptor payload. */
  public HelpDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    protocolVersion = ContractDescriptorValidation.requireText(protocolVersion, "protocolVersion");
    description = ContractDescriptorValidation.requireText(description, "description");
    usage = ContractDescriptorValidation.copyList(usage, "usage");
    bookModel = ContractDescriptorValidation.requireValue(bookModel, "bookModel");
    bookkeepingKernel =
        ContractDescriptorValidation.requireValue(bookkeepingKernel, "bookkeepingKernel");
    commands = ContractDescriptorValidation.copyList(commands, "commands");
    quickStart = ContractDescriptorValidation.copyList(quickStart, "quickStart");
    exitCodes = ContractDescriptorValidation.copyList(exitCodes, "exitCodes");
    preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
    currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
  }
}
