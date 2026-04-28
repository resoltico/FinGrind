package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  }

  @Test
  void workflowStepDescriptorFactoriesBuildTypedSteps() {
    WorkflowStepDescriptor command = WorkflowStepDescriptor.command("fingrind help");
    WorkflowStepDescriptor edit = WorkflowStepDescriptor.edit("Replace placeholders");
    WorkflowStepDescriptor note = WorkflowStepDescriptor.note("Use a fresh idempotency key");
    WorkflowStepDescriptor direct =
        new WorkflowStepDescriptor(WorkflowStepKind.NOTE, "Direct note");

    assertEquals(WorkflowStepKind.COMMAND, command.kind());
    assertEquals("fingrind help", command.text());
    assertEquals(WorkflowStepKind.EDIT, edit.kind());
    assertEquals("Replace placeholders", edit.text());
    assertEquals(WorkflowStepKind.NOTE, note.kind());
    assertEquals("Use a fresh idempotency key", note.text());
    assertEquals(WorkflowStepKind.NOTE, direct.kind());
    assertEquals("Direct note", direct.text());
  }
}
