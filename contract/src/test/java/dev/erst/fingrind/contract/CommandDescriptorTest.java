package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommandDescriptor}. */
class CommandDescriptorTest {
  @Test
  void stdoutContractSummary_distinguishesSelectableAndFixedStdoutContracts() {
    CommandDescriptor selectable =
        new CommandDescriptor(
            OperationId.HELP,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            List.of(),
            "Show help");
    CommandDescriptor fixedEnvelope =
        new CommandDescriptor(
            OperationId.EXECUTE_PLAN,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Execute one plan");
    CommandDescriptor fixedRawJson =
        new CommandDescriptor(
            OperationId.PRINT_PLAN_TEMPLATE,
            List.of(),
            List.of(),
            ExecutionMode.RAW_JSON,
            List.of(),
            List.of(),
            "Print one plan template");

    assertEquals("json, human (via --output)", selectable.stdoutContractSummary());
    assertEquals("json envelope (fixed)", fixedEnvelope.stdoutContractSummary());
    assertEquals("raw json (fixed)", fixedRawJson.stdoutContractSummary());
  }
}
