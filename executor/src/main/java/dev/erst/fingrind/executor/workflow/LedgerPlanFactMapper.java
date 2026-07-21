package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;

/** Canonical machine-facing fact expansion for ledger-plan journal steps. */
public final class LedgerPlanFactMapper {
  private LedgerPlanFactMapper() {}

  /** Expands one account-declaration outcome into workflow-owned machine facts. */
  public static List<BookWorkflowFact> accountDeclarationFacts(
      String outcome, RegisteredAccount account) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("outcome", outcome));
    facts.add(BookWorkflowFact.text("accountCode", account.accountCode().value()));
    facts.add(BookWorkflowFact.text("accountName", account.accountName().value()));
    facts.add(BookWorkflowFact.text("accountType", account.accountType().wireValue()));
    facts.add(
        BookWorkflowFact.text("accountNodeKind", account.accountTaxonomy().nodeKind().wireValue()));
    account
        .accountTaxonomy()
        .parentAccountCode()
        .ifPresent(
            parentAccountCode ->
                facts.add(BookWorkflowFact.text("parentAccountCode", parentAccountCode.value())));
    account
        .accountTaxonomy()
        .contraOfAccountCode()
        .ifPresent(
            contraOfAccountCode ->
                facts.add(
                    BookWorkflowFact.text("contraOfAccountCode", contraOfAccountCode.value())));
    account
        .accountTaxonomy()
        .financialPositionLineClassification()
        .ifPresent(
            classification ->
                facts.add(
                    BookWorkflowFact.text(
                        "financialPositionLineClassification", classification.wireValue())));
    account
        .accountTaxonomy()
        .profitAndLossLineClassification()
        .ifPresent(
            classification ->
                facts.add(
                    BookWorkflowFact.text(
                        "profitAndLossLineClassification", classification.wireValue())));
    if (account.unitOfMeasure() != null) {
      facts.add(
          BookWorkflowFact.group(
              "unitOfMeasure",
              List.of(
                  BookWorkflowFact.text("token", account.unitOfMeasure().token()),
                  BookWorkflowFact.count(
                      "quantityScale", account.unitOfMeasure().quantityScale()))));
    }
    facts.add(BookWorkflowFact.text("normalBalance", account.normalBalance().wireValue()));
    facts.add(BookWorkflowFact.flag("active", account.active()));
    facts.add(BookWorkflowFact.text("declaredAt", account.declaredAt().toString()));
    return List.copyOf(facts);
  }

  /** Expands one declared tax registration into workflow-owned machine facts. */
  public static List<BookWorkflowFact> taxRegistrationFacts(
      String outcome, DeclaredTaxRegistration registration) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("outcome", outcome));
    facts.add(BookWorkflowFact.text("taxRegistrationId", registration.taxRegistrationId().value()));
    facts.add(
        BookWorkflowFact.text("taxRegistrationName", registration.taxRegistrationName().value()));
    facts.add(BookWorkflowFact.text("jurisdiction", registration.jurisdiction().value()));
    if (registration.registrationNumber() != null) {
      facts.add(
          BookWorkflowFact.text("registrationNumber", registration.registrationNumber().value()));
    }
    facts.add(
        BookWorkflowFact.text("payableAccountCode", registration.payableAccountCode().value()));
    facts.add(
        BookWorkflowFact.text(
            "recoverableAccountCode", registration.recoverableAccountCode().value()));
    facts.add(
        BookWorkflowFact.text(
            "obligationFrequency", registration.obligationFrequency().wireValue()));
    facts.add(
        BookWorkflowFact.count("dueDaysAfterPeriodEnd", registration.dueDaysAfterPeriodEnd()));
    facts.add(BookWorkflowFact.count("taxCodeCount", registration.taxCodes().size()));
    registration
        .taxCodes()
        .forEach(taxCode -> facts.add(BookWorkflowFact.group("taxCode", taxCodeFacts(taxCode))));
    facts.add(BookWorkflowFact.text("declaredAt", registration.declaredAt().toString()));
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> taxCodeFacts(TaxCodeDefinition taxCode) {
    return List.of(
        BookWorkflowFact.text("taxCode", taxCode.taxCode().value()),
        BookWorkflowFact.text("taxCodeName", taxCode.taxCodeName().value()),
        BookWorkflowFact.count("ratePartsPerMillion", taxCode.rate().partsPerMillionOfWhole()),
        BookWorkflowFact.text("inclusionMode", taxCode.inclusionMode().wireValue()),
        BookWorkflowFact.text("applicationKind", taxCode.applicationKind().wireValue()));
  }

  /** Expands one paginated account-registry result into workflow-owned machine facts. */
  public static List<BookWorkflowFact> accountPageFacts(AccountRegistryPage page) {
    List<BookWorkflowFact> facts = new ArrayList<>(pageFacts(page.accounts().size(), page.limit()));
    page.nextCursor()
        .ifPresent(cursor -> facts.add(BookWorkflowFact.text("nextCursor", cursor.wireValue())));
    facts.add(BookWorkflowFact.flag("hasMore", page.hasMore()));
    page.accounts()
        .forEach(
            account ->
                facts.add(
                    BookWorkflowFact.group(
                        "account", accountDeclarationFacts("declared", account))));
    return List.copyOf(facts);
  }

  /** Expands one paginated posting-history result into workflow-owned machine facts. */
  public static List<BookWorkflowFact> postingPageFacts(PostingHistoryPage page) {
    List<BookWorkflowFact> facts = new ArrayList<>(pageFacts(page.postings().size(), page.limit()));
    page.nextCursor()
        .ifPresent(cursor -> facts.add(BookWorkflowFact.text("nextCursor", cursor.wireValue())));
    facts.add(BookWorkflowFact.flag("hasMore", page.hasMore()));
    page.postings()
        .forEach(posting -> facts.add(BookWorkflowFact.group("posting", postingFacts(posting))));
    return List.copyOf(facts);
  }

  /** Expands one committed posting into workflow-owned machine facts. */
  public static List<BookWorkflowFact> postingFacts(CommittedPosting postingFact) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("postingId", postingFact.postingId().value()));
    facts.add(BookWorkflowFact.text("postingKind", postingFact.postingKind().wireValue()));
    facts.add(
        BookWorkflowFact.text("postingOriginKind", postingFact.postingOriginKind().wireValue()));
    facts.add(
        BookWorkflowFact.text(
            "reversalState", postingFact.reversalReference().isPresent() ? "reversal" : "direct"));
    facts.add(
        BookWorkflowFact.text(
            "idempotencyKey",
            postingFact.provenance().requestProvenance().idempotencyKey().value()));
    facts.add(
        BookWorkflowFact.text(
            "effectiveDate", postingFact.journalEntry().effectiveDate().toString()));
    facts.add(
        BookWorkflowFact.text("recordedAt", postingFact.provenance().recordedAt().toString()));
    facts.add(
        BookWorkflowFact.money("debitTotal", MonetaryAmount.of(postingDebitTotal(postingFact))));
    facts.add(
        BookWorkflowFact.money("creditTotal", MonetaryAmount.of(postingCreditTotal(postingFact))));
    postingFact.journalEntry().lines().stream()
        .map(line -> line.accountCode().value())
        .distinct()
        .forEach(accountCode -> facts.add(BookWorkflowFact.text("accountCode", accountCode)));
    facts.add(BookWorkflowFact.group("provenance", provenanceFacts(postingFact.provenance())));
    facts.add(BookWorkflowFact.group("evidence", evidenceFacts(postingFact.evidence())));
    postingFact
        .callerAuthoredEntry()
        .ifPresent(
            entry ->
                facts.add(
                    BookWorkflowFact.group("entry", LedgerPlanEntryFactMapper.entryFacts(entry))));
    postingFact
        .journalEntry()
        .lines()
        .forEach(line -> facts.add(BookWorkflowFact.group("line", journalLineFacts(line))));
    postingFact
        .reversalReference()
        .ifPresent(
            reversalReference -> {
              List<BookWorkflowFact> reversalFacts = new ArrayList<>();
              reversalFacts.add(
                  BookWorkflowFact.text(
                      "priorPostingId", reversalReference.priorPostingId().value()));
              postingFact
                  .reversalReason()
                  .ifPresent(
                      reason -> reversalFacts.add(BookWorkflowFact.text("reason", reason.value())));
              facts.add(BookWorkflowFact.group("reversal", List.copyOf(reversalFacts)));
            });
    return List.copyOf(facts);
  }

  private static Money postingDebitTotal(CommittedPosting postingFact) {
    long debitMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), debitMinorUnits);
  }

  private static Money postingCreditTotal(CommittedPosting postingFact) {
    long creditMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.CREDIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), creditMinorUnits);
  }

  /** Expands one local account-balance view into workflow-owned machine facts. */
  public static List<BookWorkflowFact> balanceFacts(AccountBalanceView view) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(
        BookWorkflowFact.group("account", accountDeclarationFacts("declared", view.account())));
    view.effectiveDateRange()
        .effectiveDateFrom()
        .ifPresent(
            effectiveDateFrom ->
                facts.add(
                    BookWorkflowFact.text("effectiveDateFrom", effectiveDateFrom.toString())));
    view.effectiveDateRange()
        .effectiveDateTo()
        .ifPresent(
            effectiveDateTo ->
                facts.add(BookWorkflowFact.text("effectiveDateTo", effectiveDateTo.toString())));
    facts.add(BookWorkflowFact.count("bucketCount", view.balances().size()));
    for (CurrencyBalance balance : view.balances()) {
      facts.add(
          BookWorkflowFact.group(
              "balance",
              List.of(
                  BookWorkflowFact.money("debitTotal", MonetaryAmount.of(balance.debitTotal())),
                  BookWorkflowFact.money("creditTotal", MonetaryAmount.of(balance.creditTotal())),
                  BookWorkflowFact.money("netAmount", MonetaryAmount.of(balance.netAmount())),
                  BookWorkflowFact.text("balanceSide", balance.balanceSide().wireValue()))));
    }
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> pageFacts(int count, int limit) {
    return List.of(
        BookWorkflowFact.count("count", count), BookWorkflowFact.count("pageLimit", limit));
  }

  private static List<BookWorkflowFact> provenanceFacts(CommittedProvenance provenance) {
    RequestProvenance requestProvenance = provenance.requestProvenance();
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.text("commandId", requestProvenance.commandId().value()));
    facts.add(BookWorkflowFact.text("idempotencyKey", requestProvenance.idempotencyKey().value()));
    facts.add(BookWorkflowFact.text("causationId", requestProvenance.causationId().value()));
    requestProvenance
        .correlationId()
        .ifPresent(
            correlationId ->
                facts.add(BookWorkflowFact.text("correlationId", correlationId.value())));
    facts.add(BookWorkflowFact.text("recordedAt", provenance.recordedAt().toString()));
    facts.add(BookWorkflowFact.text("sourceChannel", provenance.sourceChannel().wireValue()));
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> evidenceFacts(AccountingEvidence evidence) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("sourceDocumentCount", evidence.sourceDocuments().size()));
    facts.add(BookWorkflowFact.count("approvalCount", evidence.approvals().size()));
    evidence
        .sourceDocuments()
        .forEach(
            sourceDocument ->
                facts.add(
                    BookWorkflowFact.group("sourceDocument", sourceDocumentFacts(sourceDocument))));
    evidence
        .approvals()
        .forEach(
            approval -> facts.add(BookWorkflowFact.group("approval", approvalFacts(approval))));
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> sourceDocumentFacts(
      SourceDocumentReference sourceDocument) {
    return List.of(
        BookWorkflowFact.text("sourceDocumentId", sourceDocument.sourceDocumentId().value()),
        BookWorkflowFact.text("sourceDocumentType", sourceDocument.sourceDocumentType().value()),
        BookWorkflowFact.text("documentDate", sourceDocument.documentDate().toString()));
  }

  private static List<BookWorkflowFact> approvalFacts(ApprovalReference approval) {
    return List.of(
        BookWorkflowFact.text("approvalId", approval.approvalId().value()),
        BookWorkflowFact.text("approvalType", approval.approvalType().value()),
        BookWorkflowFact.text("approverReference", approval.approverReference()),
        BookWorkflowFact.text("approverType", approval.approverType()),
        BookWorkflowFact.text("decision", approval.decision().wireValue()),
        BookWorkflowFact.text("approvedAt", approval.approvedAt().toString()));
  }

  private static List<BookWorkflowFact> journalLineFacts(JournalLine line) {
    return List.of(
        BookWorkflowFact.text("accountCode", line.accountCode().value()),
        BookWorkflowFact.text("side", line.side().wireValue()),
        BookWorkflowFact.money("amount", MonetaryAmount.of(line.amount().money())));
  }
}
