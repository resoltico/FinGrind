package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** One core-owned artifact export descriptor for a public FinGrind operation. */
public record ProtocolArtifactOutput(String format, String option, String description) {
  private static final ProtocolArtifactOutput PDF =
      new ProtocolArtifactOutput(
          "pdf",
          ProtocolOptions.PDF_OUT + " <path>",
          "Writes one PDF report artifact to the selected destination while preserving the command's selected stdout output mode.");

  /** Validates one artifact-export descriptor. */
  public ProtocolArtifactOutput {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(option, "option");
    Objects.requireNonNull(description, "description");
  }

  /** Returns the canonical PDF export descriptor. */
  public static ProtocolArtifactOutput pdf() {
    return PDF;
  }
}
