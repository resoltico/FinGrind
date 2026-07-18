package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests request-file guidance exclusive to the account-retirement command. */
class CliDiscoveryRetirementGuidanceTest extends CliDiscoveryHelpTextTestSupport {
  @Test
  void renderHelpText_rendersRetireAccountGuidanceOnlyWhenTheRetirementShapeExists() {
    HelpDescriptor retireAccountCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.RETIRE_ACCOUNT);
    String rendered = renderHelpText(retireAccountCanonical);
    String withoutRequestShapes = renderHelpText(withRequestShapes(retireAccountCanonical, null));
    String withoutRetirementShape =
        renderHelpText(
            withRequestShapes(
                retireAccountCanonical,
                new ContractRequestShapes.RequestShapesDescriptor(
                    Objects.requireNonNull(retireAccountCanonical.requestShapes()).schemaDialect(),
                    retireAccountCanonical.requestShapes().bookkeepingEntry(),
                    retireAccountCanonical.requestShapes().declareAccount(),
                    null,
                    retireAccountCanonical.requestShapes().declareTaxRegistration(),
                    retireAccountCanonical.requestShapes().ledgerPlan())));

    assertTrue(rendered.contains("Input Contract"), rendered);
    assertTrue(rendered.contains("accountCode"), rendered);
    assertTrue(rendered.contains("retire-account"), rendered);
    assertFalse(withoutRequestShapes.contains("\nInput\n"), withoutRequestShapes);
    assertFalse(withoutRetirementShape.contains("\nInput\n"), withoutRetirementShape);
  }

  private static HelpDescriptor withRequestShapes(
      HelpDescriptor baseHelp,
      ContractRequestShapes.@Nullable RequestShapesDescriptor requestShapes) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.protocolVersion(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        requestShapes,
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        baseHelp.declareTaxRegistrationTemplate(),
        baseHelp.planTemplate(),
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }
}
