package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared validation and extraction helpers for the tax context. */
final class TaxValidationSupport {
  private TaxValidationSupport() {}

  static List<TaxDefinitionViolation> declarationViolations(
      DeclareTaxRegistrationCommand command, AccountLookupStore accountLookupStore) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(accountLookupStore, "accountLookupStore");
    List<TaxDefinitionViolation> violations = new ArrayList<>();
    validateDuplicateCodes(violations, command);
    validatePayableAccount(violations, accountLookupStore, command.payableAccountCode());
    validateRecoverableAccount(violations, accountLookupStore, command.recoverableAccountCode());
    return List.copyOf(violations);
  }

  static boolean matchesObligationPeriod(
      TaxObligationFrequency frequency, LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    Objects.requireNonNull(frequency, "frequency");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    return switch (frequency) {
      case MONTHLY ->
          effectiveDateFrom.getDayOfMonth() == 1
              && effectiveDateFrom.getYear() == effectiveDateTo.getYear()
              && effectiveDateFrom.getMonth() == effectiveDateTo.getMonth()
              && effectiveDateTo.equals(
                  effectiveDateFrom.withDayOfMonth(effectiveDateFrom.lengthOfMonth()));
      case QUARTERLY -> {
        int month = effectiveDateFrom.getMonthValue();
        boolean quarterStartMonth = month == 1 || month == 4 || month == 7 || month == 10;
        LocalDate expectedQuarterEnd = effectiveDateFrom.plusMonths(3).minusDays(1);
        yield quarterStartMonth
            && effectiveDateFrom.getDayOfMonth() == 1
            && expectedQuarterEnd.equals(effectiveDateTo);
      }
      case ANNUAL ->
          effectiveDateFrom.getDayOfMonth() == 1
              && effectiveDateFrom.getMonthValue() == 1
              && effectiveDateTo.equals(LocalDate.of(effectiveDateFrom.getYear(), 12, 31));
    };
  }

  static @Nullable AppliedTax appliedTax(@Nullable BookkeepingEntry entry) {
    if (entry == null) {
      return null;
    }
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale -> sale.appliedTax();
      case BookkeepingEntry.SaleOnCredit sale -> sale.appliedTax();
      case BookkeepingEntry.ExpenseSettled expense -> expense.appliedTax();
      case BookkeepingEntry.ExpenseOnCredit expense -> expense.appliedTax();
      default -> null;
    };
  }

  private static void validateDuplicateCodes(
      List<TaxDefinitionViolation> violations, DeclareTaxRegistrationCommand command) {
    Set<String> seen = new HashSet<>();
    for (var taxCodeDefinition : command.taxCodes()) {
      if (seen.add(taxCodeDefinition.taxCode().value())) {
        continue;
      }
      violations.add(
          new TaxDefinitionViolation(
              "duplicate-tax-code",
              "taxCodes[].taxCode",
              "taxRegistrationId '%s' repeats taxCode '%s'."
                  .formatted(
                      command.taxRegistrationId().value(), taxCodeDefinition.taxCode().value())));
    }
  }

  private static void validatePayableAccount(
      List<TaxDefinitionViolation> violations,
      AccountLookupStore accountLookupStore,
      AccountCode payableAccountCode) {
    RegisteredAccount account = accountLookupStore.findAccount(payableAccountCode).orElse(null);
    if (!validateKnownActivePostableAccount(
        violations, "payableAccountCode", payableAccountCode, account)) {
      return;
    }
    RegisteredAccount validatedAccount = Objects.requireNonNull(account, "account");
    if (validatedAccount.accountType() != AccountType.LIABILITY) {
      violations.add(
          new TaxDefinitionViolation(
              "account-type-mismatch",
              "payableAccountCode",
              "payableAccountCode '%s' must be account type 'LIABILITY', but the declared account type is '%s'."
                  .formatted(
                      payableAccountCode.value(), validatedAccount.accountType().wireValue())));
    }
    if (validatedAccount.accountTaxonomy().financialPositionLineClassification().orElse(null)
        != FinancialPositionLineClassification.CURRENT_LIABILITY) {
      violations.add(
          new TaxDefinitionViolation(
              "financial-position-classification-mismatch",
              "payableAccountCode",
              "payableAccountCode '%s' must use financialPositionLineClassification 'CURRENT_LIABILITY'."
                  .formatted(payableAccountCode.value())));
    }
  }

  private static void validateRecoverableAccount(
      List<TaxDefinitionViolation> violations,
      AccountLookupStore accountLookupStore,
      AccountCode recoverableAccountCode) {
    RegisteredAccount account = accountLookupStore.findAccount(recoverableAccountCode).orElse(null);
    if (!validateKnownActivePostableAccount(
        violations, "recoverableAccountCode", recoverableAccountCode, account)) {
      return;
    }
    RegisteredAccount validatedAccount = Objects.requireNonNull(account, "account");
    if (validatedAccount.accountType() != AccountType.ASSET) {
      violations.add(
          new TaxDefinitionViolation(
              "account-type-mismatch",
              "recoverableAccountCode",
              "recoverableAccountCode '%s' must be account type 'ASSET', but the declared account type is '%s'."
                  .formatted(
                      recoverableAccountCode.value(), validatedAccount.accountType().wireValue())));
    }
    if (validatedAccount.accountTaxonomy().financialPositionLineClassification().orElse(null)
        != FinancialPositionLineClassification.CURRENT_ASSET) {
      violations.add(
          new TaxDefinitionViolation(
              "financial-position-classification-mismatch",
              "recoverableAccountCode",
              "recoverableAccountCode '%s' must use financialPositionLineClassification 'CURRENT_ASSET'."
                  .formatted(recoverableAccountCode.value())));
    }
    if (validatedAccount.accountTaxonomy().cashFlowAssetClassification().orElse(null)
        != CashFlowAssetClassification.NON_CASH) {
      violations.add(
          new TaxDefinitionViolation(
              "cash-flow-asset-classification-mismatch",
              "recoverableAccountCode",
              "recoverableAccountCode '%s' must use cashFlowAssetClassification 'NON_CASH'."
                  .formatted(recoverableAccountCode.value())));
    }
  }

  private static boolean validateKnownActivePostableAccount(
      List<TaxDefinitionViolation> violations,
      String field,
      AccountCode accountCode,
      @Nullable RegisteredAccount account) {
    if (account == null) {
      violations.add(
          new TaxDefinitionViolation(
              "unknown-account",
              field,
              "%s '%s' is not declared in this book.".formatted(field, accountCode.value())));
      return false;
    }
    if (!account.active()) {
      violations.add(
          new TaxDefinitionViolation(
              "inactive-account",
              field,
              "%s '%s' is declared but inactive.".formatted(field, accountCode.value())));
      return false;
    }
    if (account.accountTaxonomy().nodeKind() != dev.erst.fingrind.core.AccountNodeKind.POSTABLE) {
      violations.add(
          new TaxDefinitionViolation(
              "non-postable-account",
              field,
              "%s '%s' must be postable.".formatted(field, accountCode.value())));
      return false;
    }
    return true;
  }
}
