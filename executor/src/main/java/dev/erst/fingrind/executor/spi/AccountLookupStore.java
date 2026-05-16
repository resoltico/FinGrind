package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Looks up declared accounts by semantic identity. */
@FunctionalInterface
public interface AccountLookupStore {
  /** Looks up one declared account in the selected book. */
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Looks up the supplied declared accounts in one batch when the store can do so efficiently. */
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return accountCodes.stream()
        .map(accountCode -> Map.entry(accountCode, findAccount(accountCode)))
        .flatMap(
            entry -> entry.getValue().stream().map(account -> Map.entry(entry.getKey(), account)))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
