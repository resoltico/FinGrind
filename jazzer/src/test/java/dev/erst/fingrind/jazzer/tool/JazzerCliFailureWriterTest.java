package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers retained-artifact rendering on the local Jazzer operator failure surface. */
class JazzerCliFailureWriterTest {
  private static final JazzerCliCommandFailurePayload RETAINED_ARTIFACT_FAILURE =
      new JazzerCliCommandFailurePayload(
          "error",
          "promote-seed",
          1,
          "Seed promotion did not complete.",
          List.of("/work/corpus/input.json", "/work/corpus/input.json.metadata"),
          "Usage: promote-seed");

  @Test
  void writeFailure_renders_retained_artifacts_in_plain_text() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    JazzerCliFailureWriter.writeFailure(
        new PrintWriter(output, true),
        new PrintWriter(errors, true),
        false,
        RETAINED_ARTIFACT_FAILURE);

    assertTrue(output.toString().isBlank());
    assertTrue(errors.toString().contains("Seed promotion did not complete."));
    assertTrue(errors.toString().contains("Retained artifacts:"));
    assertTrue(errors.toString().contains("/work/corpus/input.json"));
    assertTrue(errors.toString().contains("/work/corpus/input.json.metadata"));
    assertTrue(errors.toString().contains("Usage: promote-seed"));
  }

  @Test
  void writeFailure_renders_retained_artifacts_in_json() throws Exception {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();

    JazzerCliFailureWriter.writeFailure(
        new PrintWriter(output, true),
        new PrintWriter(errors, true),
        true,
        RETAINED_ARTIFACT_FAILURE);

    assertTrue(output.toString().contains("\"retainedArtifactPaths\""));
    assertTrue(output.toString().contains("/work/corpus/input.json"));
    assertTrue(output.toString().contains("/work/corpus/input.json.metadata"));
    assertTrue(errors.toString().isBlank());
  }
}
