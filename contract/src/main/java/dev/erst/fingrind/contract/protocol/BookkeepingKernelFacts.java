package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Shared immutable facts describing FinGrind's current executable bookkeeping kernel. */
public record BookkeepingKernelFacts(
    String scope,
    List<String> builtInStatements,
    List<ReportCapabilityFacts> reportCapabilities,
    String description) {
  /** Validates one published bookkeeping-kernel fact family. */
  public BookkeepingKernelFacts {
    Objects.requireNonNull(scope, "scope");
    builtInStatements = List.copyOf(Objects.requireNonNull(builtInStatements, "builtInStatements"));
    reportCapabilities =
        List.copyOf(Objects.requireNonNull(reportCapabilities, "reportCapabilities"));
    Objects.requireNonNull(description, "description");
    List<String> publishedStatementIds =
        reportCapabilities.stream().map(ReportCapabilityFacts::statementId).toList();
    if (!publishedStatementIds.equals(builtInStatements)) {
      throw new IllegalArgumentException(
          "builtInStatements must equal the published report capability inventory.");
    }
  }
}
