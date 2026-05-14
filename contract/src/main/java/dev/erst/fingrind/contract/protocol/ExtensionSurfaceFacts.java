package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Shared immutable facts describing sanctioned current seams and adjacent future contexts. */
public record ExtensionSurfaceFacts(
    String model, List<String> implementedSeams, List<String> futureContexts, String description) {
  /** Validates one published extension-surface fact family. */
  public ExtensionSurfaceFacts {
    Objects.requireNonNull(model, "model");
    implementedSeams = List.copyOf(Objects.requireNonNull(implementedSeams, "implementedSeams"));
    futureContexts = List.copyOf(Objects.requireNonNull(futureContexts, "futureContexts"));
    Objects.requireNonNull(description, "description");
  }
}
