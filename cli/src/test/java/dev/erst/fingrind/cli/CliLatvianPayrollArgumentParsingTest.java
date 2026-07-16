package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks the Latvian payroll command grammar to its report and request-bound command families. */
class CliLatvianPayrollArgumentParsingTest {
  private static final Path BOOK_FILE = Path.of("book.sqlite");
  private static final Path BOOK_KEY_FILE = Path.of("book.key");

  @Test
  void payrollCommands_parseReportPresentationAndEveryPostingMutation() {
    LatvianPayrollRegister defaultReport = parseRegister();
    LatvianPayrollRegister artifactReport =
        parseRegister("--output", "json", "--pdf-out", "reports/payroll-register.pdf");

    assertEquals(OutputMode.TEXT, defaultReport.output().outputMode());
    assertEquals(OutputMode.JSON, artifactReport.output().outputMode());
    assertEquals(Path.of("reports/payroll-register.pdf"), artifactReport.output().pdfOutPath());
    assertInstanceOf(
        RecordEntry.class,
        CliPostingMutationArguments.parseRecordEntryCommand(
            requestArguments("record-latvian-monthly-payroll"),
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL));
    assertInstanceOf(
        RecordEntry.class,
        CliPostingMutationArguments.parseRecordEntryCommand(
            requestArguments("record-latvian-payroll-net-wage-settlement"),
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT));
    assertInstanceOf(
        RecordEntry.class,
        CliPostingMutationArguments.parseRecordEntryCommand(
            requestArguments("record-latvian-payroll-state-remittance"),
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE));
  }

  @Test
  void payrollRegister_rejectsDuplicateAndUnsupportedPresentationArguments() {
    assertInvalid("--output", "text", "--output", "json");
    assertInvalid("--pdf-out", "one.pdf", "--pdf-out", "two.pdf");
    assertInvalid("--as-of", "2026-07-31");
  }

  private static LatvianPayrollRegister parseRegister(String... reportArguments) {
    String[] arguments = new String[5 + reportArguments.length];
    arguments[0] = "latvian-payroll-register";
    arguments[1] = "--book-file";
    arguments[2] = BOOK_FILE.toString();
    arguments[3] = "--book-key-file";
    arguments[4] = BOOK_KEY_FILE.toString();
    System.arraycopy(reportArguments, 0, arguments, 5, reportArguments.length);
    return assertInstanceOf(LatvianPayrollRegister.class, CliArguments.parse(arguments));
  }

  private static List<String> requestArguments(String command) {
    return List.of(
        command,
        "--book-file",
        BOOK_FILE.toString(),
        "--book-key-file",
        BOOK_KEY_FILE.toString(),
        "--request-file",
        "request.json");
  }

  private static void assertInvalid(String... reportArguments) {
    assertThrows(CliArgumentsException.class, () -> parseRegister(reportArguments));
  }
}
