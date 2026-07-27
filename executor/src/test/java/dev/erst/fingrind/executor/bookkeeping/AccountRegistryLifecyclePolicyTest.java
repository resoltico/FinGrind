package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Business examples for the Account Registry's amend and retirement invariants. */
class AccountRegistryLifecyclePolicyTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-07-14T10:15:30Z");
  private static final AccountCode ACCOUNT_CODE = new AccountCode("1010");

  @Test
  void amend_replacesAnUnreferencedNeverPostedAccountDefinition() {
    AccountAmendmentDecision.Amended amended =
        assertInstanceOf(
            AccountAmendmentDecision.Amended.class,
            AccountRegistryLifecyclePolicy.amend(
                currentAssetAccount(), nonCurrentAssetAmendment(), List.of()));

    assertEquals("Operating Reserve", amended.account().accountName().value());
    assertEquals(
        FinancialPositionLineClassification.NONCURRENT_ASSET,
        amended.account().accountTaxonomy().financialPositionLineClassification().orElseThrow());
    assertEquals(DECLARED_AT, amended.account().declaredAt());
  }

  @Test
  void amend_refusesEachDurableDependency() {
    for (AccountRegistryDependency dependency : AccountRegistryDependency.values()) {
      AccountAmendmentDecision.Rejected rejected =
          assertInstanceOf(
              AccountAmendmentDecision.Rejected.class,
              AccountRegistryLifecyclePolicy.amend(
                  currentAssetAccount(), nonCurrentAssetAmendment(), List.of(dependency)));

      AccountRegistryLifecycleRejection.AccountHasDependents rejectionDetail =
          assertInstanceOf(
              AccountRegistryLifecycleRejection.AccountHasDependents.class, rejected.rejection());
      assertEquals(ACCOUNT_CODE, rejectionDetail.accountCode());
      assertEquals(List.of(dependency), rejectionDetail.dependencies());
    }
  }

  @Test
  void amend_rejectsMissingAccountsAndMakesNoChangeWhenDefinitionsAlreadyMatch() {
    AccountAmendmentDecision.Rejected missing =
        assertInstanceOf(
            AccountAmendmentDecision.Rejected.class,
            AccountRegistryLifecyclePolicy.amend(null, nonCurrentAssetAmendment(), List.of()));
    AccountAmendmentDecision.Unchanged unchanged =
        assertInstanceOf(
            AccountAmendmentDecision.Unchanged.class,
            AccountRegistryLifecyclePolicy.amend(
                currentAssetAccount(), currentAssetAmendment(), List.of()));

    assertEquals(
        new AccountRegistryLifecycleRejection.AccountNotFound(ACCOUNT_CODE), missing.rejection());
    assertEquals(currentAssetAccount(), unchanged.account());
  }

  @Test
  void retire_requiresZeroBalanceAndNoLiveOperationalReference() {
    AccountRetirementDecision.Rejected balanceRejected =
        assertInstanceOf(
            AccountRetirementDecision.Rejected.class,
            AccountRegistryLifecyclePolicy.retire(
                ACCOUNT_CODE, currentAssetAccount(), List.of(), false));
    assertInstanceOf(
        AccountRegistryLifecycleRejection.AccountBalanceNotZero.class, balanceRejected.rejection());

    AccountRetirementDecision.Rejected dependencyRejected =
        assertInstanceOf(
            AccountRetirementDecision.Rejected.class,
            AccountRegistryLifecyclePolicy.retire(
                ACCOUNT_CODE,
                currentAssetAccount(),
                List.of(
                    AccountRegistryDependency.TAX_REGISTRATIONS,
                    AccountRegistryDependency.CHILD_ACCOUNTS),
                true));
    AccountRegistryLifecycleRejection.AccountHasDependents rejectionDetail =
        assertInstanceOf(
            AccountRegistryLifecycleRejection.AccountHasDependents.class,
            dependencyRejected.rejection());
    assertEquals(
        List.of(
            AccountRegistryDependency.TAX_REGISTRATIONS, AccountRegistryDependency.CHILD_ACCOUNTS),
        rejectionDetail.dependencies());
  }

  @Test
  void retire_rejectsMissingAccounts() {
    AccountRetirementDecision.Rejected missing =
        assertInstanceOf(
            AccountRetirementDecision.Rejected.class,
            AccountRegistryLifecyclePolicy.retire(ACCOUNT_CODE, null, List.of(), true));

    assertEquals(
        new AccountRegistryLifecycleRejection.AccountNotFound(ACCOUNT_CODE), missing.rejection());
  }

  @Test
  void retire_preservesTheAccountSnapshotAndIsIdempotent() {
    AccountRetirementDecision.Retired retired =
        assertInstanceOf(
            AccountRetirementDecision.Retired.class,
            AccountRegistryLifecyclePolicy.retire(
                ACCOUNT_CODE, currentAssetAccount(), List.of(), true));

    assertFalse(retired.account().active());
    assertEquals(DECLARED_AT, retired.account().declaredAt());
    AccountRetirementDecision.Unchanged replay =
        assertInstanceOf(
            AccountRetirementDecision.Unchanged.class,
            AccountRegistryLifecyclePolicy.retire(
                ACCOUNT_CODE, retired.account(), List.of(), false));
    assertFalse(replay.account().active());
  }

  @Test
  void lifecycleRejection_requiresAndDefensivelyCopiesDurableDependencies() {
    List<AccountRegistryDependency> source =
        new ArrayList<>(List.of(AccountRegistryDependency.POSTINGS));
    AccountRegistryLifecycleRejection.AccountHasDependents rejection =
        new AccountRegistryLifecycleRejection.AccountHasDependents(ACCOUNT_CODE, source);
    source.add(AccountRegistryDependency.TAX_REGISTRATIONS);

    assertEquals(List.of(AccountRegistryDependency.POSTINGS), rejection.dependencies());
    assertEquals(
        "Account dependents must contain at least one dependency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AccountRegistryLifecycleRejection.AccountHasDependents(
                        ACCOUNT_CODE, List.of()))
            .getMessage());
  }

  @Test
  void registeredAccountAmendment_retainsItsDurableAccountCode() {
    assertEquals(
        "Account amendments must retain the durable account code.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    RegisteredAccount.amend(
                        currentAssetAccount(),
                        new AccountDeclaration(
                            new AccountCode("1020"),
                            new AccountName("Other Cash"),
                            AccountType.ASSET,
                            financialPositionTaxonomy(
                                FinancialPositionLineClassification.CURRENT_ASSET))))
            .getMessage());
  }

  private static RegisteredAccount currentAssetAccount() {
    return new RegisteredAccount(
        ACCOUNT_CODE,
        new AccountName("Cash Reserve"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        true,
        DECLARED_AT);
  }

  private static AccountDeclaration nonCurrentAssetAmendment() {
    return new AccountDeclaration(
        ACCOUNT_CODE,
        new AccountName("Operating Reserve"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET));
  }

  private static AccountDeclaration currentAssetAmendment() {
    return new AccountDeclaration(
        ACCOUNT_CODE,
        new AccountName("Cash Reserve"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET));
  }
}
