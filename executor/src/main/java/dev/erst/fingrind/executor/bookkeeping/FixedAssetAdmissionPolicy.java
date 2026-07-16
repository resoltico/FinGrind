package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** First-defense aggregate admission and journal resolution for fixed-asset lifecycle events. */
public final class FixedAssetAdmissionPolicy {
  /** Resolves one fixed-asset lifecycle request or returns its first deterministic refusal. */
  public Resolution resolve(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      PostingValidationStore book,
      String selectorValue) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          capitalizationResolution(capitalization, book, selectorValue);
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          depreciationResolution(depreciation, book, selectorValue);
      case FixedAssetBookkeepingEntryVariants.Disposal disposal ->
          disposalResolution(disposal, book, selectorValue);
      default -> Resolution.accepted(entry);
    };
  }

  private static Resolution capitalizationResolution(
      FixedAssetBookkeepingEntryVariants.Capitalization capitalization,
      PostingValidationStore book,
      String selectorValue) {
    if (book.hasFixedAsset(capitalization.fixedAssetId())) {
      return Resolution.rejected(
          violation(
              "fixed-asset-id-already-exists",
              "fixedAssetId",
              "entryKind '%s' cannot create fixedAssetId '%s' because that identifier already exists."
                  .formatted(selectorValue, capitalization.fixedAssetId().value())));
    }
    return Resolution.accepted(capitalization);
  }

  private static Resolution depreciationResolution(
      FixedAssetBookkeepingEntryVariants.Depreciation depreciation,
      PostingValidationStore book,
      String selectorValue) {
    Optional<FixedAssetRecord> found = book.findFixedAsset(depreciation.fixedAssetId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, depreciation.fixedAssetId()));
    }
    FixedAssetRecord asset = found.orElseThrow();
    if (asset.disposedOn().isPresent()) {
      return Resolution.rejected(
          violation(
              "fixed-asset-already-disposed",
              "fixedAssetId",
              "entryKind '%s' cannot depreciate fixedAssetId '%s' because it was already disposed."
                  .formatted(selectorValue, depreciation.fixedAssetId().value())));
    }
    if (depreciation.effectiveDate().isBefore(asset.depreciationSchedule().inServiceDate())) {
      return Resolution.rejected(
          violation(
              "fixed-asset-depreciation-precedes-in-service-date",
              "effectiveDate",
              "entryKind '%s' uses effectiveDate '%s' before fixedAssetId '%s' entered service on '%s'."
                  .formatted(
                      selectorValue,
                      depreciation.effectiveDate(),
                      depreciation.fixedAssetId().value(),
                      asset.depreciationSchedule().inServiceDate())));
    }
    if (depreciation.effectiveDate().isBefore(asset.lifecycleHorizon())) {
      return Resolution.rejected(
          precedesHorizon(
              selectorValue,
              depreciation.fixedAssetId(),
              depreciation.effectiveDate(),
              asset.lifecycleHorizon()));
    }
    if (!asset.depreciable()) {
      return Resolution.rejected(
          violation(
              "fixed-asset-fully-depreciated",
              "fixedAssetId",
              "entryKind '%s' cannot depreciate fixedAssetId '%s' because no depreciable carrying amount remains."
                  .formatted(selectorValue, depreciation.fixedAssetId().value())));
    }
    Money charge = nextStraightLineCharge(asset);
    return Resolution.accepted(
        new FixedAssetBookkeepingEntryVariants.Depreciation(
            depreciation.effectiveDate(),
            depreciation.fixedAssetId(),
            new ResolvedFixedAssetDepreciation(
                asset.depreciationExpenseAccountCode(),
                asset.accumulatedDepreciationAccountCode(),
                MonetaryAmount.of(charge))));
  }

  private static Resolution disposalResolution(
      FixedAssetBookkeepingEntryVariants.Disposal disposal,
      PostingValidationStore book,
      String selectorValue) {
    Optional<FixedAssetRecord> found = book.findFixedAsset(disposal.fixedAssetId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, disposal.fixedAssetId()));
    }
    FixedAssetRecord asset = found.orElseThrow();
    if (asset.disposedOn().isPresent()) {
      return Resolution.rejected(
          violation(
              "fixed-asset-already-disposed",
              "fixedAssetId",
              "entryKind '%s' cannot dispose fixedAssetId '%s' because it was already disposed."
                  .formatted(selectorValue, disposal.fixedAssetId().value())));
    }
    if (disposal.effectiveDate().isBefore(asset.lifecycleHorizon())) {
      return Resolution.rejected(
          precedesHorizon(
              selectorValue,
              disposal.fixedAssetId(),
              disposal.effectiveDate(),
              asset.lifecycleHorizon()));
    }
    if (!disposal.proceeds().currencyCode().equals(asset.cost().currencyUnit().code())) {
      return Resolution.rejected(
          violation(
              "fixed-asset-disposal-currency-mismatch",
              "proceeds",
              "entryKind '%s' must use the fixed asset's functional currency '%s' for proceeds."
                  .formatted(selectorValue, asset.cost().currencyUnit().code())));
    }
    Money proceeds = disposal.proceeds().toMoney();
    Money carrying = asset.carryingAmount();
    Money difference =
        Money.ofMinorUnits(
            carrying.currencyUnit(), Math.abs(proceeds.minorUnits() - carrying.minorUnits()));
    boolean gain = proceeds.compareTo(carrying) >= 0;
    return Resolution.accepted(
        new FixedAssetBookkeepingEntryVariants.Disposal(
            disposal.effectiveDate(),
            disposal.fixedAssetId(),
            disposal.cashAccountCode(),
            disposal.proceeds(),
            new ResolvedFixedAssetDisposal(
                asset.assetAccountCode(),
                asset.accumulatedDepreciationAccountCode(),
                gain ? asset.disposalGainAccountCode() : asset.disposalLossAccountCode(),
                MonetaryAmount.of(asset.cost()),
                MonetaryAmount.of(asset.accumulatedDepreciation()),
                MonetaryAmount.of(carrying),
                MonetaryAmount.of(difference),
                gain)));
  }

  private static Money nextStraightLineCharge(FixedAssetRecord asset) {
    FixedAssetDepreciationSchedule schedule = asset.depreciationSchedule();
    int remainingPeriods = schedule.usefulLifeMonths() - asset.depreciationPeriodsApplied();
    long remaining = asset.remainingDepreciableAmount().minorUnits();
    long charge = Math.floorDiv(remaining + remainingPeriods - 1L, remainingPeriods);
    return Money.ofMinorUnits(asset.cost().currencyUnit(), charge);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation notFound(
      String selectorValue, FixedAssetId fixedAssetId) {
    return violation(
        "fixed-asset-not-found",
        "fixedAssetId",
        "entryKind '%s' cannot find fixedAssetId '%s' in this book."
            .formatted(selectorValue, fixedAssetId.value()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation precedesHorizon(
      String selectorValue, FixedAssetId fixedAssetId, LocalDate effectiveDate, LocalDate horizon) {
    return violation(
        "fixed-asset-lifecycle-precedes-horizon",
        "effectiveDate",
        "entryKind '%s' uses effectiveDate '%s' before the lifecycle horizon '%s' for fixedAssetId '%s'."
            .formatted(selectorValue, effectiveDate, horizon, fixedAssetId.value()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation violation(
      String code, String field, String message) {
    return new BookkeepingPostingRejection.EntrySemanticsViolation(code, field, message);
  }

  /** Accepted resolved entry or one deterministic fixed-asset rejection. */
  public record Resolution(
      @Nullable BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {
    public Resolution {
      java.util.Objects.requireNonNull(rejection, "rejection");
      if (rejection.isEmpty()) {
        java.util.Objects.requireNonNull(entry, "entry");
      }
    }

    static Resolution accepted(dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry) {
      return new Resolution(entry, Optional.empty());
    }

    static Resolution rejected(BookkeepingPostingRejection.EntrySemanticsViolation violation) {
      return new Resolution(
          null,
          Optional.of(
              new BookkeepingPostingRejection.EntrySemanticsViolations(
                  java.util.List.of(violation))));
    }
  }
}
