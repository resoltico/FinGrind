package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Builds the common report-model envelope shared by one-section lifecycle registers. */
final class LifecycleRegisterReportModelSupport {
  private LifecycleRegisterReportModelSupport() {}

  static <REPORT, ROW> ReportModel build(
      REPORT report, SingleSectionRegisterProjection<REPORT, ROW> projection) {
    List<ROW> rows = projection.rows().apply(report);
    ReportSection section =
        ReportModelSupport.section(
            projection.sectionKey(),
            projection.sectionTitle(),
            rows.isEmpty()
                ? List.of(
                    new ReportVerdict(
                        "Outcome", ReportModelNarrative.noMatches(projection.noMatchSubject())))
                : List.of(),
            projection.columns(),
            rows.stream().map(projection.rowProjection()).toList(),
            List.of());
    return new ReportModel(
        projection.reportFamily(),
        projection.reportTitle(),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            projection.bookIdentity().apply(report),
            null,
            null,
            null,
            projection.effectiveDateAsOf().apply(report).orElse(null),
            EffectiveDateRange.unbounded(),
            List.of(new ReportVerdict(projection.truthLabel(), projection.truth()))),
        List.of(new ReportVerdict(projection.countLabel(), Integer.toString(rows.size()))),
        List.of(section),
        projection.csvProjection().apply(report));
  }

  /** Declares the context-specific portions of one standard single-section lifecycle register. */
  record SingleSectionRegisterProjection<REPORT, ROW>(
      String reportFamily,
      String reportTitle,
      String truthLabel,
      String truth,
      String countLabel,
      String sectionKey,
      String sectionTitle,
      String noMatchSubject,
      Function<REPORT, BookIdentity> bookIdentity,
      Function<REPORT, Optional<LocalDate>> effectiveDateAsOf,
      Function<REPORT, List<ROW>> rows,
      List<ReportColumn> columns,
      Function<ROW, ReportRow> rowProjection,
      Function<REPORT, ReportCsvProjection> csvProjection) {
    SingleSectionRegisterProjection {
      Objects.requireNonNull(reportFamily, "reportFamily");
      Objects.requireNonNull(reportTitle, "reportTitle");
      Objects.requireNonNull(truthLabel, "truthLabel");
      Objects.requireNonNull(truth, "truth");
      Objects.requireNonNull(countLabel, "countLabel");
      Objects.requireNonNull(sectionKey, "sectionKey");
      Objects.requireNonNull(sectionTitle, "sectionTitle");
      Objects.requireNonNull(noMatchSubject, "noMatchSubject");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
      Objects.requireNonNull(rows, "rows");
      columns = List.copyOf(columns);
      Objects.requireNonNull(rowProjection, "rowProjection");
      Objects.requireNonNull(csvProjection, "csvProjection");
    }
  }
}
