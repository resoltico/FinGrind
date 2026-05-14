package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Shared immutable facts describing FinGrind's current accounting-standards baseline. */
public record AccountingBaselineFacts(
    String scope,
    List<String> doctrineSources,
    List<String> builtInStatements,
    List<String> deliberateExclusions,
    String standardsPosition,
    String reportingPosition,
    String chartModelPosition,
    String smallEntityPosition,
    String operationalPosition,
    String taxPosition,
    String organizationalPosition,
    String isoClarification) {
  /** Validates one published accounting-baseline fact family. */
  public AccountingBaselineFacts {
    Objects.requireNonNull(scope, "scope");
    doctrineSources = List.copyOf(Objects.requireNonNull(doctrineSources, "doctrineSources"));
    builtInStatements = List.copyOf(Objects.requireNonNull(builtInStatements, "builtInStatements"));
    deliberateExclusions =
        List.copyOf(Objects.requireNonNull(deliberateExclusions, "deliberateExclusions"));
    Objects.requireNonNull(standardsPosition, "standardsPosition");
    Objects.requireNonNull(reportingPosition, "reportingPosition");
    Objects.requireNonNull(chartModelPosition, "chartModelPosition");
    Objects.requireNonNull(smallEntityPosition, "smallEntityPosition");
    Objects.requireNonNull(operationalPosition, "operationalPosition");
    Objects.requireNonNull(taxPosition, "taxPosition");
    Objects.requireNonNull(organizationalPosition, "organizationalPosition");
    Objects.requireNonNull(isoClarification, "isoClarification");
  }
}
