package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aggregate owner for parent-child chart invariants over one declared account registry. */
public final class ChartOfAccounts {
  private final Map<AccountCode, RegisteredAccount> accountsByCode;

  private ChartOfAccounts(Map<AccountCode, RegisteredAccount> accountsByCode) {
    this.accountsByCode = Map.copyOf(accountsByCode);
  }

  /** Returns one aggregate snapshot over the supplied declared accounts. */
  public static ChartOfAccounts of(List<RegisteredAccount> accounts) {
    Objects.requireNonNull(accounts, "accounts");
    Map<AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                Collectors.toMap(
                    RegisteredAccount::accountCode,
                    Function.identity(),
                    (priorAccount, currentAccount) -> {
                      throw new IllegalArgumentException(
                          "Duplicate declared account code inside chart aggregate: "
                              + currentAccount.accountCode());
                    },
                    LinkedHashMap::new));
    return new ChartOfAccounts(accountsByCode);
  }

  /** Validates one declaration against the live chart hierarchy. */
  public Optional<BookkeepingAdministrationRejection> validate(AccountDeclaration declaration) {
    Objects.requireNonNull(declaration, "declaration");
    Optional<AccountCode> parentAccountCode = declaration.accountTaxonomy().parentAccountCode();
    if (parentAccountCode.isEmpty()) {
      return Optional.empty();
    }
    AccountCode childAccountCode = declaration.accountCode();
    AccountCode requiredParentAccountCode = parentAccountCode.orElseThrow();
    if (requiredParentAccountCode.equals(childAccountCode)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.AccountHierarchyCycle(
              childAccountCode, requiredParentAccountCode));
    }
    RegisteredAccount parentAccount = accountsByCode.get(requiredParentAccountCode);
    if (parentAccount == null) {
      return Optional.of(
          new BookkeepingAdministrationRejection.ParentAccountMissing(
              childAccountCode, requiredParentAccountCode));
    }
    if (!parentAccount.active()) {
      return Optional.of(
          new BookkeepingAdministrationRejection.ParentAccountInactive(
              childAccountCode, requiredParentAccountCode));
    }
    if (parentAccount.accountType() != declaration.accountType()) {
      return Optional.of(
          new BookkeepingAdministrationRejection.ParentAccountTypeConflict(
              childAccountCode,
              declaration.accountType(),
              requiredParentAccountCode,
              parentAccount.accountType()));
    }
    if (wouldCreateCycle(childAccountCode, requiredParentAccountCode)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.AccountHierarchyCycle(
              childAccountCode, requiredParentAccountCode));
    }
    return Optional.empty();
  }

  private boolean wouldCreateCycle(AccountCode childAccountCode, AccountCode parentAccountCode) {
    AccountCode currentAccountCode = parentAccountCode;
    while (true) {
      if (currentAccountCode.equals(childAccountCode)) {
        return true;
      }
      RegisteredAccount currentAccount = accountsByCode.get(currentAccountCode);
      if (currentAccount == null) {
        return false;
      }
      Optional<AccountCode> nextParent = currentAccount.accountTaxonomy().parentAccountCode();
      if (nextParent.isEmpty()) {
        return false;
      }
      currentAccountCode = nextParent.orElseThrow();
    }
  }
}
