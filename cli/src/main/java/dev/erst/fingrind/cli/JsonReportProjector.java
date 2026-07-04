package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportModel;

/** JSON projector for the shared report content model. */
final class JsonReportProjector {
  private JsonReportProjector() {}

  static ReportModel project(ReportModel reportModel) {
    return java.util.Objects.requireNonNull(reportModel, "reportModel");
  }
}
