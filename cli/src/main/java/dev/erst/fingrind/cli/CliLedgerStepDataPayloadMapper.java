package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps ledger-step facts into detailed CLI JSON payloads. */
final class CliLedgerStepDataPayloadMapper {
  private CliLedgerStepDataPayloadMapper() {}

  static CliPlanJsonModels.@Nullable LedgerStepDataPayload ledgerStepDataPayload(
      LedgerJournalEntry entry) {
    try {
      return switch (entry.kind()) {
        case OPEN_BOOK -> openBookStepDataPayload(entry.facts());
        case DECLARE_ACCOUNT ->
            new CliPlanJsonModels.DeclaredAccountStepDataPayload(
                CliLedgerBookQueryPayloadMapper.accountPayload(entry.facts()));
        case PREFLIGHT_ENTRY -> preflightEntryStepDataPayload(entry.facts());
        case POST_ENTRY -> committedEntryStepDataPayload(entry.facts());
        case INSPECT_BOOK -> bookInspectionStepDataPayload(entry.facts());
        case LIST_ACCOUNTS -> accountPageStepDataPayload(entry.facts());
        case GET_POSTING ->
            new CliPlanJsonModels.PostingStepDataPayload(
                CliLedgerBookQueryPayloadMapper.postingPayload(entry.facts()));
        case LIST_POSTINGS -> postingPageStepDataPayload(entry.facts());
        case ACCOUNT_BALANCE -> accountBalanceStepDataPayload(entry.facts());
        case ASSERT ->
            assertionStepDataPayload(
                Objects.requireNonNull(entry.detailKind(), "detailKind"), entry.facts());
        case PLAN_BOUNDARY ->
            new CliPlanJsonModels.PlanBoundaryStepDataPayload(
                Objects.requireNonNull(entry.boundaryPhase(), "boundaryPhase").wireValue());
      };
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  static CliPlanJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliPlanJsonModels.LedgerStepFailurePayload(
        failure.code(),
        failure.message(),
        CliLedgerFactPayloadMapper.factPayloads(failure.facts()));
  }

  private static CliPlanJsonModels.OpenBookStepDataPayload openBookStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.OpenBookStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "initializedAt"),
        CliLedgerFactAccess.requiredTextFact(facts, "entityName"),
        CliLedgerFactAccess.requiredTextFact(facts, "functionalCurrency"),
        CliLedgerFactAccess.requiredTextFact(facts, "fiscalYearStart"));
  }

  private static CliPlanJsonModels.PreflightEntryStepDataPayload preflightEntryStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.PreflightEntryStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "idempotencyKey"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"));
  }

  private static CliPlanJsonModels.CommittedEntryStepDataPayload committedEntryStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.CommittedEntryStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "postingId"),
        CliLedgerFactAccess.requiredTextFact(facts, "idempotencyKey"),
        CliLedgerFactAccess.requiredTextFact(facts, "effectiveDate"),
        CliLedgerFactAccess.requiredTextFact(facts, "recordedAt"));
  }

  private static CliPlanJsonModels.BookInspectionStepDataPayload bookInspectionStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.BookInspectionStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "state"),
        CliLedgerFactAccess.requiredFlagFact(facts, "initialized"),
        CliLedgerFactAccess.requiredFlagFact(facts, "compatibleWithCurrentBinary"));
  }

  private static CliPlanJsonModels.AccountPageStepDataPayload accountPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.AccountPageStepDataPayload(
        CliLedgerFactAccess.requiredCountFact(facts, "count"),
        CliLedgerFactAccess.requiredCountFact(facts, "pageLimit"),
        CliLedgerFactAccess.optionalTextFact(facts, "nextCursor"),
        CliLedgerFactAccess.requiredFlagFact(facts, "hasMore"),
        CliLedgerFactAccess.groupedFacts(facts, "account").stream()
            .map(CliLedgerBookQueryPayloadMapper::accountPayload)
            .toList());
  }

  private static CliPlanJsonModels.PostingPageStepDataPayload postingPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.PostingPageStepDataPayload(
        CliLedgerFactAccess.requiredCountFact(facts, "count"),
        CliLedgerFactAccess.requiredCountFact(facts, "pageLimit"),
        CliLedgerFactAccess.optionalTextFact(facts, "nextCursor"),
        CliLedgerFactAccess.requiredFlagFact(facts, "hasMore"),
        CliLedgerFactAccess.groupedFacts(facts, "posting").stream()
            .map(CliLedgerBookQueryPayloadMapper::postingSummaryPayload)
            .toList());
  }

  private static CliPlanJsonModels.AccountBalanceStepDataPayload accountBalanceStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.AccountBalanceStepDataPayload(
        CliLedgerBookQueryPayloadMapper.accountPayload(
            CliLedgerFactAccess.requiredGroupFacts(facts, "account")),
        CliLedgerFactAccess.optionalTextFact(facts, "effectiveDateFrom"),
        CliLedgerFactAccess.optionalTextFact(facts, "effectiveDateTo"),
        CliLedgerFactAccess.requiredCountFact(facts, "bucketCount"),
        CliLedgerFactAccess.groupedFacts(facts, "balance").stream()
            .map(CliLedgerBookQueryPayloadMapper::balanceBucketPayload)
            .toList());
  }

  private static CliPlanJsonModels.LedgerStepDataPayload assertionStepDataPayload(
      LedgerAssertionKind detailKind, List<LedgerFact> facts) {
    return switch (detailKind) {
      case ACCOUNT_DECLARED, ACCOUNT_ACTIVE ->
          new CliPlanJsonModels.AccountCodeAssertionStepDataPayload(
              CliLedgerFactAccess.requiredTextFact(facts, "accountCode"));
      case POSTING_EXISTS ->
          new CliPlanJsonModels.PostingIdAssertionStepDataPayload(
              CliLedgerFactAccess.requiredTextFact(facts, "postingId"));
      case ACCOUNT_BALANCE_EQUALS -> accountBalanceStepDataPayload(facts);
    };
  }
}
