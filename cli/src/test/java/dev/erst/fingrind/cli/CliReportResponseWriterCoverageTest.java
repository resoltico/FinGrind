package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers report-writer guardrails that should remain unreachable after argument validation. */
class CliReportResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void writeTrialBalanceResult_rejectsCsvStdoutWhenPdfArtifactPathLeaksPastValidation() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                reportWriter(new ByteArrayOutputStream())
                    .writeTrialBalanceResult(
                        new TrialBalanceResult.Reported(
                            CliFixtureSupport.sampleTrialBalanceReport()),
                        OutputMode.CSV,
                        Path.of("reports/trial-balance.pdf")));

    assertEquals(
        "CSV stdout cannot be combined with --pdf-out after argument validation.",
        exception.getMessage());
  }
}
