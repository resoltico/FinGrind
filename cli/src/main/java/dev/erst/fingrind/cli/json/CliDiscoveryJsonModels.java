package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestInputDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Discovery-oriented JSON records emitted by the CLI transport layer. */
public interface CliDiscoveryJsonModels {

  record CommandIndexPayload(OperationId name, String category, String summary)
      implements ProtocolSuccessPayload {
    public CommandIndexPayload {
      name = requireValue(name, "name");
      category = requireText(category, "category");
      summary = requireText(summary, "summary");
    }
  }

  record HelpOverviewMinimalPayload(
      String application,
      String version,
      String description,
      DiscoveryDetail detail,
      List<CommandIndexPayload> commands,
      String compactDetailHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public HelpOverviewMinimalPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      commands = copyList(commands, "commands");
      compactDetailHint = requireText(compactDetailHint, "compactDetailHint");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.MINIMAL) {
        throw new IllegalArgumentException("minimal help overview requires minimal detail.");
      }
    }
  }

  record HelpOverviewPayload(
      String application,
      String version,
      String description,
      DiscoveryDetail detail,
      List<CommandDescriptor> commands,
      List<String> gettingStarted,
      List<ExitCodeDescriptor> exitCodes,
      String capabilitiesHint,
      @Nullable HelpDescriptor fullContract)
      implements ProtocolSuccessPayload {
    public HelpOverviewPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      commands = copyList(commands, "commands");
      gettingStarted = copyList(gettingStarted, "gettingStarted");
      exitCodes = copyList(exitCodes, "exitCodes");
      capabilitiesHint = requireText(capabilitiesHint, "capabilitiesHint");
      if (detail == DiscoveryDetail.FULL && fullContract == null) {
        throw new IllegalArgumentException("fullContract must be present when detail is full.");
      }
      if (detail != DiscoveryDetail.FULL && fullContract != null) {
        throw new IllegalArgumentException("fullContract must be absent unless detail is full.");
      }
    }
  }

  record HelpOverviewCompactPayload(
      String application,
      String version,
      String description,
      DiscoveryDetail detail,
      List<CommandIndexPayload> commands,
      List<ExitCodeDescriptor> exitCodes,
      String capabilitiesHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public HelpOverviewCompactPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      commands = copyList(commands, "commands");
      exitCodes = copyList(exitCodes, "exitCodes");
      capabilitiesHint = requireText(capabilitiesHint, "capabilitiesHint");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.COMPACT) {
        throw new IllegalArgumentException("compact help overview requires compact detail.");
      }
    }
  }

  record CommandHelpPayload(
      String application,
      String version,
      String description,
      DiscoveryDetail detail,
      CommandDescriptor command,
      List<String> usage,
      List<String> options,
      @Nullable RequestFileGuidancePayload requestFile,
      List<String> examples,
      List<String> operatorNotes,
      List<ExitCodeDescriptor> exitCodes)
      implements ProtocolSuccessPayload {
    public CommandHelpPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      command = requireValue(command, "command");
      usage = copyList(usage, "usage");
      options = copyList(options, "options");
      examples = copyList(examples, "examples");
      operatorNotes = copyList(operatorNotes, "operatorNotes");
      exitCodes = copyList(exitCodes, "exitCodes");
    }
  }

  record CapabilitiesMinimalPayload(
      String application,
      String version,
      DiscoveryDetail detail,
      String bookBoundary,
      List<CommandIndexPayload> commands,
      String compactDetailHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public CapabilitiesMinimalPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      detail = requireValue(detail, "detail");
      bookBoundary = requireText(bookBoundary, "bookBoundary");
      commands = copyList(commands, "commands");
      compactDetailHint = requireText(compactDetailHint, "compactDetailHint");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.MINIMAL) {
        throw new IllegalArgumentException("minimal capabilities payload requires minimal detail.");
      }
    }
  }

  record CapabilitiesPayload(
      String application,
      String version,
      DiscoveryDetail detail,
      StorageSurfaceDescriptor storage,
      CommandCatalogDescriptor commands,
      RequestInputDescriptor requestInput,
      List<String> machineGuidance,
      @Nullable CapabilitiesDescriptor fullContract)
      implements ProtocolSuccessPayload {
    public CapabilitiesPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      detail = requireValue(detail, "detail");
      storage = requireValue(storage, "storage");
      commands = requireValue(commands, "commands");
      requestInput = requireValue(requestInput, "requestInput");
      machineGuidance = copyList(machineGuidance, "machineGuidance");
      if (detail == DiscoveryDetail.FULL && fullContract == null) {
        throw new IllegalArgumentException("fullContract must be present when detail is full.");
      }
      if (detail != DiscoveryDetail.FULL && fullContract != null) {
        throw new IllegalArgumentException("fullContract must be absent unless detail is full.");
      }
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

  record CapabilitiesCompactPayload(
      String application,
      String version,
      DiscoveryDetail detail,
      String bookBoundary,
      List<String> storageEngines,
      RequestInputCompactPayload requestInput,
      List<CommandSurfacePayload> commands,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public CapabilitiesCompactPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      detail = requireValue(detail, "detail");
      bookBoundary = requireText(bookBoundary, "bookBoundary");
      storageEngines = copyList(storageEngines, "storageEngines");
      requestInput = requireValue(requestInput, "requestInput");
      commands = copyList(commands, "commands");
      fullDetailHint = requireText(fullDetailHint, "fullDetailHint");
      if (detail != DiscoveryDetail.COMPACT) {
        throw new IllegalArgumentException("compact capabilities payload requires compact detail.");
      }
    }
  }

  record RequestFileGuidancePayload(
      String description,
      DiscoveryDetail detail,
      @Nullable PostingRequestTemplateDescriptor postingTemplate,
      @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate,
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
          && ledgerPlanTemplate == null
          && requestShapes == null
          && shortcutCommand == null) {
        throw new IllegalArgumentException(
            "At least one request-file guidance artifact must be present.");
      }
    }
  }
}
