package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAccrualCutoffReportJsonModels;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the schedule CSV preserves blank optional lifecycle boundaries as blank cells. */
class CliAccrualCutoffScheduleCsvRendererTest {
  @Test
  void render_preservesAbsentRecognitionAndApplicationDatesAsBlankCells() {
    CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload mappedReport =
        CliReportPayloadMapper.accrualCutoffSchedule(
            ReportCrossFormatAccrualCutoffFixture.sampleAccrualCutoffScheduleReport(),
            Instant.parse("2026-05-01T10:00:00Z"));
    CliAccrualCutoffReportJsonModels.AccrualCutoffScheduleRowPayload sourceRow =
        mappedReport.rows().get(1);
    CliAccrualCutoffReportJsonModels.AccrualCutoffScheduleRowPayload rowWithoutApplication =
        new CliAccrualCutoffReportJsonModels.AccrualCutoffScheduleRowPayload(
            sourceRow.accrualCutoffId(),
            sourceRow.kind(),
            sourceRow.originatedOn(),
            sourceRow.cutoffAccountCode(),
            sourceRow.recognitionAccountCode(),
            sourceRow.originalAmount(),
            sourceRow.appliedAmount(),
            sourceRow.remainingAmount(),
            sourceRow.recognitionStartDate(),
            sourceRow.recognitionEndDate(),
            null);
    CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload report =
        new CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload(
            mappedReport.family(),
            mappedReport.bookIdentity(),
            mappedReport.resolvedQuery(),
            mappedReport.generatedAt(),
            List.of(rowWithoutApplication));

    String csv = CliAccrualCutoffScheduleCsvRenderer.render(report);

    assertTrue(
        csv.contains("recognitionStartDate,recognitionEndDate,latestApplicationEffectiveDate"));
    assertTrue(csv.lines().skip(1).findFirst().orElseThrow().endsWith(",,,"), csv);
  }
}
