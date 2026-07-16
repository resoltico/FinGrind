package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;

/** Routes operator-facing command help to its focused guidance owners. */
final class CliDiscoveryCommandGuidance {
  private CliDiscoveryCommandGuidance() {}

  static String renderPreparation(OperationId operationId) {
    return CliDiscoveryOperationalGuidance.renderPreparation(operationId);
  }

  static String renderRequestGuidance(HelpDescriptor helpDescriptor, OperationId operationId) {
    return CliDiscoveryRequestGuidance.render(helpDescriptor, operationId);
  }

  static String renderExitBehavior(List<ExitCodeDescriptor> exitCodes) {
    return CliDiscoveryOperationalGuidance.renderExitBehavior(exitCodes);
  }

  static String renderTemporalScopeGuidance(OperationId operationId) {
    return CliDiscoveryOperationalGuidance.renderTemporalScope(operationId);
  }

  static CliDiscoveryCommandHelpSupport.SupportEntry requestTemplateHint(OperationId operationId) {
    return CliDiscoveryRequestGuidance.requestTemplateHint(operationId);
  }
}
