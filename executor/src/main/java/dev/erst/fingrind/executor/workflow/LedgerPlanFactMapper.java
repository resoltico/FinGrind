package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestProvenance;
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

  /** Expands one declared account into workflow-owned machine facts. */
  public static List<BookWorkflowFact> declaredAccountFacts(RegisteredAccount account) {
    return List.of(
        BookWorkflowFact.text("accountCode", account.accountCode().value()),
        BookWorkflowFact.text("accountName", account.accountName().value()),
        BookWorkflowFact.text("normalBalance", account.normalBalance().wireValue()),
        BookWorkflowFact.flag("active", account.active()),
        BookWorkflowFact.text("declaredAt", account.declaredAt().toString()));
  }

  /** Expands one paginated account-registry result into workflow-owned machine facts. */
  public static List<BookWorkflowFact> accountPageFacts(AccountRegistryPage page) {
    List<BookWorkflowFact> facts = new ArrayList<>(pageFacts(page.accounts().size(), page.limit()));
    page.nextCursor()
        .ifPresent(cursor -> facts.add(BookWorkflowFact.text("nextCursor", cursor.wireValue())));
    facts.add(BookWorkflowFact.flag("hasMore", page.hasMore()));
    page.accounts()
        .forEach(
            account -> facts.add(BookWorkflowFact.group("account", declaredAccountFacts(account))));
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
    facts.add(
        BookWorkflowFact.text(
            "idempotencyKey",
            postingFact.provenance().requestProvenance().idempotencyKey().value()));
    facts.add(
        BookWorkflowFact.text(
            "effectiveDate", postingFact.journalEntry().effectiveDate().toString()));
    facts.add(
        BookWorkflowFact.text("recordedAt", postingFact.provenance().recordedAt().toString()));
    facts.add(BookWorkflowFact.group("provenance", provenanceFacts(postingFact.provenance())));
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

  /** Expands one local account-balance view into workflow-owned machine facts. */
  public static List<BookWorkflowFact> balanceFacts(AccountBalanceView view) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.group("account", declaredAccountFacts(view.account())));
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
                  BookWorkflowFact.text("currencyCode", balance.netAmount().currencyCode().value()),
                  BookWorkflowFact.text(
                      "debitTotal", balance.debitTotal().amount().toPlainString()),
                  BookWorkflowFact.text(
                      "creditTotal", balance.creditTotal().amount().toPlainString()),
                  BookWorkflowFact.text("netAmount", balance.netAmount().amount().toPlainString()),
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
    facts.add(BookWorkflowFact.text("actorId", requestProvenance.actorId().value()));
    facts.add(BookWorkflowFact.text("actorType", requestProvenance.actorType().wireValue()));
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

  private static List<BookWorkflowFact> journalLineFacts(JournalLine line) {
    return List.of(
        BookWorkflowFact.text("accountCode", line.accountCode().value()),
        BookWorkflowFact.text("side", line.side().wireValue()),
        BookWorkflowFact.text("currencyCode", line.amount().currencyCode().value()),
        BookWorkflowFact.text("amount", line.amount().amount().toPlainString()));
  }
}
