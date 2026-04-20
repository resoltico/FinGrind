package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parsed CLI command model for one FinGrind process invocation. */
sealed interface CliCommand
    permits CliCommand.OutputModeCommand, CliCommand.JsonFailureCommand, CliCommand.ReportCommand {
  /** Returns the operator-facing output mode to use for deterministic failures. */
  OutputMode failureOutputMode();

  /** Command family whose deterministic failures follow one explicit `--output` selection. */
  sealed interface OutputModeCommand extends CliCommand
      permits Help,
          Version,
          Capabilities,
          GenerateBookKeyFile,
          OpenBook,
          RekeyBook,
          DeclareAccount,
          InspectBook,
          ListAccounts,
          GetPosting,
          ListPostings,
          PreflightEntry,
          PostEntry {
    /** Selected operator-facing output mode for this command. */
    OutputMode outputMode();

    @Override
    default OutputMode failureOutputMode() {
      return outputMode();
    }
  }

  /** Command family whose deterministic failures are always JSON machine envelopes. */
  sealed interface JsonFailureCommand extends CliCommand
      permits PrintRequestTemplate, PrintPlanTemplate, ExecutePlan {
    @Override
    default OutputMode failureOutputMode() {
      return OutputMode.JSON;
    }
  }

  /** Command family whose successful output can target either terminal text/JSON or a PDF file. */
  sealed interface ReportCommand extends CliCommand
      permits AccountBalance, TrialBalance, AccountLedger, PeriodSummary {
    /** Selected report presentation settings for this command. */
    ReportOutput output();

    @Override
    default OutputMode failureOutputMode() {
      return output().outputMode();
    }
  }

  /** Shared report-presentation settings for one successful report command. */
  record ReportOutput(OutputMode outputMode, @Nullable Path pdfOutPath) {
    public ReportOutput {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests the FinGrind help payload. */
  record Help(OutputMode outputMode) implements OutputModeCommand {
    public Help {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests the FinGrind version payload. */
  record Version(OutputMode outputMode) implements OutputModeCommand {
    public Version {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests the current capability summary payload. */
  record Capabilities(OutputMode outputMode) implements OutputModeCommand {
    public Capabilities {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests a minimal valid posting request JSON document. */
  record PrintRequestTemplate() implements JsonFailureCommand {}

  /** Requests a minimal valid AI-agent ledger-plan JSON document. */
  record PrintPlanTemplate() implements JsonFailureCommand {}

  /** Creates one new owner-only generated book key file. */
  record GenerateBookKeyFile(Path bookKeyFilePath, OutputMode outputMode)
      implements OutputModeCommand {
    public GenerateBookKeyFile {
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Explicitly initializes one book file with the canonical schema. */
  record OpenBook(BookAccess bookAccess, OutputMode outputMode) implements OutputModeCommand {
    public OpenBook {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Rotates the passphrase that protects one existing book file. */
  record RekeyBook(
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode)
      implements OutputModeCommand {
    public RekeyBook {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Declares or reactivates one account in the selected book. */
  record DeclareAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements OutputModeCommand {
    public DeclareAccount {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Inspects the selected book file for lifecycle and compatibility state. */
  record InspectBook(BookAccess bookAccess, OutputMode outputMode) implements OutputModeCommand {
    public InspectBook {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Lists the account registry for the selected book. */
  record ListAccounts(BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode)
      implements OutputModeCommand {
    public ListAccounts {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Returns one committed posting by its durable identity. */
  record GetPosting(BookAccess bookAccess, PostingId postingId, OutputMode outputMode)
      implements OutputModeCommand {
    public GetPosting {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(postingId, "postingId");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Lists one filtered page of committed postings. */
  record ListPostings(BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode)
      implements OutputModeCommand {
    public ListPostings {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Computes per-currency balances for one declared account. */
  record AccountBalance(BookAccess bookAccess, AccountBalanceQuery query, ReportOutput output)
      implements ReportCommand {
    public AccountBalance {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(output, "output");
    }
  }

  /** Computes one book-wide trial balance. */
  record TrialBalance(BookAccess bookAccess, TrialBalanceQuery query, ReportOutput output)
      implements ReportCommand {
    public TrialBalance {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(output, "output");
    }
  }

  /** Computes one running ledger for the selected account. */
  record AccountLedger(BookAccess bookAccess, AccountLedgerQuery query, ReportOutput output)
      implements ReportCommand {
    public AccountLedger {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(output, "output");
    }
  }

  /** Computes one bounded period summary. */
  record PeriodSummary(BookAccess bookAccess, PeriodSummaryQuery query, ReportOutput output)
      implements ReportCommand {
    public PeriodSummary {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(query, "query");
      Objects.requireNonNull(output, "output");
    }
  }

  /** Executes one ordered AI-agent ledger plan transaction. */
  record ExecutePlan(BookAccess bookAccess, Path requestFile) implements JsonFailureCommand {
    public ExecutePlan {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
    }
  }

  /** Requests preflight validation for one book-backed posting request. */
  record PreflightEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements OutputModeCommand {
    public PreflightEntry {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests commit execution for one book-backed posting request. */
  record PostEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements OutputModeCommand {
    public PostEntry {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }
}
