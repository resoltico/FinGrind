package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;

/** Exit-code mapping for posting and ledger-plan execution commands. */
final class CliPostingExitCodes {
  private CliPostingExitCodes() {}

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

  static int exitCodeFor(LedgerPlanResult result) {
    return switch (result) {
      case LedgerPlanResult.Succeeded _ -> 0;
      case LedgerPlanResult.Rejected _ -> 2;
      case LedgerPlanResult.AssertionFailed _ -> 3;
    };
  }
}
