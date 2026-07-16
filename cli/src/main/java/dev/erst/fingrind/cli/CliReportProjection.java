package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Couples one report's shared model projection with its machine-readable payload projection. */
record CliReportProjection<REPORTED>(
    Function<REPORTED, ReportModel> reportModelBuilder,
    BiFunction<REPORTED, Instant, CliReportJsonModels.ReportPayload> reportPayloadBuilder) {
  CliReportProjection {
    Objects.requireNonNull(reportModelBuilder, "reportModelBuilder");
    Objects.requireNonNull(reportPayloadBuilder, "reportPayloadBuilder");
  }
}
