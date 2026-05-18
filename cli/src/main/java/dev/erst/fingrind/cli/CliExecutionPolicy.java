package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import java.util.Locale;
import java.util.Optional;

/** Shared CLI execution policy for failure-mode inference and typed exit-code mapping. */
final class CliExecutionPolicy {
  private CliExecutionPolicy() {}

  static OutputMode inferredFailureOutputMode(String[] args) {
    if (args.length == 0) {
      return OutputMode.HUMAN;
    }
    OutputMode inferred = defaultFailureOutputMode(args[0]);
    int index = 1;
    while (index + 1 < args.length) {
      if (!ProtocolOptions.OUTPUT.equals(args[index])) {
        index++;
        continue;
      }
      Optional<OutputMode> parsedOutputMode = parseRecognizedOutputMode(args[index + 1]);
      if (parsedOutputMode.isPresent()) {
        inferred = parsedOutputMode.orElseThrow();
      }
      index += 2;
    }
    return inferred == OutputMode.HUMAN ? OutputMode.HUMAN : OutputMode.JSON;
  }

  private static OutputMode defaultFailureOutputMode(String commandToken) {
    String normalized = commandToken.toLowerCase(Locale.ROOT);
    if (OperationId.PRINT_REQUEST_TEMPLATE.wireName().equals(normalized)
        || OperationId.PRINT_PLAN_TEMPLATE.wireName().equals(normalized)) {
      return OutputMode.JSON;
    }
    return switch (normalized) {
      case "help", "version", "capabilities" -> CliOutputModeDefaults.defaultDiscoveryOutputMode();
      default -> CliOutputModeDefaults.defaultSelectableOutputMode();
    };
  }

  private static Optional<OutputMode> parseRecognizedOutputMode(String rawOutputMode) {
    for (OutputMode outputMode : OutputMode.values()) {
      if (outputMode.wireValue().equals(rawOutputMode)) {
        return Optional.of(outputMode);
      }
    }
    return Optional.empty();
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

  static int exitCodeFor(ClosePeriodResult result) {
    return switch (result) {
      case ClosePeriodResult.Closed _ -> 0;
      case ClosePeriodResult.Rejected _ -> 2;
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

  static int exitCodeFor(FinancialPositionResult result) {
    return switch (result) {
      case FinancialPositionResult.Reported _ -> 0;
      case FinancialPositionResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(IncomeStatementResult result) {
    return switch (result) {
      case IncomeStatementResult.Reported _ -> 0;
      case IncomeStatementResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(ChangesInEquityResult result) {
    return switch (result) {
      case ChangesInEquityResult.Reported _ -> 0;
      case ChangesInEquityResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RekeyBookResult result) {
    return switch (result) {
      case RekeyBookResult.Rekeyed _ -> 0;
      case RekeyBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(BackupBookResult result) {
    return switch (result) {
      case BackupBookResult.BackedUp _ -> 0;
      case BackupBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RestoreBookResult result) {
    return switch (result) {
      case RestoreBookResult.Restored _ -> 0;
      case RestoreBookResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(RecoverRekeyResult result) {
    return switch (result) {
      case RecoverRekeyResult.Inspected _ -> 0;
      case RecoverRekeyResult.Restored _ -> 0;
      case RecoverRekeyResult.Deleted _ -> 0;
      case RecoverRekeyResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(LedgerPlanResult result) {
    return switch (result) {
      case LedgerPlanResult.Succeeded _ -> 0;
      case LedgerPlanResult.Rejected _ -> 2;
      case LedgerPlanResult.AssertionFailed _ -> 3;
    };
  }

  static int invalidInvocationExitCode() {
    return 1;
  }

  static int deterministicFailureExitCode() {
    return 2;
  }

  static int runtimeFailureExitCode() {
    return 4;
  }
}
