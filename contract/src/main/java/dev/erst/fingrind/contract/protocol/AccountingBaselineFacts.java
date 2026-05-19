package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Shared immutable facts describing FinGrind's current accounting-standards baseline. */
public record AccountingBaselineFacts(
    String scope,
    AccountingBaselineTarget currentTarget,
    AccountingBaselineTarget nextTarget,
    List<String> doctrineSources,
    List<String> builtInStatements,
    List<String> deliberateExclusions,
    List<String> nonClaims,
    List<ReportCapabilityFacts> reportCapabilities,
    AccountingPolicyPackFacts defaultPolicyPack,
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
    Objects.requireNonNull(currentTarget, "currentTarget");
    Objects.requireNonNull(nextTarget, "nextTarget");
    doctrineSources = List.copyOf(Objects.requireNonNull(doctrineSources, "doctrineSources"));
    builtInStatements = List.copyOf(Objects.requireNonNull(builtInStatements, "builtInStatements"));
    deliberateExclusions =
        List.copyOf(Objects.requireNonNull(deliberateExclusions, "deliberateExclusions"));
    nonClaims = List.copyOf(Objects.requireNonNull(nonClaims, "nonClaims"));
    reportCapabilities =
        List.copyOf(Objects.requireNonNull(reportCapabilities, "reportCapabilities"));
    Objects.requireNonNull(defaultPolicyPack, "defaultPolicyPack");
    Objects.requireNonNull(standardsPosition, "standardsPosition");
    Objects.requireNonNull(reportingPosition, "reportingPosition");
    Objects.requireNonNull(chartModelPosition, "chartModelPosition");
    Objects.requireNonNull(smallEntityPosition, "smallEntityPosition");
    Objects.requireNonNull(operationalPosition, "operationalPosition");
    Objects.requireNonNull(taxPosition, "taxPosition");
    Objects.requireNonNull(organizationalPosition, "organizationalPosition");
    Objects.requireNonNull(isoClarification, "isoClarification");
    List<String> implementedStatementIds =
        reportCapabilities.stream()
            .filter(capability -> capability.status() == CapabilityStatus.IMPLEMENTED)
            .map(ReportCapabilityFacts::statementId)
            .toList();
    if (!implementedStatementIds.equals(builtInStatements)) {
      throw new IllegalArgumentException(
          "builtInStatements must equal the implemented report capability inventory.");
    }
  }
}
