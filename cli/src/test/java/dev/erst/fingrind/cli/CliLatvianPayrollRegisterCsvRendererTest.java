package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.LatvianPayrollRegisterReportModelBuilder;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Locks the semantic CSV projection to the shared payroll-register report model. */
class CliLatvianPayrollRegisterCsvRendererTest {
  private static final Instant GENERATED_AT = Instant.parse("2026-07-15T10:00:00Z");

  @Test
  void render_matchesTheSharedModelForCompleteAndEmptyLifecycleScopes() {
    assertEquals(
        sharedCsv(ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport()),
        CliLatvianPayrollRegisterCsvRenderer.render(
            CliReportPayloadMapper.latvianPayrollRegister(
                ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport(),
                GENERATED_AT)));
    assertEquals(
        sharedCsv(ReportCrossFormatLatvianPayrollFixture.emptyLatvianPayrollRegisterReport()),
        CliLatvianPayrollRegisterCsvRenderer.render(
            CliReportPayloadMapper.latvianPayrollRegister(
                ReportCrossFormatLatvianPayrollFixture.emptyLatvianPayrollRegisterReport(),
                GENERATED_AT)));
  }

  @Test
  void render_preservesUnsettledAndReversedPayrollLineageWithoutInventingSettlements() {
    var report = ReportCrossFormatLatvianPayrollFixture.lifecycleLatvianPayrollRegisterReport();
    var payload = CliLatvianPayrollReportPayloadMapper.register(report, GENERATED_AT);
    String csv = CliLatvianPayrollRegisterCsvRenderer.render(payload);

    assertEquals(sharedCsv(report), csv);
    assertEquals("active", payload.rows().get(0).runStatus());
    assertTrue(payload.rows().get(0).settlements().isEmpty());
    assertEquals("reversed", payload.rows().get(1).runStatus());
    assertEquals(
        "2a645a3f-ac06-3a32-88bb-e7697a7af590", payload.rows().get(1).runReversalPostingId());
    assertEquals("reversed", payload.rows().get(1).settlements().getFirst().status());
    assertEquals(
        "b80b6a53-1882-3abd-94eb-14dbea809269",
        payload.rows().get(1).settlements().getFirst().reversalPostingId());
    assertTrue(csv.contains("unsettled"));
  }

  private static String sharedCsv(
      dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport report) {
    var projection =
        Objects.requireNonNull(
            LatvianPayrollRegisterReportModelBuilder.buildModel(report).tabularCsvProjection());
    return CliTextFormat.renderCsv(projection.headers(), projection.rows());
  }
}
