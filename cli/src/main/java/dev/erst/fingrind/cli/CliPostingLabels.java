package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;

/** Shared operator-facing labels and totals for posting facts. */
final class CliPostingLabels {
  private CliPostingLabels() {}

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-transfer postings";
    };
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_RESULT_TRANSFER -> "Period result transfer";
      case OPENING_BALANCE -> "Opening accounting position";
    };
  }

  static String displayPostingOriginKind(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case JOURNAL -> "Journal";
      case CASH_REVENUE -> "Cash revenue";
      case CASH_EXPENSE -> "Cash expense";
      case EQUITY_CONTRIBUTION -> "Equity contribution";
      case EQUITY_WITHDRAWAL -> "Equity withdrawal";
      case OPEN_ACCOUNTING_POSITION -> "Opening accounting position";
      case REVERSAL_ADJUSTMENT -> "Reversal adjustment";
      case PERIOD_RESULT_TRANSFER -> "Result transfer";
    };
  }

  static String displayPostingRoleText(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String reversalStateWireValue(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "reversal" : "direct";
  }

  static String reversalTargetText(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(not a reversal)");
  }

  static String reversalTargetCsv(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("");
  }

  static String postingCurrency(PostingFact postingFact) {
    return postingFact.journalEntry().currencyUnit().code();
  }

  static String postingDebitTotal(PostingFact postingFact) {
    long debitTotalMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return CliTextFormat.displayMoney(
        Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), debitTotalMinorUnits));
  }

  static String postingCreditTotal(PostingFact postingFact) {
    long creditTotalMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.CREDIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return CliTextFormat.displayMoney(
        Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), creditTotalMinorUnits));
  }
}
