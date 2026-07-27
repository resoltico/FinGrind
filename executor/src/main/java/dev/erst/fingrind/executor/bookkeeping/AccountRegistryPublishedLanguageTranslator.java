package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import java.util.Objects;

/** Translates Account Registry lifecycle requests and outcomes across the public boundary. */
public final class AccountRegistryPublishedLanguageTranslator {
  private AccountRegistryPublishedLanguageTranslator() {}

  /** Translates one published account amendment into the local Account Registry language. */
  public static AccountDeclaration fromPublished(AmendAccountCommand command) {
    Objects.requireNonNull(command, "command");
    return new AccountDeclaration(
        command.accountCode(),
        command.accountName(),
        command.accountType(),
        command.accountTaxonomy(),
        command.unitOfMeasure());
  }

  /** Translates one local account-amendment outcome into the public contract. */
  public static AmendAccountResult toPublished(AccountAmendmentOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case AccountAmendmentOutcome.Amended amended ->
          new AmendAccountResult.Amended(
              BookkeepingPublishedLanguageTranslator.toPublished(amended.account()),
              AttestationCommitProjection.fromVerifiedAppend(
                  amended.attestationAppend().requireAppended()));
      case AccountAmendmentOutcome.Unchanged unchanged ->
          new AmendAccountResult.Unchanged(
              BookkeepingPublishedLanguageTranslator.toPublished(unchanged.account()), null);
      case AccountAmendmentOutcome.Rejected rejected ->
          new AmendAccountResult.Rejected(
              BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }

  /** Translates one local account-retirement outcome into the public contract. */
  public static RetireAccountResult toPublished(AccountRetirementOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case AccountRetirementOutcome.Retired retired ->
          new RetireAccountResult.Retired(
              BookkeepingPublishedLanguageTranslator.toPublished(retired.account()),
              AttestationCommitProjection.fromVerifiedAppend(
                  retired.attestationAppend().requireAppended()));
      case AccountRetirementOutcome.Unchanged unchanged ->
          new RetireAccountResult.Unchanged(
              BookkeepingPublishedLanguageTranslator.toPublished(unchanged.account()), null);
      case AccountRetirementOutcome.Rejected rejected ->
          new RetireAccountResult.Rejected(
              BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }
}
