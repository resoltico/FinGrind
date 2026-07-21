package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct coverage for the tax-registration declaration application service. */
class TaxAdministrationServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T10:15:30Z");
  private static final AttestationOperationAuthorizer TEST_AUTHORIZER =
      ignored -> {
        throw new AssertionError("Tax service validation must not invoke an attestation signer.");
      };

  @Test
  void declareTaxRegistration_rejectsWhenBookIsNotInitialized() {
    RecordingTaxAdministrationStore store =
        new RecordingTaxAdministrationStore(
            new DeclareTaxRegistrationResult.Rejected(
                new TaxDeclarationRejection.BookNotInitialized()));
    TaxAdministrationService service =
        new TaxAdministrationService(
            () -> new BookLifecycleInspection.Missing(1),
            accountCode -> Optional.empty(),
            store,
            FIXED_CLOCK);

    DeclareTaxRegistrationResult.Rejected rejected =
        assertInstanceOf(
            DeclareTaxRegistrationResult.Rejected.class,
            service.declareTaxRegistration(validCommand(), TEST_AUTHORIZER));

    assertInstanceOf(TaxDeclarationRejection.BookNotInitialized.class, rejected.rejection());
    assertNull(store.lastCommand);
    assertNull(store.lastDeclaredAt);
    assertNull(store.lastAttestationAuthorizer);
  }

  @Test
  void declareTaxRegistration_rejectsInvalidOwnedDefinitionBeforeMutatingStore() {
    RecordingTaxAdministrationStore store =
        new RecordingTaxAdministrationStore(
            new DeclareTaxRegistrationResult.Rejected(
                new TaxDeclarationRejection.BookNotInitialized()));
    TaxAdministrationService service =
        new TaxAdministrationService(
            () -> initializedLifecycleInspection(1001, 25, 25, FIXED_CLOCK.instant()),
            lookupStore(
                Map.of(
                    new AccountCode("2100"), invalidPayableAccount(),
                    new AccountCode("1300"), invalidRecoverableAccount())),
            store,
            FIXED_CLOCK);

    DeclareTaxRegistrationResult.Rejected rejected =
        assertInstanceOf(
            DeclareTaxRegistrationResult.Rejected.class,
            service.declareTaxRegistration(commandWithDuplicateCode(), TEST_AUTHORIZER));
    TaxDeclarationRejection.DefinitionViolations violations =
        assertInstanceOf(TaxDeclarationRejection.DefinitionViolations.class, rejected.rejection());

    assertTrue(
        violations.violations().stream()
            .map(TaxDefinitionViolation::code)
            .toList()
            .containsAll(
                List.of(
                    "duplicate-tax-code",
                    "account-type-mismatch",
                    "financial-position-classification-mismatch",
                    "cash-flow-asset-classification-mismatch")));
    assertNull(store.lastCommand);
    assertNull(store.lastDeclaredAt);
    assertNull(store.lastAttestationAuthorizer);
  }

  @Test
  void declareTaxRegistration_delegatesValidDefinitionToStore() {
    DeclareTaxRegistrationCommand command = validCommand();
    DeclaredTaxRegistration registration = declaredRegistration(command, DECLARED_AT);
    RecordingTaxAdministrationStore store =
        new RecordingTaxAdministrationStore(
            new DeclareTaxRegistrationResult.Declared(registration));
    TaxAdministrationService service =
        new TaxAdministrationService(
            () -> initializedLifecycleInspection(1001, 25, 25, FIXED_CLOCK.instant()),
            lookupStore(
                Map.of(
                    command.payableAccountCode(), validPayableAccount(),
                    command.recoverableAccountCode(), validRecoverableAccount())),
            store,
            FIXED_CLOCK);

    DeclareTaxRegistrationResult.Declared declared =
        assertInstanceOf(
            DeclareTaxRegistrationResult.Declared.class,
            service.declareTaxRegistration(command, TEST_AUTHORIZER));

    assertEquals(registration, declared.registration());
    assertEquals(command, store.lastCommand);
    assertEquals(FIXED_CLOCK.instant(), store.lastDeclaredAt);
    assertNotNull(store.lastDeclaredAt);
    assertEquals(TEST_AUTHORIZER, store.lastAttestationAuthorizer);
  }

  private static AccountLookupStore lookupStore(Map<AccountCode, RegisteredAccount> accounts) {
    return accountCode -> Optional.ofNullable(accounts.get(accountCode));
  }

  private static DeclareTaxRegistrationCommand validCommand() {
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
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE)));
  }

  private static DeclareTaxRegistrationCommand commandWithDuplicateCode() {
    TaxCodeDefinition duplicateCode =
        new TaxCodeDefinition(
            new TaxCode("vat-standard-sale"),
            new TaxCodeName("VAT Standard Sale"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE);
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(duplicateCode, duplicateCode));
  }

  private static RegisteredAccount validPayableAccount() {
    return registeredAccount(
        new AccountCode("2100"),
        new AccountName("VAT Payable"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount validRecoverableAccount() {
    return registeredAccount(
        new AccountCode("1300"),
        new AccountName("VAT Recoverable"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount invalidPayableAccount() {
    return registeredAccount(
        new AccountCode("2100"),
        new AccountName("Not A Liability"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET),
        true,
        DECLARED_AT);
  }

  private static RegisteredAccount invalidRecoverableAccount() {
    return registeredAccount(
        new AccountCode("1300"),
        new AccountName("Cash Account"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET),
        true,
        DECLARED_AT);
  }

  private static DeclaredTaxRegistration declaredRegistration(
      DeclareTaxRegistrationCommand command, Instant declaredAt) {
    return new DeclaredTaxRegistration(
        command.taxRegistrationId(),
        command.taxRegistrationName(),
        command.jurisdiction(),
        command.registrationNumber(),
        command.payableAccountCode(),
        command.recoverableAccountCode(),
        command.obligationFrequency(),
        command.dueDaysAfterPeriodEnd(),
        command.taxCodes(),
        declaredAt);
  }

  /** Recording store double that captures the validated command forwarded by the service. */
  private static final class RecordingTaxAdministrationStore implements TaxAdministrationStore {
    private final DeclareTaxRegistrationResult result;
    private @Nullable DeclareTaxRegistrationCommand lastCommand;
    private @Nullable Instant lastDeclaredAt;
    private @Nullable AttestationOperationAuthorizer lastAttestationAuthorizer;

    private RecordingTaxAdministrationStore(DeclareTaxRegistrationResult result) {
      this.result = result;
    }

    @Override
    public DeclareTaxRegistrationResult declareTaxRegistration(
        DeclareTaxRegistrationCommand command,
        Instant declaredAt,
        AttestationOperationAuthorizer attestationAuthorizer) {
      this.lastCommand = command;
      this.lastDeclaredAt = declaredAt;
      this.lastAttestationAuthorizer = attestationAuthorizer;
      return result;
    }
  }
}
