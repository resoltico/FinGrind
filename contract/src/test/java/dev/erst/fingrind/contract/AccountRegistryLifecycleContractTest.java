package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct contract coverage for Account Registry lifecycle requests, outcomes, and refusals. */
class AccountRegistryLifecycleContractTest {
  @Test
  void amendAccountCommand_enforcesInventoryUnitDoctrine() {
    UnitOfMeasure unitOfMeasure = new UnitOfMeasure("pcs", 0);
    AmendAccountCommand cashAccount =
        new AmendAccountCommand(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            ContractFixtures.accountTaxonomy(AccountType.ASSET));
    AmendAccountCommand inventoryAccount =
        new AmendAccountCommand(
            new AccountCode("1400"),
            new AccountName("Inventory"),
            AccountType.ASSET,
            inventoryTaxonomy(),
            unitOfMeasure);

    assertEquals(null, cashAccount.unitOfMeasure());
    assertEquals(unitOfMeasure, inventoryAccount.unitOfMeasure());
    assertEquals(
        "Inventory account amendments require one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AmendAccountCommand(
                        new AccountCode("1400"),
                        new AccountName("Inventory"),
                        AccountType.ASSET,
                        inventoryTaxonomy(),
                        null))
            .getMessage());
    assertEquals(
        "Only inventory account amendments may carry one unitOfMeasure.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AmendAccountCommand(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        AccountType.ASSET,
                        ContractFixtures.accountTaxonomy(AccountType.ASSET),
                        unitOfMeasure))
            .getMessage());
    assertThrows(
        NullPointerException.class,
        () ->
            new AmendAccountCommand(
                nullOf(),
                new AccountName("Cash"),
                AccountType.ASSET,
                ContractFixtures.accountTaxonomy(AccountType.ASSET)));
  }

  @Test
  void lifecycleResults_rejectNullPayloads() {
    DeclaredAccount account = declaredAccount();
    BookAdministrationRejection rejection =
        new AccountRegistryLifecycleRejection.AccountNotFound(new AccountCode("1000"));

    assertEquals(account, new AmendAccountResult.Amended(account).account());
    assertEquals(account, new AmendAccountResult.Unchanged(account).account());
    assertEquals(rejection, new AmendAccountResult.Rejected(rejection).rejection());
    assertEquals(account, new RetireAccountResult.Retired(account).account());
    assertEquals(account, new RetireAccountResult.Unchanged(account).account());
    assertEquals(rejection, new RetireAccountResult.Rejected(rejection).rejection());

    assertThrows(NullPointerException.class, () -> new AmendAccountResult.Amended(nullOf()));
    assertThrows(NullPointerException.class, () -> new AmendAccountResult.Unchanged(nullOf()));
    assertThrows(NullPointerException.class, () -> new AmendAccountResult.Rejected(nullOf()));
    assertThrows(NullPointerException.class, () -> new RetireAccountResult.Retired(nullOf()));
    assertThrows(NullPointerException.class, () -> new RetireAccountResult.Unchanged(nullOf()));
    assertThrows(NullPointerException.class, () -> new RetireAccountResult.Rejected(nullOf()));
  }

  @Test
  void lifecycleRejections_copyDependentsAndPublishSpecificNarratives() {
    List<AccountRegistryDependency> source =
        new ArrayList<>(List.of(AccountRegistryDependency.POSTINGS));
    AccountRegistryLifecycleRejection.AccountHasDependents dependents =
        new AccountRegistryLifecycleRejection.AccountHasDependents(new AccountCode("1000"), source);
    source.add(AccountRegistryDependency.TAX_REGISTRATIONS);

    assertEquals(List.of(AccountRegistryDependency.POSTINGS), dependents.dependencies());
    assertThrows(
        UnsupportedOperationException.class,
        () -> dependents.dependencies().add(AccountRegistryDependency.CHILD_ACCOUNTS));
    assertEquals(
        "Account '1000' cannot change lifecycle while durable dependents remain: postings.",
        RejectionNarrative.message(dependents));
    assertEquals(
        "Account '1000' is not declared in this book.",
        RejectionNarrative.message(
            new AccountRegistryLifecycleRejection.AccountNotFound(new AccountCode("1000"))));
    assertEquals(
        "Account '1000' cannot retire because its current balance is not zero.",
        RejectionNarrative.message(
            new AccountRegistryLifecycleRejection.AccountBalanceNotZero(new AccountCode("1000"))));
    assertEquals(
        "Account dependents must contain at least one dependency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new AccountRegistryLifecycleRejection.AccountHasDependents(
                        new AccountCode("1000"), List.of()))
            .getMessage());
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountRegistryLifecycleRejection.AccountHasDependents(
                new AccountCode("1000"), nullOf()));
  }

  @Test
  void retireAccountCommand_andTaxLedgerStep_validateTheirPayloads() {
    RetireAccountCommand retireAccountCommand = new RetireAccountCommand(new AccountCode("1000"));
    DeclareTaxRegistrationCommand taxRegistrationCommand = taxRegistrationCommand();
    LedgerStep.DeclareTaxRegistration step =
        new LedgerStep.DeclareTaxRegistration(
            new LedgerStepId("declare-vat-registration"), taxRegistrationCommand);

    assertEquals(new AccountCode("1000"), retireAccountCommand.accountCode());
    assertEquals(
        dev.erst.fingrind.contract.protocol.LedgerStepKind.DECLARE_TAX_REGISTRATION, step.kind());
    assertThrows(NullPointerException.class, () -> new RetireAccountCommand(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new LedgerStep.DeclareTaxRegistration(nullOf(), taxRegistrationCommand));
    assertThrows(
        NullPointerException.class,
        () ->
            new LedgerStep.DeclareTaxRegistration(
                new LedgerStepId("declare-vat-registration"), nullOf()));
  }

  private static DeclaredAccount declaredAccount() {
    return ContractFixtures.declaredAccount(
        "1000", "Cash", AccountType.ASSET, true, Instant.parse("2026-06-13T10:15:30Z"));
  }

  private static AccountTaxonomy inventoryTaxonomy() {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.INVENTORY),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }

  private static DeclareTaxRegistrationCommand taxRegistrationCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber("LV40001234567"),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }
}
