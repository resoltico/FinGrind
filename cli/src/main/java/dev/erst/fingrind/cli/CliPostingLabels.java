package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.Map;
import java.util.Objects;

/** Shared operator-facing labels and totals for posting facts. */
final class CliPostingLabels {
  private static final Map<PostingOriginKind, String> POSTING_ORIGIN_LABELS =
      Map.ofEntries(
          Map.entry(PostingOriginKind.DIRECT_JOURNAL, "Direct journal"),
          Map.entry(PostingOriginKind.SALE_SETTLED, "Settled sale"),
          Map.entry(PostingOriginKind.SALE_ON_CREDIT, "Sale on credit"),
          Map.entry(PostingOriginKind.PURCHASE_SETTLED, "Settled purchase"),
          Map.entry(PostingOriginKind.PURCHASE_ON_CREDIT, "Purchase on credit"),
          Map.entry(
              PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED,
              "Settled inventory capitalization"),
          Map.entry(
              PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
              "Inventory capitalization on credit"),
          Map.entry(PostingOriginKind.INVENTORY_WRITE_DOWN, "Inventory write-down"),
          Map.entry(PostingOriginKind.INVENTORY_SHRINKAGE, "Inventory shrinkage"),
          Map.entry(PostingOriginKind.INVENTORY_COUNT_INCREASE, "Inventory count increase"),
          Map.entry(PostingOriginKind.PREPAYMENT, "Prepayment"),
          Map.entry(PostingOriginKind.DEFERRED_REVENUE, "Deferred revenue"),
          Map.entry(PostingOriginKind.ACCRUED_EXPENSE, "Accrued expense"),
          Map.entry(PostingOriginKind.ACCRUAL_CUTOFF_RECOGNITION, "Accrual cut-off recognition"),
          Map.entry(PostingOriginKind.ACCRUED_EXPENSE_SETTLEMENT, "Accrued expense settlement"),
          Map.entry(PostingOriginKind.EXPENSE_SETTLED, "Settled expense"),
          Map.entry(PostingOriginKind.EXPENSE_ON_CREDIT, "Expense on credit"),
          Map.entry(PostingOriginKind.RECEIPT, "Receipt"),
          Map.entry(PostingOriginKind.PAYMENT, "Payment"),
          Map.entry(PostingOriginKind.OWNER_CONTRIBUTION, "Owner contribution"),
          Map.entry(PostingOriginKind.OWNER_WITHDRAWAL, "Owner withdrawal"),
          Map.entry(PostingOriginKind.OPENING_POSITION, "Opening position"),
          Map.entry(PostingOriginKind.REVERSAL, "Reversal"),
          Map.entry(PostingOriginKind.INTERIM_RESULT_SWEEP, "Interim result sweep"),
          Map.entry(PostingOriginKind.FISCAL_YEAR_CLOSE, "Fiscal-year close"));

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
    return Objects.requireNonNull(POSTING_ORIGIN_LABELS.get(postingOriginKind));
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
