package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationResult;

/** Exit-code mapping for non-report read commands. */
final class CliBookQueryExitCodes {
  private CliBookQueryExitCodes() {}

  static int exitCodeFor(ListAccountsResult result) {
    return switch (result) {
      case ListAccountsResult.Listed _ -> 0;
      case ListAccountsResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(ListTaxRegistrationsResult result) {
    return switch (result) {
      case ListTaxRegistrationsResult.Listed _ -> 0;
      case ListTaxRegistrationsResult.Rejected _ -> 2;
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

  static int exitCodeFor(TaxObligationResult result) {
    return switch (result) {
      case TaxObligationResult.Reported _ -> 0;
      case TaxObligationResult.Rejected _ -> 2;
    };
  }
}
