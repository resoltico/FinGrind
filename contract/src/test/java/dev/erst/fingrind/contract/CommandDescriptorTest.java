package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.List;
import java.util.Objects;
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
            new SelectableOutputDefaultsDescriptor(OutputMode.HUMAN, OutputMode.HUMAN),
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

    assertEquals(
        "json, human (via --output; default: human interactive, human redirected)",
        selectable.stdoutContractSummary());
    assertEquals("json envelope (fixed)", fixedEnvelope.stdoutContractSummary());
    assertEquals("raw json (fixed)", fixedRawJson.stdoutContractSummary());
  }

  @Test
  void constructor_rejectsSelectableDefaultsWhenNoOutputModesAreAvailable() {
    IllegalArgumentException rejection =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CommandDescriptor(
                    OperationId.HELP,
                    List.of(),
                    List.of(),
                    ExecutionMode.JSON_ENVELOPE,
                    List.of(),
                    new SelectableOutputDefaultsDescriptor(OutputMode.HUMAN, OutputMode.HUMAN),
                    List.of(),
                    "Show help"));

    assertEquals(
        "selectableOutputDefaults must be absent when outputModes is empty.",
        rejection.getMessage());
  }

  @Test
  void convenienceConstructor_infersRedirectedDefaultsFromOperationKind() {
    CommandDescriptor help =
        new CommandDescriptor(
            OperationId.HELP,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            List.of(),
            "Show help");
    CommandDescriptor declareAccount =
        new CommandDescriptor(
            OperationId.DECLARE_ACCOUNT,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            List.of(),
            "Declare one account");
    CommandDescriptor fixedEnvelope =
        new CommandDescriptor(
            OperationId.EXECUTE_PLAN,
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            List.of(),
            List.of(),
            "Execute one plan");

    assertEquals(
        OutputMode.HUMAN,
        Objects.requireNonNull(help.selectableOutputDefaults()).redirectedStdout());
    assertEquals(
        OutputMode.JSON,
        Objects.requireNonNull(declareAccount.selectableOutputDefaults()).redirectedStdout());
    assertNull(fixedEnvelope.selectableOutputDefaults());
  }
}
