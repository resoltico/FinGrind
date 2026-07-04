package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliMutationJsonModels;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AnchorEntry;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

/** Maps resolved-journal success facts onto the CLI mutation payload surface. */
final class CliResolvedJournalPayloadMapper {
  private static final Comparator<AnchorEntry> ANCHOR_ENTRY_ORDER =
      Comparator.comparing((AnchorEntry entry) -> entry.role().wireValue())
          .thenComparing(entry -> entry.side().wireValue());
  private static final Comparator<EconomicEventClass> EVENT_CLASS_ORDER =
      Comparator.comparing(EconomicEventClass::wireValue);

  private CliResolvedJournalPayloadMapper() {}

  static CliMutationJsonModels.ResolvedJournalPayload resolvedJournalPayload(
      ResolvedJournal resolvedJournal) {
    return new CliMutationJsonModels.ResolvedJournalPayload(
        journalEntryPayload(resolvedJournal.expandedLines()),
        appliedTaxPayload(resolvedJournal.appliedTax()),
        foreignExchangePayload(resolvedJournal.foreignExchangeDetails()),
        classificationPayload(resolvedJournal.classification()));
  }

  private static CliMutationJsonModels.JournalEntryPayload journalEntryPayload(
      JournalEntry journalEntry) {
    return new CliMutationJsonModels.JournalEntryPayload(
        journalEntry.effectiveDate().toString(),
        journalEntry.lines().stream()
            .map(CliResolvedJournalPayloadMapper::journalLinePayload)
            .toList());
  }

  private static CliBookQueryJsonModels.JournalLinePayload journalLinePayload(JournalLine line) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        line.accountCode().value(),
        line.side().wireValue(),
        MonetaryAmount.of(line.amount().money()));
  }

  private static CliMutationJsonModels.ClassificationPayload classificationPayload(
      ClassificationResult classification) {
    return new CliMutationJsonModels.ClassificationPayload(
        classification.eventClass().wireValue(),
        classification.anchorSignature().stream()
            .sorted(ANCHOR_ENTRY_ORDER)
            .map(CliResolvedJournalPayloadMapper::anchorEntryPayload)
            .toList(),
        classification.containedTypedEvents().stream()
            .sorted(EVENT_CLASS_ORDER)
            .map(EconomicEventClass::wireValue)
            .toList(),
        classification.hasCashLine(),
        classification.evidenceClass().wireValue(),
        new CliMutationJsonModels.StructuralContextPayload(
            classification
                .structural()
                .reversesPriorPosting()
                .map(value -> value.value())
                .orElse(null),
            classification.structural().adoptionOpeningEntry()));
  }

  private static CliMutationJsonModels.AnchorEntryPayload anchorEntryPayload(
      AnchorEntry anchorEntry) {
    return new CliMutationJsonModels.AnchorEntryPayload(
        anchorEntry.role().wireValue(), anchorEntry.side().wireValue());
  }

  private static CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload
      foreignExchangePayload(@Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (foreignExchangeDetails == null) {
      return null;
    }
    return new CliForeignExchangeJsonModels.ForeignExchangePayload(
        foreignExchangeDetails.transactionAmount(),
        foreignExchangeDetails.functionalAmount(),
        new CliForeignExchangeJsonModels.QuotedExchangeRatePayload(
            foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().quotedOn().toString(),
            foreignExchangeDetails.quotedExchangeRate().quoteSource()),
        foreignExchangeDetails.treatmentKind().wireValue());
  }

  private static CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTaxPayload(
      @Nullable AppliedTax appliedTax) {
    if (appliedTax == null) {
      return null;
    }
    return CliTaxPayloadMapper.appliedTaxPayload(appliedTax);
  }
}
