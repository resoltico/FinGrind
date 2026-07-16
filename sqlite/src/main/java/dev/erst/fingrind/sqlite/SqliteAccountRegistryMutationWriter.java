package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;

/** Persists Account Registry definitions and lifecycle state for one SQLite book. */
final class SqliteAccountRegistryMutationWriter {
  private SqliteAccountRegistryMutationWriter() {}

  static void upsertAccount(SqliteNativeDatabase activeDatabase, RegisteredAccount account) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      int nextParameter = bindAccountDefinition(statement, account, 2);
      statement.bindInt(nextParameter, Boolean.compare(account.active(), false));
      statement.bindText(
          nextParameter + 1, CanonicalTemporalText.formatUtcInstant(account.declaredAt()));
      statement.step();
    }
  }

  static void amendAccount(SqliteNativeDatabase activeDatabase, RegisteredAccount account) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAccountLifecycleSql.UPDATE_ACCOUNT_DEFINITION)) {
      int nextParameter = bindAccountDefinition(statement, account, 1);
      statement.bindText(nextParameter, account.accountCode().value());
      statement.step();
    }
  }

  static void retireAccount(SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAccountLifecycleSql.RETIRE_ACCOUNT)) {
      statement.bindText(1, accountCode.value());
      statement.step();
    }
  }

  /** Binds the mutable and taxonomic definition of one account and returns the next parameter. */
  private static int bindAccountDefinition(
      SqliteNativeStatement statement, RegisteredAccount account, int firstParameter) {
    statement.bindText(firstParameter, account.accountName().value());
    statement.bindText(firstParameter + 1, account.accountType().wireValue());
    statement.bindText(firstParameter + 2, account.accountTaxonomy().nodeKind().wireValue());
    statement.bindText(
        firstParameter + 3,
        account.accountTaxonomy().parentAccountCode().map(AccountCode::value).orElse(null));
    statement.bindText(
        firstParameter + 4,
        account
            .accountTaxonomy()
            .financialPositionLineClassification()
            .map(value -> value.wireValue())
            .orElse(null));
    statement.bindText(
        firstParameter + 5,
        account
            .accountTaxonomy()
            .cashFlowAssetClassification()
            .map(value -> value.wireValue())
            .orElse(null));
    statement.bindText(
        firstParameter + 6,
        account
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .map(value -> value.wireValue())
            .orElse(null));
    statement.bindText(
        firstParameter + 7,
        account.unitOfMeasure() == null ? null : account.unitOfMeasure().token());
    if (account.unitOfMeasure() == null) {
      statement.bindNull(firstParameter + 8);
    } else {
      statement.bindInt(firstParameter + 8, account.unitOfMeasure().quantityScale());
    }
    return firstParameter + 9;
  }
}
