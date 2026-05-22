package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
  private static final DateTimeFormatter HUMAN_INSTANT_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

  private PdfValueFormatter() {}

  static String instant(Instant instant) {
    return HUMAN_INSTANT_FORMATTER.format(instant);
  }

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

  static String displayRowKind(StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> "Account";
      case CURRENT_PERIOD_RESULT -> "Current period result";
    };
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> lineCode;
      case CURRENT_PERIOD_RESULT -> "(derived)";
    };
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
    };
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    return switch (lineClassification) {
      case CURRENT_ASSET -> "Current asset";
      case NONCURRENT_ASSET -> "Non-current asset";
      case CURRENT_LIABILITY -> "Current liability";
      case NONCURRENT_LIABILITY -> "Non-current liability";
      case OWNER_CAPITAL -> "Owner capital";
      case OWNER_DRAWINGS -> "Owner drawings";
      case PARTNER_CAPITAL -> "Partner capital";
      case PARTNER_CURRENT -> "Partner current";
      case SHARE_CAPITAL -> "Share capital";
      case RETAINED_EARNINGS -> "Retained earnings";
      case ACCUMULATED_SURPLUS -> "Accumulated surplus";
      case RESERVE -> "Reserve";
      case CURRENT_PERIOD_RESULT -> "Current period result";
      case OTHER_EQUITY -> "Other equity";
    };
  }

  static String displayProfitAndLossLineClassification(
      ProfitAndLossLineClassification lineClassification) {
    return switch (lineClassification) {
      case OPERATING_REVENUE -> "Operating revenue";
      case OTHER_REVENUE -> "Other revenue";
      case FINANCE_INCOME -> "Finance income";
      case COST_OF_SALES -> "Cost of sales";
      case OPERATING_EXPENSE -> "Operating expense";
      case DEPRECIATION_AND_AMORTIZATION -> "Depreciation and amortization";
      case FINANCE_EXPENSE -> "Finance expense";
      case TAX_EXPENSE -> "Tax expense";
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

  static String displayEntityProfile(EntityForm entityForm, OwnerModel ownerModel) {
    return wireLabel(entityForm.wireValue()) + " / " + wireLabel(ownerModel.wireValue());
  }

  static String displayBusinessActivityTags(List<BusinessActivityTag> businessActivityTags) {
    return businessActivityTags.isEmpty()
        ? "(none)"
        : businessActivityTags.stream()
            .map(BusinessActivityTag::value)
            .collect(java.util.stream.Collectors.joining(", "));
  }

  static String displayPolicyProfile(AccountingPolicyProfile policyProfile) {
    return wireLabel(policyProfile.wireValue());
  }

  static String optionalDate(@Nullable LocalDate date) {
    return date == null ? "latest committed posting date" : date.toString();
  }

  static String optionalDateRange(@Nullable LocalDate from, @Nullable LocalDate to) {
    String lower = from == null ? "book start" : from.toString();
    String upper = to == null ? "latest committed posting date" : to.toString();
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
        .orElse("(not a reversal)");
  }

  static String postingRole(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_CLOSE -> "Period close";
      case OPENING_BALANCE -> "Opening balance";
    };
  }

  private static String wireLabel(String wireValue) {
    return java.util.Arrays.stream(wireValue.split("_+"))
        .map(token -> token.substring(0, 1) + token.substring(1).toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.joining(" "));
  }
}
