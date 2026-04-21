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
    format = requireText(format, "format");
    option = requireText(option, "option");
    description = requireText(description, "description");
  }

  /** Returns the canonical PDF export descriptor. */
  public static ProtocolArtifactOutput pdf() {
    return PDF;
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
