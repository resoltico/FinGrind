package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Rebuilds financing caller and executor facts from durable arrangement lifecycle tables. */
final class SqliteFinancingOriginatingEntryMapper {
  private SqliteFinancingOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case FINANCING_BORROWING -> borrowing(database, postingId, postingRow, journalEntry);
      case FINANCING_PRINCIPAL_REPAYMENT ->
          application(
              database, postingId, postingRow, journalEntry, ApplicationKind.PRINCIPAL_REPAYMENT);
      case FINANCING_INTEREST_ACCRUAL ->
          application(
              database, postingId, postingRow, journalEntry, ApplicationKind.INTEREST_ACCRUAL);
      case FINANCING_INTEREST_PAYMENT ->
          application(
              database, postingId, postingRow, journalEntry, ApplicationKind.INTEREST_PAYMENT);
      default -> null;
    };
  }

  private static BookkeepingEntry borrowing(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry) {
    Arrangement arrangement =
        findArrangementByOrigin(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Financing borrowing posting has no durable arrangement aggregate."));
    return new FinancingBookkeepingEntryVariants.Borrowing(
        journalEntry.effectiveDate(),
        arrangement.id(),
        SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(postingRow),
        arrangement.principalLiabilityAccountCode(),
        arrangement.interestPayableAccountCode(),
        SqlitePostingOriginatingEntryMappingSupport.requiredEntryAmount(postingRow));
  }

  private static BookkeepingEntry application(
      SqliteNativeDatabase database,
      PostingId postingId,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      ApplicationKind expectedKind) {
    Application application =
        findApplication(database, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Financing lifecycle posting has no durable application aggregate."));
    if (application.kind() != expectedKind) {
      throw new IllegalStateException(
          "Financing lifecycle posting has the wrong application kind.");
    }
    MonetaryAmount amount = MonetaryAmount.of(application.amount());
    ResolvedFinancingApplication resolved =
        new ResolvedFinancingApplication(
            application.principalLiabilityAccountCode(), application.interestPayableAccountCode());
    return switch (application.kind()) {
      case PRINCIPAL_REPAYMENT ->
          new FinancingBookkeepingEntryVariants.PrincipalRepayment(
              journalEntry.effectiveDate(),
              application.arrangementId(),
              SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(
                  postingRow),
              amount,
              resolved);
      case INTEREST_ACCRUAL ->
          new FinancingBookkeepingEntryVariants.InterestAccrual(
              journalEntry.effectiveDate(),
              application.arrangementId(),
              SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryDebitAccountCode(
                  postingRow),
              amount,
              resolved);
      case INTEREST_PAYMENT ->
          new FinancingBookkeepingEntryVariants.InterestPayment(
              journalEntry.effectiveDate(),
              application.arrangementId(),
              SqlitePostingOriginatingEntryMappingSupport.requiredPrimaryCreditAccountCode(
                  postingRow),
              amount,
              resolved);
    };
  }

  private static Optional<Arrangement> findArrangementByOrigin(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select financing_arrangement_id, principal_liability_account_code, interest_payable_account_code
        from financing_arrangement
        where origin_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          return Optional.of(arrangement(statement));
        });
  }

  private static Optional<Application> findApplication(
      SqliteNativeDatabase database, PostingId postingId) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        """
        select application.application_kind, application.financing_arrangement_id,
               application.currency_code, application.amount_minor,
               arrangement.principal_liability_account_code,
               arrangement.interest_payable_account_code
        from financing_application application
        inner join financing_arrangement arrangement
            on arrangement.financing_arrangement_id = application.financing_arrangement_id
        where application.application_posting_id = ?
        """,
        statement -> {
          statement.bindText(1, postingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 2));
          return Optional.of(
              new Application(
                  ApplicationKind.valueOf(SqlitePostingMapper.requiredText(statement, 0)),
                  new FinancingArrangementId(SqlitePostingMapper.requiredText(statement, 1)),
                  Money.ofMinorUnits(currency, statement.columnLong(3)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 4)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 5))));
        });
  }

  private static Arrangement arrangement(SqliteNativeStatement statement) {
    return new Arrangement(
        new FinancingArrangementId(SqlitePostingMapper.requiredText(statement, 0)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 1)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 2)));
  }

  private record Arrangement(
      FinancingArrangementId id,
      AccountCode principalLiabilityAccountCode,
      AccountCode interestPayableAccountCode) {}

  private record Application(
      ApplicationKind kind,
      FinancingArrangementId arrangementId,
      Money amount,
      AccountCode principalLiabilityAccountCode,
      AccountCode interestPayableAccountCode) {}

  /** Closed set of durable financing application kinds owned by the financing context. */
  private enum ApplicationKind {
    PRINCIPAL_REPAYMENT,
    INTEREST_ACCRUAL,
    INTEREST_PAYMENT
  }
}
