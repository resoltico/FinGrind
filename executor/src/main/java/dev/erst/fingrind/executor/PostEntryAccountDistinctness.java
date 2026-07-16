package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;

/** Factory for the typed-entry role-account distinctness invariant. */
final class PostEntryAccountDistinctness {
  private PostEntryAccountDistinctness() {}

  static PostEntryDistinctAccountPair distinct(
      AccountCode firstAccountCode,
      String firstField,
      AccountCode secondAccountCode,
      String secondField) {
    return new PostEntryDistinctAccountPair(
        firstAccountCode, firstField, secondAccountCode, secondField);
  }

  static PostEntryDistinctAccountPair distinct(
      PostEntryAccountExpectation firstExpectation, PostEntryAccountExpectation secondExpectation) {
    return distinct(
        firstExpectation.accountCode(),
        firstExpectation.field(),
        secondExpectation.accountCode(),
        secondExpectation.field());
  }
}
