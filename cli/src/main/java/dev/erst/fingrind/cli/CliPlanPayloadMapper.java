package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps ledger-plan outcomes into typed CLI JSON payloads. */
final class CliPlanPayloadMapper {
  private CliPlanPayloadMapper() {}

  static CliPlanJsonModels.LedgerPlanPayload ledgerPlanPayload(
      LedgerPlanResult result, PlanResultDetail resultDetail) {
    CliPlanJsonModels.LedgerPlanSummaryPayload summaryPayload = ledgerPlanSummaryPayload(result);
    return new CliPlanJsonModels.LedgerPlanPayload(
        result.planId().value(),
        result.status(),
        resultDetail,
        summaryPayload,
        resultDetail == PlanResultDetail.FULL
            ? ledgerExecutionJournalPayload(result.journal())
            : null);
  }

  private static CliPlanJsonModels.LedgerPlanSummaryPayload ledgerPlanSummaryPayload(
      LedgerPlanResult result) {
    LedgerExecutionJournal journal = result.journal();
    LedgerJournalEntry terminalStep = journal.terminalStep();
    @Nullable LedgerStepFailure failure =
        terminalStep instanceof LedgerJournalEntry.Failed failed ? failed.requiredFailure() : null;
    return new CliPlanJsonModels.LedgerPlanSummaryPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().size(),
        (int)
            journal.steps().stream()
                .filter(step -> step.status() == LedgerStepStatus.SUCCEEDED)
                .count(),
        failure == null ? 0 : 1,
        failure == null ? null : terminalStep.stepId().value(),
        failure == null ? null : failure.code(),
        failure == null ? null : failure.message());
  }

  private static CliPlanJsonModels.LedgerExecutionJournalPayload ledgerExecutionJournalPayload(
      LedgerExecutionJournal journal) {
    return new CliPlanJsonModels.LedgerExecutionJournalPayload(
        journal.startedAt().toString(),
        journal.finishedAt().toString(),
        journal.steps().stream().map(CliPlanPayloadMapper::ledgerJournalEntryPayload).toList());
  }

  private static CliPlanJsonModels.LedgerJournalEntryPayload ledgerJournalEntryPayload(
      LedgerJournalEntry entry) {
    CliPlanJsonModels.LedgerStepFailurePayload failurePayload =
        switch (entry) {
          case LedgerJournalEntry.Succeeded _ -> null;
          case LedgerJournalEntry.Failed failed ->
              ledgerStepFailurePayload(failed.requiredFailure());
        };
    return new CliPlanJsonModels.LedgerJournalEntryPayload(
        entry.stepId().value(),
        entry.kind(),
        detailKind(entry.journalStep()),
        boundaryPhase(entry.journalStep()),
        entry.status(),
        entry.startedAt().toString(),
        entry.finishedAt().toString(),
        ledgerStepDataPayload(entry),
        failurePayload);
  }

  private static @Nullable LedgerAssertionKind detailKind(LedgerJournalStep journalStep) {
    return journalStep.detailKind();
  }

  private static @Nullable LedgerBoundaryPhase boundaryPhase(LedgerJournalStep journalStep) {
    return journalStep.boundaryPhase();
  }

  private static CliPlanJsonModels.@Nullable LedgerStepDataPayload ledgerStepDataPayload(
      LedgerJournalEntry entry) {
    try {
      return switch (entry.kind()) {
        case OPEN_BOOK -> openBookStepDataPayload(entry.facts());
        case DECLARE_ACCOUNT ->
            new CliPlanJsonModels.DeclaredAccountStepDataPayload(accountPayload(entry.facts()));
        case PREFLIGHT_ENTRY -> preflightEntryStepDataPayload(entry.facts());
        case POST_ENTRY -> committedEntryStepDataPayload(entry.facts());
        case INSPECT_BOOK -> bookInspectionStepDataPayload(entry.facts());
        case LIST_ACCOUNTS -> accountPageStepDataPayload(entry.facts());
        case GET_POSTING ->
            new CliPlanJsonModels.PostingStepDataPayload(postingPayload(entry.facts()));
        case LIST_POSTINGS -> postingPageStepDataPayload(entry.facts());
        case ACCOUNT_BALANCE -> accountBalanceStepDataPayload(entry.facts());
        case ASSERT ->
            assertionStepDataPayload(
                Objects.requireNonNull(entry.detailKind(), "detailKind"), entry.facts());
        case PLAN_BOUNDARY ->
            planBoundaryStepDataPayload(
                Objects.requireNonNull(entry.boundaryPhase(), "boundaryPhase"));
      };
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static CliPlanJsonModels.OpenBookStepDataPayload openBookStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.OpenBookStepDataPayload(
        requiredTextFact(facts, "initializedAt"),
        requiredTextFact(facts, "entityName"),
        requiredTextFact(facts, "functionalCurrency"),
        requiredTextFact(facts, "fiscalYearStart"));
  }

  private static CliPlanJsonModels.PreflightEntryStepDataPayload preflightEntryStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.PreflightEntryStepDataPayload(
        requiredTextFact(facts, "idempotencyKey"), requiredTextFact(facts, "effectiveDate"));
  }

  private static CliPlanJsonModels.CommittedEntryStepDataPayload committedEntryStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.CommittedEntryStepDataPayload(
        requiredTextFact(facts, "postingId"),
        requiredTextFact(facts, "idempotencyKey"),
        requiredTextFact(facts, "effectiveDate"),
        requiredTextFact(facts, "recordedAt"));
  }

  private static CliPlanJsonModels.BookInspectionStepDataPayload bookInspectionStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.BookInspectionStepDataPayload(
        requiredTextFact(facts, "state"),
        requiredFlagFact(facts, "initialized"),
        requiredFlagFact(facts, "compatibleWithCurrentBinary"));
  }

  private static CliPlanJsonModels.AccountPageStepDataPayload accountPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.AccountPageStepDataPayload(
        requiredCountFact(facts, "count"),
        requiredCountFact(facts, "pageLimit"),
        optionalTextFact(facts, "nextCursor"),
        requiredFlagFact(facts, "hasMore"),
        groupedFacts(facts, "account").stream().map(CliPlanPayloadMapper::accountPayload).toList());
  }

  private static CliPlanJsonModels.PostingPageStepDataPayload postingPageStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.PostingPageStepDataPayload(
        requiredCountFact(facts, "count"),
        requiredCountFact(facts, "pageLimit"),
        optionalTextFact(facts, "nextCursor"),
        requiredFlagFact(facts, "hasMore"),
        groupedFacts(facts, "posting").stream()
            .map(CliPlanPayloadMapper::postingSummaryPayload)
            .toList());
  }

  private static CliPlanJsonModels.AccountBalanceStepDataPayload accountBalanceStepDataPayload(
      List<LedgerFact> facts) {
    return new CliPlanJsonModels.AccountBalanceStepDataPayload(
        accountPayload(requiredGroupFacts(facts, "account")),
        optionalTextFact(facts, "effectiveDateFrom"),
        optionalTextFact(facts, "effectiveDateTo"),
        requiredCountFact(facts, "bucketCount"),
        groupedFacts(facts, "balance").stream()
            .map(CliPlanPayloadMapper::balanceBucketPayload)
            .toList());
  }

  private static CliPlanJsonModels.LedgerStepDataPayload assertionStepDataPayload(
      LedgerAssertionKind detailKind, List<LedgerFact> facts) {
    return switch (detailKind) {
      case ACCOUNT_DECLARED, ACCOUNT_ACTIVE ->
          new CliPlanJsonModels.AccountCodeAssertionStepDataPayload(
              requiredTextFact(facts, "accountCode"));
      case POSTING_EXISTS ->
          new CliPlanJsonModels.PostingIdAssertionStepDataPayload(
              requiredTextFact(facts, "postingId"));
      case ACCOUNT_BALANCE_EQUALS -> accountBalanceStepDataPayload(facts);
    };
  }

  private static CliPlanJsonModels.PlanBoundaryStepDataPayload planBoundaryStepDataPayload(
      LedgerBoundaryPhase boundaryPhase) {
    return new CliPlanJsonModels.PlanBoundaryStepDataPayload(boundaryPhase.wireValue());
  }

  private static CliPlanJsonModels.LedgerStepFailurePayload ledgerStepFailurePayload(
      LedgerStepFailure failure) {
    return new CliPlanJsonModels.LedgerStepFailurePayload(
        failure.code(), failure.message(), factPayloads(failure.facts()));
  }

  private static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        requiredTextFact(facts, "accountCode"),
        requiredTextFact(facts, "accountName"),
        requiredTextFact(facts, "accountType"),
        requiredTextFact(facts, "accountRole"),
        requiredTextFact(facts, "accountNodeKind"),
        optionalTextFact(facts, "parentAccountCode"),
        optionalTextFact(facts, "financialPositionLineClassification"),
        optionalTextFact(facts, "profitAndLossLineClassification"),
        requiredTextFact(facts, "normalBalance"),
        requiredFlagFact(facts, "active"),
        requiredTextFact(facts, "declaredAt"));
  }

  private static CliBookQueryJsonModels.PostingPayload postingPayload(List<LedgerFact> facts) {
    List<LedgerFact> provenanceFacts = requiredGroupFacts(facts, "provenance");
    List<LedgerFact> evidenceFacts = requiredGroupFacts(facts, "evidence");
    @Nullable List<LedgerFact> reversalFacts = optionalGroupFacts(facts, "reversal");
    return new CliBookQueryJsonModels.PostingPayload(
        requiredTextFact(facts, "postingId"),
        requiredTextFact(facts, "postingKind"),
        requiredTextFact(facts, "postingOriginKind"),
        requiredTextFact(facts, "reversalState"),
        requiredTextFact(facts, "effectiveDate"),
        requiredTextFact(facts, "recordedAt"),
        requiredTextFact(provenanceFacts, "actorId"),
        requiredTextFact(provenanceFacts, "actorType"),
        requiredTextFact(provenanceFacts, "commandId"),
        requiredTextFact(provenanceFacts, "idempotencyKey"),
        requiredTextFact(provenanceFacts, "causationId"),
        optionalTextFact(provenanceFacts, "correlationId"),
        requiredTextFact(provenanceFacts, "sourceChannel"),
        evidencePayload(evidenceFacts),
        reversalFacts == null ? null : reversalPayload(reversalFacts),
        groupedFacts(facts, "line").stream().map(CliPlanPayloadMapper::linePayload).toList());
  }

  private static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      List<LedgerFact> facts) {
    @Nullable List<LedgerFact> reversalFacts = optionalGroupFacts(facts, "reversal");
    List<LedgerFact> evidenceFacts = requiredGroupFacts(facts, "evidence");
    return new CliBookQueryJsonModels.PostingSummaryPayload(
        requiredTextFact(facts, "postingId"),
        requiredTextFact(facts, "postingKind"),
        requiredTextFact(facts, "postingOriginKind"),
        requiredTextFact(facts, "reversalState"),
        reversalFacts == null ? null : optionalTextFact(reversalFacts, "priorPostingId"),
        requiredTextFact(facts, "effectiveDate"),
        requiredTextFact(facts, "recordedAt"),
        requiredMoneyFact(facts, "debitTotal"),
        requiredMoneyFact(facts, "creditTotal"),
        textFacts(facts, "accountCode"),
        groupedFacts(evidenceFacts, "sourceDocument").stream()
            .map(groupFacts -> requiredTextFact(groupFacts, "sourceDocumentId"))
            .toList(),
        groupedFacts(evidenceFacts, "approval").stream()
            .map(groupFacts -> requiredTextFact(groupFacts, "approvalId"))
            .toList());
  }

  private static CliBookQueryJsonModels.AccountingEvidencePayload evidencePayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.AccountingEvidencePayload(
        groupedFacts(facts, "sourceDocument").stream()
            .map(CliPlanPayloadMapper::sourceDocumentPayload)
            .toList(),
        groupedFacts(facts, "approval").stream()
            .map(CliPlanPayloadMapper::approvalPayload)
            .toList());
  }

  private static CliBookQueryJsonModels.SourceDocumentPayload sourceDocumentPayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.SourceDocumentPayload(
        requiredTextFact(facts, "sourceDocumentId"),
        requiredTextFact(facts, "sourceDocumentType"),
        requiredTextFact(facts, "documentDate"),
        requiredTextFact(facts, "capturedAt"),
        requiredTextFact(facts, "storageLocator"),
        requiredTextFact(facts, "contentSha256"));
  }

  private static CliBookQueryJsonModels.ApprovalPayload approvalPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.ApprovalPayload(
        requiredTextFact(facts, "approvalId"),
        requiredTextFact(facts, "approvalType"),
        requiredTextFact(facts, "approverId"),
        requiredTextFact(facts, "approverType"),
        requiredTextFact(facts, "decision"),
        requiredTextFact(facts, "approvedAt"));
  }

  private static CliBookQueryJsonModels.ReversalPayload reversalPayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.ReversalPayload(
        requiredTextFact(facts, "priorPostingId"), requiredTextFact(facts, "reason"));
  }

  private static CliBookQueryJsonModels.JournalLinePayload linePayload(List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        requiredTextFact(facts, "accountCode"),
        requiredTextFact(facts, "side"),
        requiredMoneyFact(facts, "amount"));
  }

  private static CliBookQueryJsonModels.BalanceBucketPayload balanceBucketPayload(
      List<LedgerFact> facts) {
    return new CliBookQueryJsonModels.BalanceBucketPayload(
        requiredMoneyFact(facts, "debitTotal"),
        requiredMoneyFact(facts, "creditTotal"),
        requiredMoneyFact(facts, "netAmount"),
        requiredTextFact(facts, "balanceSide"));
  }

  private static List<CliPlanJsonModels.LedgerFactPayload> factPayloads(List<LedgerFact> facts) {
    return facts.stream().map(CliPlanPayloadMapper::ledgerFactPayload).toList();
  }

  private static CliPlanJsonModels.LedgerFactPayload ledgerFactPayload(LedgerFact fact) {
    return switch (fact) {
      case LedgerFact.Text text ->
          new CliPlanJsonModels.TextLedgerFactPayload("text", text.name(), text.value());
      case LedgerFact.Flag flag ->
          new CliPlanJsonModels.FlagLedgerFactPayload("flag", flag.name(), flag.value());
      case LedgerFact.Count count ->
          new CliPlanJsonModels.CountLedgerFactPayload("count", count.name(), count.value());
      case LedgerFact.Money money ->
          new CliPlanJsonModels.MoneyLedgerFactPayload("money", money.name(), money.value());
      case LedgerFact.Group group ->
          new CliPlanJsonModels.GroupLedgerFactPayload(
              "group", group.name(), factPayloads(group.facts()));
    };
  }

  private static String requiredTextFact(List<LedgerFact> facts, String name) {
    @Nullable String value = optionalTextFact(facts, name);
    if (value == null) {
      throw missingFact(name);
    }
    return value;
  }

  private static @Nullable String optionalTextFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Text text && text.name().equals(name)) {
        return text.value();
      }
    }
    return null;
  }

  private static boolean requiredFlagFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Flag flag && flag.name().equals(name)) {
        return flag.value();
      }
    }
    throw missingFact(name);
  }

  private static int requiredCountFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Count count && count.name().equals(name)) {
        return count.value();
      }
    }
    throw missingFact(name);
  }

  private static MonetaryAmount requiredMoneyFact(List<LedgerFact> facts, String name) {
    for (LedgerFact fact : facts) {
      if (fact instanceof LedgerFact.Money money && money.name().equals(name)) {
        return money.value();
      }
    }
    throw missingFact(name);
  }

  private static List<List<LedgerFact>> groupedFacts(List<LedgerFact> facts, String name) {
    return facts.stream()
        .filter(fact -> fact instanceof LedgerFact.Group group && group.name().equals(name))
        .map(fact -> ((LedgerFact.Group) fact).facts())
        .toList();
  }

  private static List<LedgerFact> requiredGroupFacts(List<LedgerFact> facts, String name) {
    @Nullable List<LedgerFact> groupFacts = optionalGroupFacts(facts, name);
    if (groupFacts == null) {
      throw missingFact(name);
    }
    return groupFacts;
  }

  private static @Nullable List<LedgerFact> optionalGroupFacts(
      List<LedgerFact> facts, String name) {
    return groupedFacts(facts, name).stream().findFirst().orElse(null);
  }

  private static List<String> textFacts(List<LedgerFact> facts, String name) {
    return facts.stream()
        .filter(fact -> fact instanceof LedgerFact.Text text && text.name().equals(name))
        .map(fact -> ((LedgerFact.Text) fact).value())
        .toList();
  }

  private static IllegalArgumentException missingFact(String name) {
    return new IllegalArgumentException("Missing ledger fact: " + name);
  }
}
