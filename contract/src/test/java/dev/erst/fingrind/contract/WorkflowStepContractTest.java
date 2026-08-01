package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepKind;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for scaffold placeholder and help-workflow contract owners. */
class WorkflowStepContractTest {
  @Test
  void scaffoldPlaceholdersPublishCanonicalReservedValues() {
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.EFFECTIVE_DATE));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.COMMAND_ID));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.IDEMPOTENCY_KEY));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.CAUSATION_ID));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.TAX_REGISTRATION_ID));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.TAX_JURISDICTION));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.OUTPUT_TAX_CODE));
    assertFalse(ScaffoldPlaceholders.isReserved("real-id"));
  }

  @Test
  void workflowStepKindsPublishStableWireValues() {
    assertEquals("command", WorkflowStepKind.COMMAND.wireValue());
    assertEquals("edit", WorkflowStepKind.EDIT.wireValue());
    assertEquals("note", WorkflowStepKind.NOTE.wireValue());
    assertEquals(
        WorkflowStepKind.EDIT,
        WireValue.fromWireValue(WorkflowStepKind.class, "edit", "Unsupported workflow step kind"));
    assertEquals(List.of("command", "edit", "note"), WireValue.wireValues(WorkflowStepKind.class));
    assertEquals(
        List.of(
            "path-posix-shell",
            "bundle-posix-shell",
            "source-checkout-posix-shell",
            "source-checkout-windows-powershell",
            "direct-java-posix-shell",
            "direct-java-windows-powershell",
            "container-docker"),
        WireValue.wireValues(WorkflowSurface.class));
    assertEquals(
        WorkflowSurface.CONTAINER_DOCKER, WorkflowSurface.fromWireValue("container-docker"));
    assertThrows(
        IllegalArgumentException.class,
        () -> WorkflowSurface.fromWireValue("bundle-windows-powershell"));
  }

  @Test
  void workflowStepDescriptorFactoriesBuildTypedSteps() {
    WorkflowStepDescriptor command = WorkflowStepDescriptor.command("./bin/fingrind help");
    WorkflowStepDescriptor edit =
        WorkflowStepDescriptor.edit(
            "./declare-account-supplemental-cash-reserve.json",
            """
            {
              "accountCode": "1000"
            }
            """);
    WorkflowStepDescriptor note = WorkflowStepDescriptor.note("Use a fresh idempotency key");
    WorkflowStepDescriptor direct = new WorkflowStepDescriptor.Note("Direct note");

    assertEquals(WorkflowStepKind.COMMAND, command.kind());
    assertEquals("./bin/fingrind help", ((WorkflowStepDescriptor.Command) command).text());
    assertEquals(WorkflowStepKind.EDIT, edit.kind());
    assertEquals(
        "./declare-account-supplemental-cash-reserve.json",
        ((WorkflowStepDescriptor.Edit) edit).path());
    assertTrue(
        ((WorkflowStepDescriptor.Edit) edit).content().contains("\"accountCode\": \"1000\""));
    assertEquals(WorkflowStepKind.NOTE, note.kind());
    assertEquals("Use a fresh idempotency key", ((WorkflowStepDescriptor.Note) note).text());
    assertEquals(WorkflowStepKind.NOTE, direct.kind());
    assertEquals("Direct note", ((WorkflowStepDescriptor.Note) direct).text());

    WorkflowDescriptor workflow =
        new WorkflowDescriptor(WorkflowSurface.BUNDLE_POSIX_SHELL, List.of(command, edit, note));
    assertEquals(WorkflowSurface.BUNDLE_POSIX_SHELL, workflow.surface());
    assertEquals(List.of(command, edit, note), workflow.steps());
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowDescriptor(WorkflowSurface.BUNDLE_POSIX_SHELL, List.of()));
    assertThrows(
        NullPointerException.class, () -> new WorkflowStepDescriptor.Command(nullOf(String.class)));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor.Edit(nullOf(String.class), "{}"));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor.Edit("./file.json", nullOf(String.class)));
    assertThrows(
        NullPointerException.class, () -> new WorkflowStepDescriptor.Note(nullOf(String.class)));
  }
}
