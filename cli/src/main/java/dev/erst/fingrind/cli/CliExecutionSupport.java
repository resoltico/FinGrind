package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;

/** Shared CLI execution policy for failure-mode inference and typed exit-code mapping. */
final class CliExecutionSupport {
  private CliExecutionSupport() {}

  static OutputMode inferredFailureOutputMode(String[] args) {
    if (args.length == 0) {
      return OutputMode.HUMAN;
    }
    OutputMode inferred =
        ProtocolCatalog.findByToken(args[0])
            .map(
                operation ->
                    switch (operation.id()) {
                      case HELP, CAPABILITIES, VERSION -> OutputMode.HUMAN;
                      case PRINT_REQUEST_TEMPLATE,
                          PRINT_PLAN_TEMPLATE,
                          GENERATE_BOOK_KEY_FILE,
                          OPEN_BOOK,
                          REKEY_BOOK,
                          DECLARE_ACCOUNT,
                          INSPECT_BOOK,
                          LIST_ACCOUNTS,
                          GET_POSTING,
                          LIST_POSTINGS,
                          ACCOUNT_BALANCE,
                          TRIAL_BALANCE,
                          ACCOUNT_LEDGER,
                          PERIOD_SUMMARY,
                          EXECUTE_PLAN,
                          PREFLIGHT_ENTRY,
                          POST_ENTRY ->
                          OutputMode.JSON;
                    })
            .orElse(OutputMode.JSON);
    int index = 1;
    while (index + 1 < args.length) {
      if (!ProtocolOptions.OUTPUT.equals(args[index])) {
        index++;
        continue;
      }
      OutputMode parsedOutputMode = parseRecognizedOutputMode(args[index + 1]);
      if (parsedOutputMode != null) {
        inferred = parsedOutputMode;
      }
      index += 2;
    }
    return inferred == OutputMode.HUMAN ? OutputMode.HUMAN : OutputMode.JSON;
  }

  private static @org.jspecify.annotations.Nullable OutputMode parseRecognizedOutputMode(
      String rawOutputMode) {
    for (OutputMode outputMode : OutputMode.values()) {
      if (outputMode.wireValue().equals(rawOutputMode)) {
        return outputMode;
      }
    }
    return null;
  }

  static int exitCodeFor(PreflightEntryResult result) {
    return switch (result) {
      case PostEntryResult.PreflightAccepted _ -> 0;
      case PostEntryResult.PreflightRejected _ -> 2;
    };
  }

  static int exitCodeFor(CommitEntryResult result) {
    return switch (result) {
      case PostEntryResult.Committed _ -> 0;
      case PostEntryResult.CommitRejected _ -> 2;
    };
  }

  static int exitCodeFor(OpenBookResult result) {
    return switch (result) {
      case OpenBookResult.Opened _ -> 0;
      case OpenBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared _ -> 0;
      case DeclareAccountResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(ListAccountsResult result) {
    return switch (result) {
      case ListAccountsResult.Listed _ -> 0;
      case ListAccountsResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(GetPostingResult result) {
    return switch (result) {
      case GetPostingResult.Found _ -> 0;
      case GetPostingResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(ListPostingsResult result) {
    return switch (result) {
      case ListPostingsResult.Listed _ -> 0;
      case ListPostingsResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(AccountBalanceResult result) {
    return switch (result) {
      case AccountBalanceResult.Reported _ -> 0;
      case AccountBalanceResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(TrialBalanceResult result) {
    return switch (result) {
      case TrialBalanceResult.Reported _ -> 0;
      case TrialBalanceResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(AccountLedgerResult result) {
    return switch (result) {
      case AccountLedgerResult.Reported _ -> 0;
      case AccountLedgerResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(PeriodSummaryResult result) {
    return switch (result) {
      case PeriodSummaryResult.Reported _ -> 0;
      case PeriodSummaryResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RekeyBookResult result) {
    return switch (result) {
      case RekeyBookResult.Rekeyed _ -> 0;
      case RekeyBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(LedgerPlanResult result) {
    return switch (result) {
      case LedgerPlanResult.Succeeded _ -> 0;
      case LedgerPlanResult.Rejected _ -> 2;
      case LedgerPlanResult.AssertionFailed _ -> 3;
    };
  }
}
