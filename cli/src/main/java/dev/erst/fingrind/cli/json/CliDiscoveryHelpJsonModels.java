package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.cli.json.CliDiscoveryRequestFileGuidanceJsonModels.RequestFileGuidancePayload;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Help-oriented discovery JSON record families emitted by the CLI transport layer. */
public interface CliDiscoveryHelpJsonModels extends CliDiscoveryCommonJsonModels {

  record HelpOverviewMinimalPayload(
      String application,
      String version,
      String protocolVersion,
      String description,
      DiscoveryDetail detail,
      @Nullable String category,
      List<CommandNamePayload> commands,
      String compactDetailHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public HelpOverviewMinimalPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      category = requireOptionalText(category, "category");
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
      String protocolVersion,
      String description,
      DiscoveryDetail detail,
      @Nullable String category,
      List<CommandDescriptor> commands,
      List<String> gettingStarted,
      List<ExitCodeDescriptor> exitCodes,
      String capabilitiesHint,
      @Nullable HelpDescriptor fullContract)
      implements ProtocolSuccessPayload {
    public HelpOverviewPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      category = requireOptionalText(category, "category");
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
      String protocolVersion,
      String description,
      DiscoveryDetail detail,
      @Nullable String category,
      List<CommandIndexPayload> commands,
      List<ExitCodeDescriptor> exitCodes,
      String capabilitiesHint,
      String fullDetailHint)
      implements ProtocolSuccessPayload {
    public HelpOverviewCompactPayload {
      application = requireText(application, "application");
      version = requireText(version, "version");
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      category = requireOptionalText(category, "category");
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
      String protocolVersion,
      String description,
      DiscoveryDetail detail,
      CommandDescriptor command,
      String syntax,
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
      protocolVersion = requireText(protocolVersion, "protocolVersion");
      description = requireText(description, "description");
      detail = requireValue(detail, "detail");
      command = requireValue(command, "command");
      syntax = requireText(syntax, "syntax");
      usage = copyList(usage, "usage");
      options = copyList(options, "options");
      examples = copyList(examples, "examples");
      operatorNotes = copyList(operatorNotes, "operatorNotes");
      exitCodes = copyList(exitCodes, "exitCodes");
    }
  }
}
