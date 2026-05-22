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
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Discovery-oriented JSON records emitted by the CLI transport layer. */
public interface CliDiscoveryJsonModels {

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
