package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareTaxRegistrationTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared discovery JSON record families reused across help and capabilities surfaces. */
public interface CliDiscoveryCommonJsonModels {

  record CommandNamePayload(OperationId name, String category) implements ProtocolSuccessPayload {
    public CommandNamePayload {
      name = requireValue(name, "name");
      category = requireText(category, "category");
    }
  }

  record CommandIndexPayload(OperationId name, String category, String summary)
      implements ProtocolSuccessPayload {
    public CommandIndexPayload {
      name = requireValue(name, "name");
      category = requireText(category, "category");
      summary = requireText(summary, "summary");
    }
  }

  record CommandSurfacePayload(
      OperationId name,
      String category,
      String summary,
      List<String> aliases,
      List<String> options,
      String executionMode,
      List<String> outputModes,
      @Nullable SelectableOutputDefaultsDescriptor selectableOutputDefaults,
      List<String> artifactOutputs,
      boolean requestFileCommand)
      implements ProtocolSuccessPayload {
    public CommandSurfacePayload {
      name = requireValue(name, "name");
      category = requireText(category, "category");
      summary = requireText(summary, "summary");
      aliases = copyList(aliases, "aliases");
      options = copyList(options, "options");
      executionMode = requireText(executionMode, "executionMode");
      outputModes = copyList(outputModes, "outputModes");
      artifactOutputs = copyList(artifactOutputs, "artifactOutputs");
    }
  }

  record RequestInputCompactPayload(
      String bookFileOption,
      List<String> bookPassphraseOptions,
      String requestFileOption,
      List<String> requestFileCommands,
      String stdinToken,
      String outputOption)
      implements ProtocolSuccessPayload {
    public RequestInputCompactPayload {
      bookFileOption = requireText(bookFileOption, "bookFileOption");
      bookPassphraseOptions = copyList(bookPassphraseOptions, "bookPassphraseOptions");
      requestFileOption = requireText(requestFileOption, "requestFileOption");
      requestFileCommands = copyList(requestFileCommands, "requestFileCommands");
      stdinToken = requireText(stdinToken, "stdinToken");
      outputOption = requireText(outputOption, "outputOption");
    }
  }

  record CommandCountPayload(String category, int count) implements ProtocolSuccessPayload {
    public CommandCountPayload {
      category = requireText(category, "category");
      if (count < 0) {
        throw new IllegalArgumentException("count must not be negative.");
      }
    }
  }

  record RequestFileGuidancePayload(
      String description,
      DiscoveryDetail detail,
      @Nullable PostingRequestTemplateDescriptor postingTemplate,
      @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate,
      @Nullable DeclareTaxRegistrationTemplateDescriptor declareTaxRegistrationTemplate,
      @Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplate,
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
          && requestShapes == null
          && shortcutCommand == null) {
        throw new IllegalArgumentException(
            "At least one request-file guidance artifact must be present.");
      }
    }
  }

  record CapabilitiesRequestInputSlicePayload(
      RequestInputCompactPayload requestInput,
      dev.erst.fingrind.contract.discovery.ContractRequestShapes.@Nullable RequestInputDescriptor
          fullRequestInput)
      implements ProtocolSuccessPayload {
    public CapabilitiesRequestInputSlicePayload {
      requestInput = requireValue(requestInput, "requestInput");
    }
  }

  record CapabilitiesCommandsSlicePayload(
      @Nullable String category,
      List<CommandNamePayload> commands,
      @Nullable List<CommandSurfacePayload> commandSurfaces,
      @Nullable List<CommandDescriptor> fullCommands)
      implements ProtocolSuccessPayload {
    public CapabilitiesCommandsSlicePayload {
      category = requireOptionalText(category, "category");
      commands = copyList(commands, "commands");
      if (commandSurfaces != null) {
        commandSurfaces = copyList(commandSurfaces, "commandSurfaces");
      }
      if (fullCommands != null) {
        fullCommands = copyList(fullCommands, "fullCommands");
      }
    }
  }
}
