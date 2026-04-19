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
sealed interface CliCommand {
  /** Returns the operator-facing output mode to use for deterministic failures. */
  OutputMode failureOutputMode();

  /** Shared report-presentation settings for one successful report command. */
  record ReportOutput(OutputMode outputMode, @Nullable Path pdfOutPath) {
    public ReportOutput {
      Objects.requireNonNull(outputMode, "outputMode");
    }
  }

  /** Requests the FinGrind help payload. */
  record Help(OutputMode outputMode) implements CliCommand {
    public Help {
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Requests the FinGrind version payload. */
  record Version(OutputMode outputMode) implements CliCommand {
    public Version {
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Requests the current capability summary payload. */
  record Capabilities(OutputMode outputMode) implements CliCommand {
    public Capabilities {
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Requests a minimal valid posting request JSON document. */
  record PrintRequestTemplate() implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return OutputMode.JSON;
    }
  }

  /** Requests a minimal valid AI-agent ledger-plan JSON document. */
  record PrintPlanTemplate() implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return OutputMode.JSON;
    }
  }

  /** Creates one new owner-only generated book key file. */
  record GenerateBookKeyFile(Path bookKeyFilePath, OutputMode outputMode) implements CliCommand {
    public GenerateBookKeyFile {
      Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Explicitly initializes one book file with the canonical schema. */
  record OpenBook(BookAccess bookAccess, OutputMode outputMode) implements CliCommand {
    public OpenBook {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Rotates the passphrase that protects one existing book file. */
  record RekeyBook(
      BookAccess bookAccess,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode)
      implements CliCommand {
    public RekeyBook {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(replacementPassphraseSource, "replacementPassphraseSource");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Declares or reactivates one account in the selected book. */
  record DeclareAccount(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements CliCommand {
    public DeclareAccount {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Inspects the selected book file for lifecycle and compatibility state. */
  record InspectBook(BookAccess bookAccess, OutputMode outputMode) implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Lists the account registry for the selected book. */
  record ListAccounts(BookAccess bookAccess, ListAccountsQuery query, OutputMode outputMode)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Returns one committed posting by its durable identity. */
  record GetPosting(BookAccess bookAccess, PostingId postingId, OutputMode outputMode)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Lists one filtered page of committed postings. */
  record ListPostings(BookAccess bookAccess, ListPostingsQuery query, OutputMode outputMode)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Computes per-currency balances for one declared account. */
  record AccountBalance(BookAccess bookAccess, AccountBalanceQuery query, ReportOutput output)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return output.outputMode();
    }
  }

  /** Computes one book-wide trial balance. */
  record TrialBalance(BookAccess bookAccess, TrialBalanceQuery query, ReportOutput output)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return output.outputMode();
    }
  }

  /** Computes one running ledger for the selected account. */
  record AccountLedger(BookAccess bookAccess, AccountLedgerQuery query, ReportOutput output)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return output.outputMode();
    }
  }

  /** Computes one bounded period summary. */
  record PeriodSummary(BookAccess bookAccess, PeriodSummaryQuery query, ReportOutput output)
      implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return output.outputMode();
    }
  }

  /** Executes one ordered AI-agent ledger plan transaction. */
  record ExecutePlan(BookAccess bookAccess, Path requestFile) implements CliCommand {
    @Override
    public OutputMode failureOutputMode() {
      return OutputMode.JSON;
    }
  }

  /** Requests preflight validation for one book-backed posting request. */
  record PreflightEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements CliCommand {
    public PreflightEntry {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }

  /** Requests commit execution for one book-backed posting request. */
  record PostEntry(BookAccess bookAccess, Path requestFile, OutputMode outputMode)
      implements CliCommand {
    public PostEntry {
      Objects.requireNonNull(bookAccess, "bookAccess");
      Objects.requireNonNull(requestFile, "requestFile");
      Objects.requireNonNull(outputMode, "outputMode");
    }

    @Override
    public OutputMode failureOutputMode() {
      return outputMode;
    }
  }
}
