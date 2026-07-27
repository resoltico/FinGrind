package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Captures one report family's workflow, projection, rendering, and exit policy. */
record CliConfiguredReportHandler<QUERY, RESULT, REPORTED>(
    WorkflowCall<QUERY, RESULT> workflowCall,
    ReportedValue<RESULT, REPORTED> reportedValue,
    Function<REPORTED, ReportModel> reportModelBuilder,
    ResultWriter<RESULT> resultWriter,
    ToIntFunction<RESULT> successExitCode) {
  CliConfiguredReportHandler {
    Objects.requireNonNull(workflowCall, "workflowCall");
    Objects.requireNonNull(reportedValue, "reportedValue");
    Objects.requireNonNull(reportModelBuilder, "reportModelBuilder");
    Objects.requireNonNull(resultWriter, "resultWriter");
    Objects.requireNonNull(successExitCode, "successExitCode");
  }

  /** Writes one report-family result through the chosen output mode and artifact context. */
  @FunctionalInterface
  interface ResultWriter<RESULT> {
    /** Publishes one resolved report result and any exported artifact publication. */
    void write(
        RESULT result,
        OutputMode outputMode,
        @Nullable ArtifactPublicationResult exportedArtifact,
        Instant generatedAt);
  }

  /** Resolves one report-family workflow call for the selected book and query. */
  @FunctionalInterface
  interface WorkflowCall<QUERY, RESULT> {
    /** Resolves one report-family workflow call for the selected book and query. */
    ContractDecision<RESULT> run(BookAccess bookAccess, QUERY query);
  }

  /** Resolves the reported value when one successful result carries a report payload. */
  @FunctionalInterface
  interface ReportedValue<RESULT, REPORTED> {
    /** Returns the report payload for this result, or null when the result is non-reported. */
    @Nullable REPORTED apply(RESULT result);
  }
}
