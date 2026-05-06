package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerFact;
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
final class LedgerPlanFactMapper {
  private LedgerPlanFactMapper() {}

  static List<LedgerFact> declaredAccountFacts(RegisteredAccount account) {
    return List.of(
        LedgerFact.text("accountCode", account.accountCode().value()),
        LedgerFact.text("accountName", account.accountName().value()),
        LedgerFact.text("normalBalance", account.normalBalance().wireValue()),
        LedgerFact.flag("active", account.active()),
        LedgerFact.text("declaredAt", account.declaredAt().toString()));
  }

  static List<LedgerFact> accountPageFacts(AccountRegistryPage page) {
    List<LedgerFact> facts = new ArrayList<>(pageFacts(page.accounts().size(), page.limit()));
    page.nextCursor()
        .ifPresent(cursor -> facts.add(LedgerFact.text("nextCursor", cursor.wireValue())));
    facts.add(LedgerFact.flag("hasMore", page.hasMore()));
    page.accounts()
        .forEach(account -> facts.add(LedgerFact.group("account", declaredAccountFacts(account))));
    return List.copyOf(facts);
  }

  static List<LedgerFact> postingPageFacts(PostingHistoryPage page) {
    List<LedgerFact> facts = new ArrayList<>(pageFacts(page.postings().size(), page.limit()));
    page.nextCursor()
        .ifPresent(cursor -> facts.add(LedgerFact.text("nextCursor", cursor.wireValue())));
    facts.add(LedgerFact.flag("hasMore", page.hasMore()));
    page.postings()
        .forEach(posting -> facts.add(LedgerFact.group("posting", postingFacts(posting))));
    return List.copyOf(facts);
  }

  static List<LedgerFact> postingFacts(CommittedPosting postingFact) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.text("postingId", postingFact.postingId().value()));
    facts.add(
        LedgerFact.text(
            "idempotencyKey",
            postingFact.provenance().requestProvenance().idempotencyKey().value()));
    facts.add(
        LedgerFact.text("effectiveDate", postingFact.journalEntry().effectiveDate().toString()));
    facts.add(LedgerFact.text("recordedAt", postingFact.provenance().recordedAt().toString()));
    facts.add(LedgerFact.group("provenance", provenanceFacts(postingFact.provenance())));
    postingFact
        .journalEntry()
        .lines()
        .forEach(line -> facts.add(LedgerFact.group("line", journalLineFacts(line))));
    postingFact
        .reversalReference()
        .ifPresent(
            reversalReference -> {
              List<LedgerFact> reversalFacts = new ArrayList<>();
              reversalFacts.add(
                  LedgerFact.text("priorPostingId", reversalReference.priorPostingId().value()));
              postingFact
                  .reversalReason()
                  .ifPresent(
                      reason -> reversalFacts.add(LedgerFact.text("reason", reason.value())));
              facts.add(LedgerFact.group("reversal", List.copyOf(reversalFacts)));
            });
    return List.copyOf(facts);
  }

  static List<LedgerFact> balanceFacts(AccountBalanceView view) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.group("account", declaredAccountFacts(view.account())));
    view.effectiveDateRange()
        .effectiveDateFrom()
        .ifPresent(
            effectiveDateFrom ->
                facts.add(LedgerFact.text("effectiveDateFrom", effectiveDateFrom.toString())));
    view.effectiveDateRange()
        .effectiveDateTo()
        .ifPresent(
            effectiveDateTo ->
                facts.add(LedgerFact.text("effectiveDateTo", effectiveDateTo.toString())));
    facts.add(LedgerFact.count("bucketCount", view.balances().size()));
    for (CurrencyBalance balance : view.balances()) {
      facts.add(
          LedgerFact.group(
              "balance",
              List.of(
                  LedgerFact.text("currencyCode", balance.netAmount().currencyCode().value()),
                  LedgerFact.text("debitTotal", balance.debitTotal().amount().toPlainString()),
                  LedgerFact.text("creditTotal", balance.creditTotal().amount().toPlainString()),
                  LedgerFact.text("netAmount", balance.netAmount().amount().toPlainString()),
                  LedgerFact.text("balanceSide", balance.balanceSide().wireValue()))));
    }
    return List.copyOf(facts);
  }

  private static List<LedgerFact> pageFacts(int count, int limit) {
    return List.of(LedgerFact.count("count", count), LedgerFact.count("pageLimit", limit));
  }

  private static List<LedgerFact> provenanceFacts(CommittedProvenance provenance) {
    RequestProvenance requestProvenance = provenance.requestProvenance();
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.text("actorId", requestProvenance.actorId().value()));
    facts.add(LedgerFact.text("actorType", requestProvenance.actorType().wireValue()));
    facts.add(LedgerFact.text("commandId", requestProvenance.commandId().value()));
    facts.add(LedgerFact.text("idempotencyKey", requestProvenance.idempotencyKey().value()));
    facts.add(LedgerFact.text("causationId", requestProvenance.causationId().value()));
    requestProvenance
        .correlationId()
        .ifPresent(
            correlationId -> facts.add(LedgerFact.text("correlationId", correlationId.value())));
    facts.add(LedgerFact.text("recordedAt", provenance.recordedAt().toString()));
    facts.add(LedgerFact.text("sourceChannel", provenance.sourceChannel().wireValue()));
    return List.copyOf(facts);
  }

  private static List<LedgerFact> journalLineFacts(JournalLine line) {
    return List.of(
        LedgerFact.text("accountCode", line.accountCode().value()),
        LedgerFact.text("side", line.side().wireValue()),
        LedgerFact.text("currencyCode", line.amount().currencyCode().value()),
        LedgerFact.text("amount", line.amount().amount().toPlainString()));
  }
}
