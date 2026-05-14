package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareAccountTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.LedgerPlanTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.PostingRequestTemplateDescriptor;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Discovery-oriented JSON records emitted by the CLI transport layer. */
public interface CliDiscoveryJsonModels {

  record HelpOverviewPayload(
      String application,
      String version,
      String description,
      List<CommandDescriptor> commands,
      List<String> gettingStarted,
      List<ExitCodeDescriptor> exitCodes,
      String capabilitiesHint)
      implements ProtocolSuccessPayload {
    public HelpOverviewPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      description = requireText(description, "description");
      commands = copyList(commands, "commands");
      gettingStarted = copyList(gettingStarted, "gettingStarted");
      exitCodes = copyList(exitCodes, "exitCodes");
      capabilitiesHint = requireText(capabilitiesHint, "capabilitiesHint");
    }
  }

  record CommandHelpPayload(
      String application,
      String version,
      String description,
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
      command = requireValue(command, "command");
      usage = copyList(usage, "usage");
      options = copyList(options, "options");
      examples = copyList(examples, "examples");
      operatorNotes = copyList(operatorNotes, "operatorNotes");
      exitCodes = copyList(exitCodes, "exitCodes");
    }
  }

  record RequestFileGuidancePayload(
      String description,
      @Nullable PostingRequestTemplateDescriptor postingTemplate,
      @Nullable DeclareAccountTemplateDescriptor declareAccountTemplate,
      @Nullable LedgerPlanTemplateDescriptor ledgerPlanTemplate,
      @Nullable RequestShapesDescriptor requestShapes,
      @Nullable String shortcutCommand)
      implements ProtocolSuccessPayload {
    public RequestFileGuidancePayload {
      description = requireText(description, "description");
      shortcutCommand = requireOptionalText(shortcutCommand, "shortcutCommand");
      if (postingTemplate == null
          && declareAccountTemplate == null
          && ledgerPlanTemplate == null
          && requestShapes == null) {
        throw new IllegalArgumentException(
            "At least one request-file guidance artifact must be present.");
      }
    }
  }
}
