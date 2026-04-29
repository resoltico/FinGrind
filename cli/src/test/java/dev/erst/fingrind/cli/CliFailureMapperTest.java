package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class CliFailureMapperTest extends FinGrindCliTestSupport {
  @Test
  void cliFailure_mapsSealedCliCommandExceptions() {
    CliFailure argumentFailure =
        CliFailureMapper.cliFailure(
            new CliArgumentsException("invalid-argument", "--flag", "bad flag", "fix flag"));
    CliFailure requestFailure =
        CliFailureMapper.cliFailure(
            new CliRequestException("invalid-request", "bad request", "fix request", null));

    assertEquals("invalid-argument", argumentFailure.code());
    assertEquals("--flag", argumentFailure.argument());
    assertEquals("invalid-request", requestFailure.code());
    assertNull(requestFailure.argument());
  }

  @Test
  void cliFailureMapper_coversContractAndDefaultRuntimeMessages() {
    CliFailure contractFailure =
        CliFailureMapper.contractFailure(
            new ContractFailure(
                ContractErrors.Descriptor.INVALID_REQUEST,
                "bad request",
                "fix request",
                "--book-file"));
    CliFailure runtimeFailure = CliFailureMapper.runtimeFailure(new RuntimeException());

    assertEquals(ContractErrors.Descriptor.INVALID_REQUEST.code(), contractFailure.code());
    assertEquals("--book-file", contractFailure.argument());
    assertEquals(ContractErrors.Descriptor.RUNTIME_FAILURE.code(), runtimeFailure.code());
    assertEquals("CLI command failed.", runtimeFailure.message());
    assertTrue(runtimeFailure.hint().contains("underlying runtime problem"));
    assertEquals(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        CliFailureMapper.runtimeFailure(
                new ContractFailureException(
                    ContractErrors.Descriptor.INVALID_REQUEST.failure(
                        "deterministic", "fix deterministic input", "--request-file")))
            .code());
  }

  @Test
  void cliFailureMapper_mapsPdfExportFailuresToPdfArgument() {
    CliFailure runtimeFailure =
        CliFailureMapper.runtimeFailure(
            new CliPdfExportException(
                Path.of("reports", "trial-balance.pdf"), new IOException("disk full")));

    assertEquals(ContractErrors.Descriptor.PDF_EXPORT_FAILURE.code(), runtimeFailure.code());
    assertEquals("--pdf-out", runtimeFailure.argument());
    assertTrue(runtimeFailure.message().contains("trial-balance.pdf"));
    assertTrue(runtimeFailure.hint().contains("--pdf-out"));
  }

  @Test
  void outputSelectionHelpers_coverTemplateAndMalformedOutputBranches() {
    BookAccess bookAccess =
        new BookAccess(
            Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")));
    CliCommand.ReportOutput humanReport = new CliCommand.ReportOutput(OutputMode.HUMAN, null);

    assertEquals(OutputMode.HUMAN, new Help(null, OutputMode.HUMAN).failureOutputMode());
    assertEquals(OutputMode.JSON, new Capabilities(OutputMode.JSON).failureOutputMode());
    assertEquals(OutputMode.HUMAN, new Version(OutputMode.HUMAN).failureOutputMode());
    assertEquals(OutputMode.JSON, new PrintRequestTemplate().failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new GenerateBookKeyFile(Path.of("book.key"), OutputMode.HUMAN).failureOutputMode());
    assertEquals(OutputMode.HUMAN, new OpenBook(bookAccess, OutputMode.HUMAN).failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new RekeyBook(
                bookAccess,
                new BookAccess.PassphraseSource.KeyFile(Path.of("replacement.key")),
                OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new DeclareAccount(bookAccess, Path.of("declare-account.json"), OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN, new InspectBook(bookAccess, OutputMode.HUMAN).failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new ListAccounts(bookAccess, new ListAccountsQuery(20, Optional.empty()), OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new GetPosting(bookAccess, new PostingId("posting-1"), OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new ListPostings(
                bookAccess,
                new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty()),
                OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new AccountBalance(
                bookAccess,
                new AccountBalanceQuery(new AccountCode("1000"), null, null),
                humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new TrialBalance(bookAccess, new TrialBalanceQuery(Optional.empty()), humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new AccountLedger(
                bookAccess,
                new AccountLedgerQuery(new AccountCode("1000"), null, null),
                humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new PeriodSummary(
                bookAccess,
                new PeriodSummaryQuery(
                    LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new PreflightEntry(bookAccess, Path.of("request.json"), OutputMode.HUMAN)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new PostEntry(bookAccess, Path.of("request.json"), OutputMode.HUMAN).failureOutputMode());
    assertEquals(OutputMode.JSON, new PrintPlanTemplate().failureOutputMode());
    assertEquals(
        OutputMode.JSON, new ExecutePlan(bookAccess, Path.of("plan.json")).failureOutputMode());
    assertEquals(
        OutputMode.JSON,
        CliExecutionPolicy.inferredFailureOutputMode(
            new String[] {
              "open-book",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output",
              "not-a-real-mode"
            }));
    assertEquals(
        OutputMode.HUMAN,
        CliExecutionPolicy.inferredFailureOutputMode(
            new String[] {
              "open-book",
              "--book-file",
              "book.sqlite",
              "--book-key-file",
              "book.key",
              "--output",
              "human"
            }));
  }
}
