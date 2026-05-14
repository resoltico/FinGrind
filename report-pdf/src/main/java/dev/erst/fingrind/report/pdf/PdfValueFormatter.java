package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
  private PdfValueFormatter() {}

  static String displayMoney(Money money) {
    return money.canonicalDecimal();
  }

  static String displayBalanceSide(BalanceSide balanceSide) {
    return switch (balanceSide) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
      case ZERO -> "Balanced";
    };
  }

  static String displayAccountTypeSection(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Assets";
      case LIABILITY -> "Liabilities";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expenses";
    };
  }

  static String displayRowKind(boolean synthetic) {
    return synthetic ? "Synthetic" : "Account";
  }

  static String displayLineRole(Optional<AccountRole> lineRole) {
    return lineRole.map(PdfValueFormatter::displayAccountRole).orElse("(derived)");
  }

  static String displayAccountType(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Asset";
      case LIABILITY -> "Liability";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expense";
    };
  }

  static String displayAccountRole(AccountRole accountRole) {
    return switch (accountRole) {
      case ORDINARY -> "Ordinary";
      case CONTRA -> "Contra";
      case RETAINED_EARNINGS -> "Retained earnings";
    };
  }

  static String displayNormalBalance(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  static String displayBoolean(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-closing postings";
    };
  }

  static String optionalDate(@Nullable LocalDate date) {
    return date == null ? "(current durable posting horizon)" : date.toString();
  }

  static String optionalDateRange(@Nullable LocalDate from, @Nullable LocalDate to) {
    String lower = from == null ? "(unbounded lower filter)" : from.toString();
    String upper = to == null ? "(current durable posting horizon)" : to.toString();
    return lower + " to " + upper;
  }

  static String effectiveDateRange(EffectiveDateRange range) {
    return optionalDateRange(
        range.effectiveDateFrom().orElse(null), range.effectiveDateTo().orElse(null));
  }

  static String comparativeRange(EffectiveDateRange range) {
    return range.effectiveDateFrom().isEmpty() && range.effectiveDateTo().isEmpty()
        ? "(none)"
        : effectiveDateRange(range);
  }

  static String reversalTarget(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(direct)");
  }

  static String reversalState(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_CLOSE -> "Period close";
      case OPENING_BALANCE -> "Opening balance";
    };
  }
}
