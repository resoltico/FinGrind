package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused layout tests for discovery help text and support surfaces. */
class CliDiscoveryHelpTextLayoutTest extends CliDiscoveryHelpTextTestSupport {
  @Test
  void renderHelpText_supportLabelsRemainSubordinateToTheSupportSection() {
    String rendered =
        renderHelpText(
            MachineContract.help(
                CliDiscoveryTestSupport.identity(),
                CliDiscoveryTestSupport.environment(),
                OperationId.EXECUTE_PLAN));

    assertFalse(rendered.contains("Command help\n------------"), rendered);
    assertFalse(rendered.contains("Machine contract\n----------------"), rendered);
    assertFalse(rendered.contains("Request template\n----------------"), rendered);
  }

  @Test
  void supportEntryFactoriesRejectBlankLabelsAndPayloads() {
    IllegalArgumentException blankLabel =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliDiscoveryCommandHelpSupport.SupportEntry.command(
                    " ", CliInvocationText.commandExample(OperationId.HELP)));
    IllegalArgumentException blankCommand =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliDiscoveryCommandHelpSupport.SupportEntry.command("Command help", " "));
    IllegalArgumentException blankNote =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliDiscoveryCommandHelpSupport.SupportEntry.note("Request template", " "));

    assertEquals("label must not be blank.", blankLabel.getMessage());
    assertEquals("command must not be blank.", blankCommand.getMessage());
    assertEquals("note must not be blank.", blankNote.getMessage());
  }

  @Test
  void renderHelpText_executePlanUsesOverCapKeysAsContinuationHeaders() {
    int labelWidthCap = 32;
    String exactCapLabel = "E".repeat(labelWidthCap);
    String overCapLabel = "O".repeat(labelWidthCap + 1);
    String directRendered =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Short", "alpha"),
                List.of(exactCapLabel, "beta"),
                List.of(overCapLabel, "gamma")),
            80,
            labelWidthCap);
    String expectedContinuationHeaderRendered =
        """
        Short%s : alpha
        %s : beta
        %s:
        %sgamma
        """
            .formatted(" ".repeat(27), exactCapLabel, overCapLabel, " ".repeat(35))
            .stripTrailing();

    String rendered =
        renderHelpText(
            MachineContract.help(
                CliDiscoveryTestSupport.identity(),
                CliDiscoveryTestSupport.environment(),
                OperationId.EXECUTE_PLAN));
    String planIdLine = firstLineStartingWith(rendered, "planId");
    int valueColumn = planIdLine.indexOf("Caller-supplied plan identifier.");
    String overCapFieldLine =
        firstLineStartingWith(rendered, "steps[].posting.evidence.sourceDocuments:");
    List<String> renderedLines = rendered.lines().toList();
    String overCapContinuationLine = renderedLines.get(renderedLines.indexOf(overCapFieldLine) + 1);

    assertAll(
        () -> assertEquals(expectedContinuationHeaderRendered, directRendered),
        () -> assertEquals(35, directRendered.lines().toList().getFirst().indexOf("alpha")),
        () -> assertEquals(35, directRendered.lines().toList().get(1).indexOf("beta")),
        () -> assertEquals(overCapLabel + ":", directRendered.lines().toList().get(2)),
        () -> assertEquals(35, directRendered.lines().toList().get(3).indexOf("gamma")),
        () -> assertFalse(directRendered.contains(overCapLabel + " : gamma"), directRendered),
        () -> assertEquals("steps[].posting.evidence.sourceDocuments:", overCapFieldLine),
        () -> assertFalse(overCapFieldLine.contains(" : "), overCapFieldLine),
        () ->
            assertEquals(
                valueColumn,
                overCapContinuationLine.indexOf(
                    "Non-empty ordered source-document references linked"),
                rendered),
        () -> assertEquals(32, planIdLine.indexOf(" : ")));
  }

  @Test
  void keyFilePathGuidanceSection_elidesBlankGuidanceAndWrapsPresentGuidance() {
    assertEquals("", CliDiscoveryHelpTextRenderer.keyFilePathGuidanceSection("   "));

    String rendered =
        CliDiscoveryHelpTextRenderer.keyFilePathGuidanceSection(
            "Store the key file in one owner-only directory.");

    assertTrue(rendered.contains("Key-File Path"));
    assertTrue(rendered.contains("Store the key file in one owner-only directory."));
  }
}
