package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared contract-model fixtures for split behavior-owned tests. */
class ContractTestSupport {
  protected LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  protected LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  protected DeclaredAccount declaredAccount(String accountCode) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName("Cash"),
        AccountType.ASSET,
        AccountRole.ORDINARY,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(dev.erst.fingrind.core.FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()),
        true,
        Instant.parse("2026-04-07T10:15:30Z"));
  }

  protected PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(),
        PostingLineage.direct(),
        PostingKind.STANDARD,
        ContractFixtures.accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty()),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  protected JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("10.00")),
            new JournalLine(
                new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("10.00"))));
  }

  protected Money money(String amount) {
    return Money.parse("EUR", amount);
  }

  protected MonetaryAmount monetaryAmount(String currencyCode, String amountText) {
    return MonetaryAmount.of(Money.parse(currencyCode, amountText));
  }

  protected BookIdentity bookIdentity() {
    return ContractFixtures.bookIdentity();
  }

  protected OpenBookCommand openBookCommand() {
    return ContractFixtures.openBookCommand();
  }

  protected PostingCoverage postingCoverage() {
    return ContractFixtures.postingCoverage();
  }

  protected AccountPage accountPage(
      List<DeclaredAccount> accounts, int limit, Optional<AccountPageCursor> nextCursor) {
    return ContractFixtures.accountPage(accounts, limit, nextCursor);
  }

  protected PostingPage postingPage(
      List<PostingFact> postings, int limit, Optional<PostingPageCursor> nextCursor) {
    return ContractFixtures.postingPage(
        Optional.empty(), EffectiveDateRange.unbounded(), postings, limit, nextCursor);
  }

  protected PostingPage postingPage(
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor) {
    return ContractFixtures.postingPage(
        accountCodeFilter, effectiveDateRange, postings, limit, nextCursor);
  }

  protected GetPostingResult.Found foundPosting(PostingFact postingFact) {
    return new GetPostingResult.Found(bookIdentity(), postingFact);
  }
}
