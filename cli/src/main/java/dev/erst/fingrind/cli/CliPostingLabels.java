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
      case NON_CLOSING_POSTINGS -> "Non-closing postings";
    };
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case OPENING_BALANCE -> "Opening accounting position";
      case INTERIM_RESULT_SWEEP -> "Interim result sweep";
      case FISCAL_YEAR_CLOSE -> "Fiscal-year close";
    };
  }

  static String displayPostingOriginKind(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case DIRECT_JOURNAL -> "Direct journal";
      case SALE -> "Sale";
      case EXPENSE -> "Expense";
      case OWNER_CONTRIBUTION -> "Owner contribution";
      case OWNER_WITHDRAWAL -> "Owner withdrawal";
      case OPENING_POSITION -> "Opening position";
      case REVERSAL -> "Reversal";
      case INTERIM_RESULT_SWEEP -> "Interim result sweep";
      case FISCAL_YEAR_CLOSE -> "Fiscal-year close";
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
