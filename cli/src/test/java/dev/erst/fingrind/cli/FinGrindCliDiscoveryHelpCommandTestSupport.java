package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared helper logic for command-scoped discovery help tests. */
class FinGrindCliDiscoveryHelpCommandTestSupport extends FinGrindCliDiscoveryCommandTestSupport {
  protected final String runCommandHelpText(OperationId operationId) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"help", operationId.wireName(), "--output", "text"});

    assertEquals(0, exitCode);
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  protected final JsonNode runCommandHelpPayloadJson(OperationId operationId) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {"help", operationId.wireName(), "--output", "json", "--detail", "full"});

    assertEquals(0, exitCode);
    return assertDoesNotThrow(
        () -> new ObjectMapper().readTree(outputStream.toByteArray()).path("payload"));
  }

  protected final void assertTemporalScopeHelp(
      String commandName, String scopeKind, String boundaryFlags, String boundaryBehaviorSnippet) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"help", commandName, "--output", "text"});
    assertEquals(0, exitCode);
    String help = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(help.contains("Temporal Scope"));
    assertTrue(help.contains("Scope kind"));
    assertTrue(help.contains(scopeKind));
    assertTrue(help.contains("Boundary flags"));
    assertTrue(help.contains(boundaryFlags));
    assertTrue(help.contains(boundaryBehaviorSnippet));
  }

  protected static void assertForbiddenPresence(JsonNode descriptorArray, String fieldName) {
    JsonNode descriptor = descriptorByName(descriptorArray, fieldName);
    assertEquals(
        RequestFieldPresence.FORBIDDEN.wireValue(),
        descriptor.path("presence").stringValue(),
        descriptor.toPrettyString());
  }

  protected static JsonNode descriptorByName(JsonNode descriptorArray, String fieldName) {
    for (JsonNode descriptor : descriptorArray) {
      if (fieldName.equals(descriptor.path("name").stringValue())) {
        return descriptor;
      }
    }
    throw new AssertionError("Missing descriptor: " + fieldName + " in " + descriptorArray);
  }

  protected static void assertContainsShellCommandBlock(String help, String command) {
    String expectedShellBlock =
        CliTextFormat.renderShellCommandBlock(
            java.util.List.of(command), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    assertTrue(help.contains(expectedShellBlock), help);
  }

  protected static void assertContainsNestedPostingModelPaths(
      String help, ContractRequestShapes.PostEntryRequestShapeDescriptor postingModel) {
    assertContainsPrefixedFieldRows(help, postingModel.topLevelFields(), "steps[].posting.");
    assertContainsPrefixedFieldRows(help, postingModel.lineFields(), "steps[].posting.lines[].");
    assertContainsPrefixedFieldRows(
        help, postingModel.openingBalanceFields(), "steps[].posting.openingBalances[].");
    assertContainsPrefixedFieldRows(
        help, postingModel.evidenceFields(), "steps[].posting.evidence.");
    assertContainsPrefixedFieldRows(
        help, postingModel.sourceDocumentFields(), "steps[].posting.evidence.sourceDocuments[].");
    assertContainsPrefixedFieldRows(
        help, postingModel.approvalFields(), "steps[].posting.evidence.approvals[].");
    assertContainsPrefixedFieldRows(
        help, postingModel.provenanceFields(), "steps[].posting.provenance.");
    assertContainsPrefixedFieldRows(
        help, postingModel.reversalFields(), "steps[].posting.reversal.");
  }

  protected static Optional<String> expectedRequestTemplateSupportCommand(OperationId operationId) {
    HelpDescriptor helpDescriptor =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(), CliDiscoveryTestSupport.environment(), operationId);
    if (helpDescriptor.requestTemplate() != null
        || helpDescriptor.declareAccountTemplate() != null) {
      return Optional.of(
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName());
    }
    if (helpDescriptor.planTemplate() != null) {
      return Optional.of(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE));
    }
    return Optional.empty();
  }

  protected static boolean hasNamedField(JsonNode fields, String expectedName) {
    if (!fields.isArray()) {
      return false;
    }
    for (JsonNode field : fields) {
      if (expectedName.equals(field.path("name").stringValue())) {
        return true;
      }
    }
    return false;
  }

  private static void assertContainsPrefixedFieldRows(
      String help,
      java.util.List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      if (field.presence() == RequestFieldPresence.FORBIDDEN) {
        continue;
      }
      assertTrue(help.contains(prefix + field.name()), help);
    }
  }
}
