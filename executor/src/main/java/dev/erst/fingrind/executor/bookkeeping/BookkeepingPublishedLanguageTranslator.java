package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import java.util.Objects;

/** Translates between the public published language and the local bookkeeping model. */
public final class BookkeepingPublishedLanguageTranslator {
  private BookkeepingPublishedLanguageTranslator() {}

  /** Translates one public declare-account request into the local bookkeeping model. */
  public static AccountDeclaration fromPublished(DeclareAccountCommand command) {
    Objects.requireNonNull(command, "command");
    return new AccountDeclaration(
        command.accountCode(), command.accountName(), command.normalBalance());
  }

  /** Translates one public post-entry request into the local bookkeeping model. */
  public static PostingCommand fromPublished(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    return new PostingCommand(
        command.journalEntry(),
        fromPublished(command.postingLineage()),
        command.requestProvenance(),
        command.sourceChannel());
  }

  /** Translates one published committed posting into the local bookkeeping model. */
  public static CommittedPosting fromPublished(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return new CommittedPosting(
        postingFact.postingId(),
        postingFact.journalEntry(),
        fromPublished(postingFact.postingLineage()),
        postingFact.provenance());
  }

  /** Translates one published posting lineage into the bookkeeping model. */
  public static PostingLineageModel fromPublished(PostingLineage postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineage.Direct _ -> PostingLineageModel.direct();
      case PostingLineage.Reversal reversal ->
          PostingLineageModel.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Translates one bookkeeping registered account into the public response model. */
  public static DeclaredAccount toPublished(RegisteredAccount account) {
    Objects.requireNonNull(account, "account");
    return new DeclaredAccount(
        account.accountCode(),
        account.accountName(),
        account.normalBalance(),
        account.active(),
        account.declaredAt());
  }

  /** Translates one bookkeeping committed posting into the public response model. */
  public static PostingFact toPublished(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new PostingFact(
        posting.postingId(),
        posting.journalEntry(),
        toPublished(posting.postingLineage()),
        posting.provenance());
  }

  /** Translates one bookkeeping opening outcome into the public response model. */
  public static OpenBookResult toPublished(BookOpeningOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case BookOpeningOutcome.Opened opened -> new OpenBookResult.Opened(opened.initializedAt());
      case BookOpeningOutcome.Rejected rejected ->
          new OpenBookResult.Rejected(rejected.rejection());
    };
  }

  /** Translates one bookkeeping account-declaration outcome into the public response model. */
  public static DeclareAccountResult toPublished(AccountDeclarationOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case AccountDeclarationOutcome.Declared declared ->
          new DeclareAccountResult.Declared(toPublished(declared.account()));
      case AccountDeclarationOutcome.Rejected rejected ->
          new DeclareAccountResult.Rejected(rejected.rejection());
    };
  }

  /** Translates one bookkeeping lineage back into the public published language. */
  public static PostingLineage toPublished(PostingLineageModel postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineageModel.Direct _ -> PostingLineage.direct();
      case PostingLineageModel.Reversal reversal ->
          PostingLineage.reversal(reversal.reference(), reversal.reason());
    };
  }
}
