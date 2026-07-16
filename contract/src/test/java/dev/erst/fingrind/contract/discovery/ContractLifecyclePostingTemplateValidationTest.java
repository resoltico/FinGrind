package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates.FinancingTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetDepreciationScheduleTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractFixedAssetTemplates.FixedAssetTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Contract coverage for lifecycle-template eligibility and required-context invariants. */
class ContractLifecyclePostingTemplateValidationTest {
  @Test
  void lifecycleContextBlocksAreRejectedForUnrelatedEntryKinds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.SALE_SETTLED, fields(fixedAsset(), null, null), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.SALE_SETTLED, fields(null, financing(), null), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.SALE_SETTLED,
                fields(null, null, realizedForeignExchange()),
                null));
  }

  @Test
  void lifecycleEntryKindsRequireTheirOwnedContextBlocks() {
    assertThrows(
        NullPointerException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION, fields(null, null, null), null));
    assertThrows(
        NullPointerException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.FINANCING_BORROWING, fields(null, null, null), null));
    assertThrows(
        NullPointerException.class,
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION, fields(null, null, null), null));
  }

  @Test
  void scalarTemplateRulesRejectMissingRequiredAndUnexpectedContextValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ContractPostingTemplateFieldRules.requirePresent(null, "context"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ContractPostingTemplateFieldRules.requireAbsent(new Object(), "context"));
  }

  @Test
  void fixedAssetDepreciationScheduleRejectsBothOutOfRangeBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedAssetDepreciationScheduleTemplateDescriptor("2026-07-15", 0, money("1")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FixedAssetDepreciationScheduleTemplateDescriptor("2026-07-15", 1_201, money("1")));
  }

  private static PostingTemplateFields fields(
      @Nullable FixedAssetTemplateDescriptor fixedAsset,
      @Nullable FinancingTemplateDescriptor financing,
      @Nullable RealizedForeignExchangeTemplateDescriptor realizedForeignExchange) {
    return new PostingTemplateFields(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        fixedAsset,
        financing,
        realizedForeignExchange);
  }

  private static FixedAssetTemplateDescriptor fixedAsset() {
    return new FixedAssetTemplateDescriptor(null, null, null, null, null, null, null, null, null);
  }

  private static FinancingTemplateDescriptor financing() {
    return new FinancingTemplateDescriptor(null, null, null, null, null, null);
  }

  private static RealizedForeignExchangeTemplateDescriptor realizedForeignExchange() {
    return new RealizedForeignExchangeTemplateDescriptor(null, null, null);
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }
}
