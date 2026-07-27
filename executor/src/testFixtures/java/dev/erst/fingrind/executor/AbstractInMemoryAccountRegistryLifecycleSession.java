package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryLifecyclePolicy;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementDecision;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Adds Account Registry lifecycle behavior to in-memory fixtures that model posting history. */
abstract class AbstractInMemoryAccountRegistryLifecycleSession
    extends AbstractInMemoryBookAdministrationSession {
  @Override
  public AccountAmendmentOutcome amendAccount(
      AccountDeclaration amendment,
      Instant amendedAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          requireDirectMutationPermitted();
          if (!initialized) {
            return new AccountAmendmentOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          AccountAmendmentDecision decision =
              AccountRegistryLifecyclePolicy.amend(
                  accountsByCode.get(amendment.accountCode()),
                  amendment,
                  amendmentDependencies(amendment.accountCode()));
          if (decision instanceof AccountAmendmentDecision.Amended amended) {
            accountsByCode.put(amendment.accountCode(), amended.account());
          }
          return switch (decision) {
            case AccountAmendmentDecision.Amended amended ->
                new AccountAmendmentOutcome.Amended(amended.account(), IN_MEMORY_DIRECT_APPEND);
            case AccountAmendmentDecision.Unchanged unchanged ->
                new AccountAmendmentOutcome.Unchanged(unchanged.account());
            case AccountAmendmentDecision.Rejected rejected ->
                new AccountAmendmentOutcome.Rejected(rejected.rejection());
          };
        });
  }

  /**
   * Test-fixture-only lifecycle seeding that never represents a protected-book production write.
   */
  AccountAmendmentOutcome amendAccount(AccountDeclaration amendment, Instant amendedAt) {
    return amendAccount(
        amendment,
        amendedAt,
        ignored -> {
          throw new AssertionError(
              "In-memory fixture seeding must not invoke an attestation signer.");
        });
  }

  @Override
  public AccountRetirementOutcome retireAccount(
      AccountCode accountCode,
      Instant retiredAt,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    return InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          requireDirectMutationPermitted();
          if (!initialized) {
            return new AccountRetirementOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized());
          }
          AccountRetirementDecision decision =
              AccountRegistryLifecyclePolicy.retire(
                  accountCode,
                  accountsByCode.get(accountCode),
                  retirementDependencies(accountCode),
                  currentBalanceZero(accountCode));
          if (decision instanceof AccountRetirementDecision.Retired retired) {
            accountsByCode.put(accountCode, retired.account());
          }
          return switch (decision) {
            case AccountRetirementDecision.Retired retired ->
                new AccountRetirementOutcome.Retired(retired.account(), IN_MEMORY_DIRECT_APPEND);
            case AccountRetirementDecision.Unchanged unchanged ->
                new AccountRetirementOutcome.Unchanged(unchanged.account());
            case AccountRetirementDecision.Rejected rejected ->
                new AccountRetirementOutcome.Rejected(rejected.rejection());
          };
        });
  }

  /**
   * Test-fixture-only lifecycle seeding that never represents a protected-book production write.
   */
  AccountRetirementOutcome retireAccount(AccountCode accountCode, Instant retiredAt) {
    return retireAccount(
        accountCode,
        retiredAt,
        ignored -> {
          throw new AssertionError(
              "In-memory fixture seeding must not invoke an attestation signer.");
        });
  }

  /** Returns durable relationships that prohibit amendment of the named account. */
  protected List<AccountRegistryDependency> amendmentDependencies(AccountCode accountCode) {
    return lifecycleDependencies(accountCode, true);
  }

  /** Returns live operational relationships that prohibit retirement of the named account. */
  protected List<AccountRegistryDependency> retirementDependencies(AccountCode accountCode) {
    return lifecycleDependencies(accountCode, false);
  }

  /** Returns whether every functional-currency balance bucket for the named account is zero. */
  protected boolean currentBalanceZero(AccountCode accountCode) {
    return true;
  }

  /**
   * Lets posting-capable fixtures report durable posting history without weakening this base seam.
   */
  protected boolean hasPostingHistory(AccountCode accountCode) {
    return false;
  }

  /** Lets tax-capable fixtures report registrations that still bind the named account. */
  protected boolean hasTaxRegistrationBinding(AccountCode accountCode) {
    return false;
  }

  private List<AccountRegistryDependency> lifecycleDependencies(
      AccountCode accountCode, boolean includePostingHistory) {
    List<AccountRegistryDependency> dependencies = new ArrayList<>();
    if (includePostingHistory && hasPostingHistory(accountCode)) {
      dependencies.add(AccountRegistryDependency.POSTINGS);
    }
    if (hasTaxRegistrationBinding(accountCode)) {
      dependencies.add(AccountRegistryDependency.TAX_REGISTRATIONS);
    }
    if (accountsByCode.values().stream()
        .map(RegisteredAccount::accountTaxonomy)
        .flatMap(taxonomy -> taxonomy.parentAccountCode().stream())
        .anyMatch(accountCode::equals)) {
      dependencies.add(AccountRegistryDependency.CHILD_ACCOUNTS);
    }
    return List.copyOf(dependencies);
  }
}
