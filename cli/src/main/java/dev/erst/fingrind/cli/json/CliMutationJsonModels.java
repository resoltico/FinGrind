package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Write-side JSON payloads emitted by the CLI transport layer. */
public interface CliMutationJsonModels {

  record PreflightAcceptedPayload(
      String idempotencyKey, String effectiveDate, ResolvedJournalPayload resolvedJournal)
      implements CliSuccessPayload {
    public PreflightAcceptedPayload {
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      Objects.requireNonNull(resolvedJournal, "resolvedJournal");
    }
  }

  record CommittedPostingPayload(
      String postingId,
      String idempotencyKey,
      String effectiveDate,
      String recordedAt,
      boolean idempotentReplay,
      ResolvedJournalPayload resolvedJournal)
      implements CliSuccessPayload {
    public CommittedPostingPayload {
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      Objects.requireNonNull(resolvedJournal, "resolvedJournal");
    }
  }

  record ResolvedJournalPayload(
      JournalEntryPayload expandedLines,
      CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
      CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchangeDetails,
      ClassificationPayload classification) {
    /** Validates one resolved-journal payload. */
    public ResolvedJournalPayload(
        JournalEntryPayload expandedLines,
        CliTaxJsonModels.@Nullable AppliedTaxPayload appliedTax,
        CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload foreignExchangeDetails,
        ClassificationPayload classification) {
      this.expandedLines = Objects.requireNonNull(expandedLines, "expandedLines");
      this.appliedTax = appliedTax;
      this.foreignExchangeDetails = foreignExchangeDetails;
      this.classification = Objects.requireNonNull(classification, "classification");
    }
  }

  record JournalEntryPayload(
      String effectiveDate, List<CliBookQueryJsonModels.JournalLinePayload> lines) {
    public JournalEntryPayload {
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      lines = copyList(lines, "lines");
    }
  }

  record ClassificationPayload(
      String eventClass,
      List<AnchorEntryPayload> anchorSignature,
      List<String> containedTypedEvents,
      boolean hasCashLine,
      String evidenceClass,
      StructuralContextPayload structural) {
    public ClassificationPayload {
      eventClass = requireText(eventClass, "eventClass");
      anchorSignature = copyList(anchorSignature, "anchorSignature");
      containedTypedEvents = copyList(containedTypedEvents, "containedTypedEvents");
      evidenceClass = requireText(evidenceClass, "evidenceClass");
      Objects.requireNonNull(structural, "structural");
    }
  }

  record AnchorEntryPayload(String accountRole, String side) {
    public AnchorEntryPayload {
      accountRole = requireText(accountRole, "accountRole");
      side = requireText(side, "side");
    }
  }

  record StructuralContextPayload(
      @Nullable String reversesPriorPostingId, boolean adoptionOpeningEntry) {
    public StructuralContextPayload {
      reversesPriorPostingId =
          requireOptionalText(reversesPriorPostingId, "reversesPriorPostingId");
    }
  }
}
