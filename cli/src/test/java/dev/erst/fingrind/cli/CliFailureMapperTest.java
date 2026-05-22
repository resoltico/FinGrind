package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FinGrindCli}. */
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
    assertNull(argumentFailure.details());
    assertNull(requestFailure.details());
  }

  @Test
  void cliFailure_preservesStructuredInvalidRequestDetails() {
    CliFailure requestFailure =
        CliFailureMapper.cliFailure(
            new CliRequestException(
                "invalid-request",
                "bad request",
                "fix request",
                null,
                new CliErrorJsonModels.InvalidRequestDetails(List.of("Problem one."))));
    assertEquals(
        List.of("Problem one."),
        assertInstanceOf(CliErrorJsonModels.InvalidRequestDetails.class, requestFailure.details())
            .violations());
  }

  @Test
  void cliFailure_preservesStructuredInvalidJsonDetails() {
    CliFailure requestFailure =
        CliFailureMapper.cliFailure(
            new CliRequestException(
                "invalid-request",
                "bad request",
                "fix request",
                null,
                new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 4, 12)));
    CliErrorJsonModels.InvalidJsonDetails details =
        assertInstanceOf(CliErrorJsonModels.InvalidJsonDetails.class, requestFailure.details());
    assertEquals("Unexpected token", details.parseMessage());
    assertEquals(4, details.line());
    assertEquals(12, details.column());
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
    assertTrue(
        java.util.Objects.requireNonNull(runtimeFailure.hint())
            .contains("underlying runtime problem"));
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
    assertTrue(java.util.Objects.requireNonNull(runtimeFailure.hint()).contains("--pdf-out"));
  }

  @Test
  void cliExecutionPolicy_mapsFailureFamiliesToDistinctExitCodes() {
    assertEquals(
        1,
        CliExecutionPolicy.contractFailureExitCode(
            ContractErrors.Descriptor.INVALID_REQUEST.failure(
                "Invalid request.", "Repair the input.", "--request-file")));
    assertEquals(
        5,
        CliExecutionPolicy.contractFailureExitCode(
            ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.failure(
                "Interactive prompt unavailable.", "Use stdin or a key file.", null)));
    assertEquals(
        6,
        CliExecutionPolicy.contractFailureExitCode(
            ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                "Verification failed.", "Check the passphrase or key file.", "--book-key-file")));
    assertEquals(
        7,
        CliExecutionPolicy.contractFailureExitCode(
            ContractErrors.Descriptor.BOOK_KEY_FILE_ALREADY_EXISTS.failure(
                "Key file already exists.",
                "Choose a new destination.",
                "--backup-book-key-file-out")));
    assertEquals(
        4,
        CliExecutionPolicy.contractFailureExitCode(
            ContractErrors.Descriptor.RUNTIME_FAILURE.failure(
                "Runtime failure.", "Inspect the runtime diagnostics.", null)));
    assertEquals(
        4,
        CliExecutionPolicy.failureExitCode(new CliFailure("custom-runtime", "boom", null, null)));
  }

  @Test
  void outputSelectionHelpers_coverTemplateAndMalformedOutputBranches() {
    BookAccess bookAccess =
        new BookAccess(
            Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")));
    CliCommand.ReportOutput humanReport = new CliCommand.ReportOutput(OutputMode.HUMAN, null);
    assertEquals(
        OutputMode.HUMAN,
        new Help(null, OutputMode.HUMAN, DiscoveryDetail.COMPACT).failureOutputMode());
    assertEquals(
        OutputMode.JSON,
        new Capabilities(OutputMode.JSON, DiscoveryDetail.COMPACT).failureOutputMode());
    assertEquals(OutputMode.HUMAN, new Version(OutputMode.HUMAN).failureOutputMode());
    assertEquals(OutputMode.JSON, new PrintRequestTemplate().failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new GenerateBookKeyFile(Path.of("book.key"), OutputMode.HUMAN).failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new OpenBook(bookAccess, openBookCommand(), OutputMode.HUMAN).failureOutputMode());
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
                bookAccess, AccountBalanceQuery.unbounded(new AccountCode("1000")), humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new TrialBalance(
                bookAccess, new TrialBalanceQuery(Optional.empty(), allPostingKinds()), humanReport)
            .failureOutputMode());
    assertEquals(
        OutputMode.HUMAN,
        new AccountLedger(
                bookAccess, AccountLedgerQuery.unbounded(new AccountCode("1000")), humanReport)
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
        OutputMode.JSON,
        new ExecutePlan(bookAccess, Path.of("plan.json"), PlanResultDetail.SUMMARY)
            .failureOutputMode());
    assertEquals(OutputMode.HUMAN, CliExecutionPolicy.inferredFailureOutputMode(new String[0]));
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
