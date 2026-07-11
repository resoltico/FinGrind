package dev.erst.fingrind.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Canonical current-kernel reachability doctrine for declared account classifications. */
public final class AccountClassificationReachability {
  private AccountClassificationReachability() {}

  /** One published reachability cell for one declared account classification. */
  public record ReachabilityCell(
      String classificationFamily,
      AccountType accountType,
      String classification,
      boolean declarable,
      boolean openingReachable,
      boolean operationalJournalReachable,
      boolean reversalReachable) {
    /** Validates one current-kernel reachability cell. */
    public ReachabilityCell {
      Objects.requireNonNull(classificationFamily, "classificationFamily");
      if (classificationFamily.isBlank()) {
        throw new IllegalArgumentException("classificationFamily must not be blank.");
      }
      Objects.requireNonNull(accountType, "accountType");
      Objects.requireNonNull(classification, "classification");
      if (classification.isBlank()) {
        throw new IllegalArgumentException("classification must not be blank.");
      }
      if (!declarable && (openingReachable || operationalJournalReachable || reversalReachable)) {
        throw new IllegalArgumentException(
            "Non-declarable reachability cells must not report any reachable write path.");
      }
    }
  }

  /** Returns the current-kernel reachability matrix for every declarable classification cell. */
  public static List<ReachabilityCell> currentKernel() {
    List<ReachabilityCell> cells = new ArrayList<>();
    for (FinancialPositionLineClassification classification :
        FinancialPositionLineClassification.values()) {
      cells.add(financialPositionCell(classification));
    }
    for (ProfitAndLossLineClassification classification :
        ProfitAndLossLineClassification.values()) {
      cells.add(profitAndLossCell(classification));
    }
    return List.copyOf(cells);
  }

  /** Returns the current-kernel reachability facts for one validated account taxonomy. */
  public static ReachabilityCell reachabilityFor(AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    return switch (classificationFamily(accountTaxonomy)) {
      case FINANCIAL_POSITION ->
          financialPositionCell(
              accountTaxonomy.financialPositionLineClassification().orElseThrow());
      case PROFIT_AND_LOSS ->
          profitAndLossCell(accountTaxonomy.profitAndLossLineClassification().orElseThrow());
    };
  }

  /** Returns whether opening-position admission is allowed for the supplied taxonomy. */
  public static boolean openingReachable(AccountTaxonomy accountTaxonomy) {
    return reachabilityFor(accountTaxonomy).openingReachable();
  }

  /** Returns whether one direct operational journal may post against the supplied taxonomy. */
  public static boolean operationalJournalReachable(AccountTaxonomy accountTaxonomy) {
    return reachabilityFor(accountTaxonomy).operationalJournalReachable();
  }

  /** Returns whether one contingent reversal may touch the supplied taxonomy. */
  public static boolean reversalReachable(AccountTaxonomy accountTaxonomy) {
    return reachabilityFor(accountTaxonomy).reversalReachable();
  }

  private static ReachabilityCell financialPositionCell(
      FinancialPositionLineClassification classification) {
    FinancialPositionLineClassification requiredClassification =
        Objects.requireNonNull(classification, "classification");
    return new ReachabilityCell(
        ClassificationFamily.FINANCIAL_POSITION.wireValue(),
        requiredClassification.accountType(),
        requiredClassification.wireValue(),
        true,
        true,
        requiredClassification != FinancialPositionLineClassification.INVENTORY
            && !requiredClassification.reservedForCloseOperations(),
        !requiredClassification.reservedForCloseOperations());
  }

  private static ReachabilityCell profitAndLossCell(
      ProfitAndLossLineClassification classification) {
    return new ReachabilityCell(
        ClassificationFamily.PROFIT_AND_LOSS.wireValue(),
        Objects.requireNonNull(classification, "classification").accountType(),
        classification.wireValue(),
        true,
        false,
        true,
        true);
  }

  private static ClassificationFamily classificationFamily(AccountTaxonomy accountTaxonomy) {
    boolean hasFinancialPosition =
        Objects.requireNonNull(accountTaxonomy, "accountTaxonomy")
            .financialPositionLineClassification()
            .isPresent();
    boolean hasProfitAndLoss = accountTaxonomy.profitAndLossLineClassification().isPresent();
    if (hasFinancialPosition == hasProfitAndLoss) {
      throw new IllegalArgumentException(
          "Account taxonomy must carry exactly one classification family.");
    }
    return hasFinancialPosition
        ? ClassificationFamily.FINANCIAL_POSITION
        : ClassificationFamily.PROFIT_AND_LOSS;
  }

  /** Classification families carried by validated account taxonomies. */
  private enum ClassificationFamily {
    /** Taxonomy families that classify financial-position accounts. */
    FINANCIAL_POSITION,

    /** Taxonomy families that classify profit-and-loss accounts. */
    PROFIT_AND_LOSS;

    private String wireValue() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }
}
