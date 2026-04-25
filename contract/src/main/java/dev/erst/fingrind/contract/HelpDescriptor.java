package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the help payload. */
public record HelpDescriptor(
    String application,
    String version,
    String description,
    List<String> usage,
    ContractResponse.BookModelDescriptor bookModel,
    List<CommandDescriptor> commands,
    List<String> quickStart,
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
    commands = ContractDescriptorValidation.copyList(commands, "commands");
    quickStart = ContractDescriptorValidation.copyList(quickStart, "quickStart");
    exitCodes = ContractDescriptorValidation.copyList(exitCodes, "exitCodes");
    preflight = ContractDescriptorValidation.requireValue(preflight, "preflight");
    currencyModel = ContractDescriptorValidation.requireValue(currencyModel, "currencyModel");
    environment = ContractDescriptorValidation.requireValue(environment, "environment");
  }
}
