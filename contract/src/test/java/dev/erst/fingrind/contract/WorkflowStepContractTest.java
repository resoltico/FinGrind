package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for scaffold placeholder and help-workflow contract owners. */
class WorkflowStepContractTest {
  @Test
  void scaffoldPlaceholdersPublishCanonicalReservedValues() {
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.EFFECTIVE_DATE));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.ACTOR_ID));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.COMMAND_ID));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.IDEMPOTENCY_KEY));
    assertTrue(ScaffoldPlaceholders.isReserved(ScaffoldPlaceholders.CAUSATION_ID));
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
        List.of("bundle-posix-shell", "bundle-windows-powershell"),
        WireValue.wireValues(WorkflowSurface.class));
    assertEquals(
        WorkflowSurface.BUNDLE_WINDOWS_POWERSHELL,
        WorkflowSurface.fromWireValue("bundle-windows-powershell"));
  }

  @Test
  void workflowStepDescriptorFactoriesBuildTypedSteps() {
    WorkflowStepDescriptor command = WorkflowStepDescriptor.command("./bin/fingrind help");
    WorkflowStepDescriptor edit =
        WorkflowStepDescriptor.edit(
            "./declare-account-cash.json",
            """
            {
              "accountCode": "1000"
            }
            """);
    WorkflowStepDescriptor note = WorkflowStepDescriptor.note("Use a fresh idempotency key");
    WorkflowStepDescriptor direct =
        new WorkflowStepDescriptor(WorkflowStepKind.NOTE, "Direct note", null, null);

    assertEquals(WorkflowStepKind.COMMAND, command.kind());
    assertEquals("./bin/fingrind help", command.text());
    assertEquals(null, command.path());
    assertEquals(null, command.content());
    assertEquals(WorkflowStepKind.EDIT, edit.kind());
    assertEquals("./declare-account-cash.json", edit.path());
    assertTrue(edit.content() != null && edit.content().contains("\"accountCode\": \"1000\""));
    assertEquals(null, edit.text());
    assertEquals(WorkflowStepKind.NOTE, note.kind());
    assertEquals("Use a fresh idempotency key", note.text());
    assertEquals(WorkflowStepKind.NOTE, direct.kind());
    assertEquals("Direct note", direct.text());

    WorkflowDescriptor workflow =
        new WorkflowDescriptor(WorkflowSurface.BUNDLE_POSIX_SHELL, List.of(command, edit, note));
    assertEquals(WorkflowSurface.BUNDLE_POSIX_SHELL, workflow.surface());
    assertEquals(List.of(command, edit, note), workflow.steps());
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowDescriptor(WorkflowSurface.BUNDLE_POSIX_SHELL, List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, "cmd", "./file.json", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, "cmd", null, "{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.COMMAND, "cmd", "./file.json", "{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.EDIT, "bad", null, null));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.EDIT, null, null, "{}"));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.EDIT, null, "./file.json", null));
    assertThrows(
        NullPointerException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.NOTE, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.NOTE, "note", "./file.json", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.NOTE, "note", null, "{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WorkflowStepDescriptor(WorkflowStepKind.NOTE, "note", "./file.json", "{}"));
  }
}
