package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps ledger-step facts into detailed CLI JSON payloads. */
final class CliLedgerStepDataPayloadMapper {
  private CliLedgerStepDataPayloadMapper() {}

  static CliPlanStepDataJsonModels.@Nullable LedgerStepDataPayload ledgerStepDataPayload(
      LedgerJournalEntry entry) {
    try {
      LedgerJournalKind kind = entry.kind();
      if (isCommittedJournalKind(kind)) {
        return committedEntryStepDataPayload(entry.facts());
      }
      if (kind == LedgerStepKind.PREFLIGHT_ENTRY) {
        return preflightEntryStepDataPayload(entry.facts());
      }
      if (isQueryJournalKind(kind)) {
        return queryStepDataPayload(entry);
      }
      if (kind == LedgerStepKind.DECLARE_ACCOUNT) {
        return new CliPlanStepDataJsonModels.AccountDeclarationStepDataPayload(
            CliLedgerFactAccess.requiredTextFact(entry.facts(), "outcome"),
            CliLedgerBookQueryPayloadMapper.accountPayload(entry.facts()));
      }
      if (kind == LedgerStepKind.DECLARE_TAX_REGISTRATION) {
        return new CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload(
            CliLedgerFactAccess.requiredTextFact(entry.facts(), "outcome"),
            CliLedgerTaxRegistrationPayloadMapper.taxRegistrationPayload(entry.facts()));
      }
      if (kind == LedgerStepKind.ASSERT) {
        return assertionStepDataPayload(
            Objects.requireNonNull(entry.detailKind(), "detailKind"), entry.facts());
      }
      return new CliPlanStepDataJsonModels.PlanBoundaryStepDataPayload(
          Objects.requireNonNull(entry.boundaryCheckpoint(), "boundaryCheckpoint").wireValue());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static boolean isCommittedJournalKind(LedgerJournalKind kind) {
    return kind instanceof LedgerStepKind stepKind && stepKind.commitsPosting();
  }

  private static boolean isQueryJournalKind(LedgerJournalKind kind) {
    return kind == LedgerStepKind.INSPECT_BOOK
        || kind == LedgerStepKind.LIST_ACCOUNTS
        || kind == LedgerStepKind.GET_POSTING
        || kind == LedgerStepKind.LIST_POSTINGS
        || kind == LedgerStepKind.ACCOUNT_BALANCE;
  }

  static CliPlanResultJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliPlanResultJsonModels.LedgerStepFailurePayload(
        failure.code(),
        failure.message(),
        CliLedgerFactPayloadMapper.factPayloads(failure.facts()));
  }

  private static CliPlanStepDataJsonModels.LedgerStepDataPayload queryStepDataPayload(
      LedgerJournalEntry entry) {
    if (entry.kind() == LedgerStepKind.INSPECT_BOOK) {
      return bookInspectionStepDataPayload(entry.facts());
    }
    if (entry.kind() == LedgerStepKind.LIST_ACCOUNTS) {
      return accountPageStepDataPayload(entry.facts());
    }
    if (entry.kind() == LedgerStepKind.GET_POSTING) {
      return new CliPlanStepDataJsonModels.PostingStepDataPayload(
          CliLedgerBookQueryPayloadMapper.postingPayload(entry.facts()));
    }
    if (entry.kind() == LedgerStepKind.LIST_POSTINGS) {
      return postingPageStepDataPayload(entry.facts());
    }
    return accountBalanceStepDataPayload(entry.facts());
  }

  private static CliPlanStepDataJsonModels.PreflightEntryStepDataPayload
      preflightEntryStepDataPayload(List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.PreflightEntryStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "idempotencyKey"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"));
  }

  private static CliPlanStepDataJsonModels.CommittedEntryStepDataPayload
      committedEntryStepDataPayload(List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.CommittedEntryStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "postingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "idempotencyKey"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"),
        CliLedgerFactAccess.requiredTextFact(facts, "recordedAt"));
  }

  private static CliPlanStepDataJsonModels.BookInspectionStepDataPayload
      bookInspectionStepDataPayload(List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.BookInspectionStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "state"),
        CliLedgerFactAccess.requiredFlagFact(facts, "initialized"),
        CliLedgerFactAccess.requiredFlagFact(facts, "compatibleWithCurrentBinary"));
  }

  private static CliPlanStepDataJsonModels.AccountPageStepDataPayload accountPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
        CliLedgerFactAccess.requiredCountFact(facts, "count"),
        CliLedgerFactAccess.requiredCountFact(facts, "pageLimit"),
        CliLedgerFactAccess.optionalTextFact(facts, "nextCursor"),
        CliLedgerFactAccess.requiredFlagFact(facts, "hasMore"),
        CliLedgerFactAccess.groupedFacts(facts, "account").stream()
            .map(CliLedgerBookQueryPayloadMapper::accountPayload)
            .toList());
  }

  private static CliPlanStepDataJsonModels.PostingPageStepDataPayload postingPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
        CliLedgerFactAccess.requiredCountFact(facts, "count"),
        CliLedgerFactAccess.requiredCountFact(facts, "pageLimit"),
        CliLedgerFactAccess.optionalTextFact(facts, "nextCursor"),
        CliLedgerFactAccess.requiredFlagFact(facts, "hasMore"),
        CliLedgerFactAccess.groupedFacts(facts, "posting").stream()
            .map(CliLedgerBookQueryPayloadMapper::postingSummaryPayload)
            .toList());
  }

  private static CliPlanStepDataJsonModels.AccountBalanceStepDataPayload
      accountBalanceStepDataPayload(List<LedgerFact> facts) {
    return new CliPlanStepDataJsonModels.AccountBalanceStepDataPayload(
        CliLedgerBookQueryPayloadMapper.accountPayload(
            CliLedgerFactAccess.requiredGroupFacts(facts, "account")),
        CliLedgerFactAccess.optionalTextFact(facts, "effectiveDateFrom"),
        CliLedgerFactAccess.optionalTextFact(facts, "effectiveDateTo"),
        CliLedgerFactAccess.requiredCountFact(facts, "bucketCount"),
        CliLedgerFactAccess.groupedFacts(facts, "balance").stream()
            .map(CliLedgerBookQueryPayloadMapper::balanceBucketPayload)
            .toList());
  }

  private static CliPlanStepDataJsonModels.LedgerStepDataPayload assertionStepDataPayload(
      LedgerAssertionKind detailKind, List<LedgerFact> facts) {
    return switch (detailKind) {
      case ACCOUNT_DECLARED, ACCOUNT_ACTIVE ->
          new CliPlanStepDataJsonModels.AccountCodeAssertionStepDataPayload(
              CliLedgerFactAccess.requiredTextFact(facts, "accountCode"));
      case POSTING_EXISTS ->
          new CliPlanStepDataJsonModels.PostingIdAssertionStepDataPayload(
              CliLedgerFactAccess.requiredTextFact(facts, "postingId"));
      case ACCOUNT_BALANCE_EQUALS -> accountBalanceStepDataPayload(facts);
    };
  }
}
