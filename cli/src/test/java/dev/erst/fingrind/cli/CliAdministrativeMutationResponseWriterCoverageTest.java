package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises every administrative mutation transport branch through public outcome objects. */
class CliAdministrativeMutationResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  private static final Path BOOK_PATH = Path.of("books", "current.sqlite");
  private static final Path KEY_PATH = Path.of("keys", "current.key");

  @Test
  void writesBookOpeningKeyGenerationAndRekeyOutcomesAcrossEveryOutputFamily() {
    ByteArrayOutputStream openedJson = new ByteArrayOutputStream();
    writer(openedJson)
        .writeOpenBookResult(
            BOOK_PATH,
            List.of(Path.of("books")),
            new OpenBookResult.Opened(Instant.parse("2026-07-21T10:20:30Z"), bookIdentity()),
            OutputMode.JSON);
    assertJsonContains(openedJson, "\"entityName\":\"Acme Studio\"");

    ByteArrayOutputStream openedText = new ByteArrayOutputStream();
    writer(openedText)
        .writeOpenBookResult(
            BOOK_PATH,
            List.of(Path.of("books")),
            new OpenBookResult.Opened(Instant.parse("2026-07-21T10:20:30Z"), bookIdentity()),
            OutputMode.TEXT);
    String opened = openedText.toString(StandardCharsets.UTF_8);
    assertTrue(opened.contains("Book Initialized"));
    assertTrue(opened.contains("Tightened parent directory"));

    ByteArrayOutputStream rejectedOpen = new ByteArrayOutputStream();
    writer(rejectedOpen)
        .writeOpenBookResult(
            BOOK_PATH,
            List.of(),
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedOpen, "\"code\":\"book-already-initialized\"");

    GeneratedBookKeyFile generatedKeyFile =
        new GeneratedBookKeyFile(KEY_PATH, "base64", 256, "rw-------");
    ByteArrayOutputStream generatedKeyJson = new ByteArrayOutputStream();
    writer(generatedKeyJson)
        .writeGenerateBookKeyFileResult(
            generatedKeyFile, List.of(Path.of("keys")), OutputMode.JSON);
    assertJsonContains(generatedKeyJson, "\"entropyBits\":256");

    ByteArrayOutputStream generatedKeyText = new ByteArrayOutputStream();
    writer(generatedKeyText)
        .writeGenerateBookKeyFileResult(
            generatedKeyFile, List.of(Path.of("keys")), OutputMode.TEXT);
    String generatedKey = generatedKeyText.toString(StandardCharsets.UTF_8);
    assertTrue(generatedKey.contains("Book Key File Generated"));
    assertTrue(generatedKey.contains("Tightened parent directory"));

    ByteArrayOutputStream rekeyedJson = new ByteArrayOutputStream();
    writer(rekeyedJson)
        .writeRekeyBookResult(new RekeyBookResult.Rekeyed(BOOK_PATH), KEY_PATH, OutputMode.JSON);
    assertJsonContains(rekeyedJson, "\"status\":\"ok\"");

    ByteArrayOutputStream rekeyedText = new ByteArrayOutputStream();
    writer(rekeyedText)
        .writeRekeyBookResult(new RekeyBookResult.Rekeyed(BOOK_PATH), KEY_PATH, OutputMode.TEXT);
    assertTrue(rekeyedText.toString(StandardCharsets.UTF_8).contains("Book Rekeyed"));

    ByteArrayOutputStream rejectedRekey = new ByteArrayOutputStream();
    writer(rejectedRekey)
        .writeRekeyBookResult(
            new RekeyBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite"))),
            KEY_PATH,
            OutputMode.JSON);
    assertJsonContains(rejectedRekey, "\"code\":\"backup-destination-already-exists\"");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeOpenBookResult(
                    BOOK_PATH,
                    List.of(),
                    new OpenBookResult.Opened(
                        Instant.parse("2026-07-21T10:20:30Z"), bookIdentity()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeGenerateBookKeyFileResult(generatedKeyFile, List.of(), OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeRekeyBookResult(
                    new RekeyBookResult.Rekeyed(BOOK_PATH), KEY_PATH, OutputMode.CSV));

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new OpenBookResult.Opened(Instant.parse("2026-07-21T10:20:30Z"), bookIdentity())));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized())));
    assertEquals(0, CliAdministrativeExitCodes.exitCodeFor(new RekeyBookResult.Rekeyed(BOOK_PATH)));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite")))));
  }

  @Test
  void writesCloseOutcomesAndDelegatedAccountDeclarationWithoutLosingOutputPolicy() {
    ByteArrayOutputStream sweepJson = new ByteArrayOutputStream();
    writer(sweepJson)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult()), OutputMode.JSON);
    assertJsonContains(sweepJson, "\"sweepOrder\":1");

    ByteArrayOutputStream sweepText = new ByteArrayOutputStream();
    writer(sweepText)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult()), OutputMode.TEXT);
    assertTrue(sweepText.toString(StandardCharsets.UTF_8).contains("Interim Result Swept"));

    ByteArrayOutputStream rejectedSweep = new ByteArrayOutputStream();
    writer(rejectedSweep)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedSweep, "\"code\":\"administration-book-not-initialized\"");

    ByteArrayOutputStream closeJson = new ByteArrayOutputStream();
    writer(closeJson)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), false), OutputMode.JSON);
    assertJsonContains(closeJson, "\"idempotentReplay\":false");

    ByteArrayOutputStream closeText = new ByteArrayOutputStream();
    writer(closeText)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), true), OutputMode.TEXT);
    assertTrue(closeText.toString(StandardCharsets.UTF_8).contains("Fiscal Year Already Closed"));

    ByteArrayOutputStream rejectedClose = new ByteArrayOutputStream();
    writer(rejectedClose)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedClose, "\"code\":\"administration-book-not-initialized\"");

    ByteArrayOutputStream accountJson = new ByteArrayOutputStream();
    writer(accountJson)
        .writeDeclareAccountResult(
            new DeclareAccountResult.Declared(declaredCashAccount()), OutputMode.JSON);
    assertJsonContains(accountJson, "\"outcome\":\"declared\"");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeInterimResultSweepResult(
                    new InterimResultSweepResult.Swept(sampleSweptInterimResult()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeFiscalYearCloseResult(
                    new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), false),
                    OutputMode.CSV));

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult())));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new InterimResultSweepResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), false)));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized())));
  }

  private static CliAdministrativeMutationResponseWriter writer(
      ByteArrayOutputStream outputStream) {
    return new CliAdministrativeMutationResponseWriter(outputChannel(outputStream));
  }

  private static SweptInterimResult sampleSweptInterimResult() {
    return new SweptInterimResult(
        1,
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        new AccountCode("3200"),
        List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"))),
        Instant.parse("2026-04-30T12:00:00Z"),
        List.of(new PostingId("98be232b-af01-324d-b4fc-6f62636fae68")));
  }

  private static ClosedFiscalYear sampleClosedFiscalYear() {
    return new ClosedFiscalYear(
        1,
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        new AccountCode("3000"),
        new AccountCode("3200"),
        new AccountCode("3300"),
        Instant.parse("2026-12-31T12:00:00Z"),
        List.of(
            new PostingId("98be232b-af01-324d-b4fc-6f62636fae68"),
            new PostingId("548200b1-9743-3000-a75c-17a99ebf79b7")));
  }
}
