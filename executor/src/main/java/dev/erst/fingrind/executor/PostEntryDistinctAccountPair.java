package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Two role-bearing fields whose account codes must not be the same. */
record PostEntryDistinctAccountPair(
    AccountCode firstAccountCode,
    String firstField,
    AccountCode secondAccountCode,
    String secondField) {
  PostEntryDistinctAccountPair {
    Objects.requireNonNull(firstAccountCode, "firstAccountCode");
    Objects.requireNonNull(firstField, "firstField");
    Objects.requireNonNull(secondAccountCode, "secondAccountCode");
    Objects.requireNonNull(secondField, "secondField");
  }
}
