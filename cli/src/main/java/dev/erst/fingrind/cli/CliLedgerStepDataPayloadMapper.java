package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
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

  static CliPlanJsonModels.@Nullable LedgerStepDataPayload ledgerStepDataPayload(
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
      if (kind == LedgerStepKind.ENSURE_BOOK) {
        return ensureBookStepDataPayload(entry.facts());
      }
      if (kind == LedgerStepKind.DECLARE_ACCOUNT) {
        return new CliPlanJsonModels.AccountDeclarationStepDataPayload(
            CliLedgerFactAccess.requiredTextFact(entry.facts(), "outcome"),
            CliLedgerBookQueryPayloadMapper.accountPayload(entry.facts()));
      }
      if (kind == LedgerStepKind.DECLARE_TAX_REGISTRATION) {
        return new CliPlanJsonModels.TaxRegistrationDeclarationStepDataPayload(
            CliLedgerFactAccess.requiredTextFact(entry.facts(), "outcome"),
            CliLedgerTaxRegistrationPayloadMapper.taxRegistrationPayload(entry.facts()));
      }
      if (kind == LedgerStepKind.ASSERT) {
        return assertionStepDataPayload(
            Objects.requireNonNull(entry.detailKind(), "detailKind"), entry.facts());
      }
      return new CliPlanJsonModels.PlanBoundaryStepDataPayload(
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

  static CliPlanJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliPlanJsonModels.LedgerStepFailurePayload(
        failure.code(),
        failure.message(),
        CliLedgerFactPayloadMapper.factPayloads(failure.facts()));
  }

  private static CliPlanJsonModels.LedgerStepDataPayload queryStepDataPayload(
      LedgerJournalEntry entry) {
    if (entry.kind() == LedgerStepKind.INSPECT_BOOK) {
      return bookInspectionStepDataPayload(entry.facts());
    }
    if (entry.kind() == LedgerStepKind.LIST_ACCOUNTS) {
      return accountPageStepDataPayload(entry.facts());
    }
    if (entry.kind() == LedgerStepKind.GET_POSTING) {
      return new CliPlanJsonModels.PostingStepDataPayload(
          CliLedgerBookQueryPayloadMapper.postingPayload(entry.facts()));
    }
    if (entry.kind() == LedgerStepKind.LIST_POSTINGS) {
      return postingPageStepDataPayload(entry.facts());
    }
    return accountBalanceStepDataPayload(entry.facts());
  }

  private static CliPlanJsonModels.EnsureBookStepDataPayload ensureBookStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.EnsureBookStepDataPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "initializedAt"),
        CliLedgerFactAccess.requiredTextFact(facts, "entityName"),
        CliLedgerFactAccess.requiredTextFact(facts, "functionalCurrency"),
        CliLedgerFactAccess.requiredTextFact(facts, "fiscalYearStart"),
        CliLedgerFactAccess.requiredTextFact(facts, "bookStartEffectiveDate"));
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
