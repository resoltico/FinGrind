package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookInspectionJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportValueJsonModels;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared exact-value conversions used by the family-owned machine report mappers. */
final class CliReportPayloadMappingSupport {
  private CliReportPayloadMappingSupport() {}

  static CliReportJsonModels.PeriodResolvedQuery periodQuery(
      LocalDate periodStart,
      LocalDate periodEnd,
      PostingCoverage postingCoverage,
      CliReportJsonModels.@Nullable ComparativeRangePayload comparative) {
    return new CliReportJsonModels.PeriodResolvedQuery(
        periodStart.toString(), periodEnd.toString(), postingCoverage.name(), comparative);
  }

  static CliReportJsonModels.@Nullable ComparativeRangePayload comparativeRange(
      EffectiveDateRange range) {
    if (range.effectiveDateFrom().isEmpty() && range.effectiveDateTo().isEmpty()) {
      return null;
    }
    return new CliReportJsonModels.ComparativeRangePayload(
        date(range.effectiveDateFrom().orElse(null)), date(range.effectiveDateTo().orElse(null)));
  }

  static CliBookInspectionJsonModels.BookIdentityPayload bookIdentity(BookIdentity bookIdentity) {
    return CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity);
  }

  static CliReportValueJsonModels.AccountPayload account(DeclaredAccount account) {
    return new CliReportValueJsonModels.AccountPayload(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType().name(),
        account.normalBalance().name(),
        account.active());
  }

  static List<CliReportValueJsonModels.BalancePayload> balances(List<CurrencyBalance> balances) {
    return balances.stream().map(CliReportPayloadMappingSupport::balance).toList();
  }

  static CliReportValueJsonModels.BalancePayload balance(CurrencyBalance balance) {
    return new CliReportValueJsonModels.BalancePayload(
        balance.debitTotal().currencyUnit().code(),
        money(balance.debitTotal()),
        money(balance.creditTotal()),
        money(balance.netAmount()),
        balance.balanceSide().name());
  }

  static CliReportValueJsonModels.MoneyPayload money(Money money) {
    return new CliReportValueJsonModels.MoneyPayload(
        money.currencyUnit().code(), Long.toString(money.minorUnits()));
  }

  static CliReportValueJsonModels.MoneyPayload money(MonetaryAmount money) {
    return new CliReportValueJsonModels.MoneyPayload(money.currencyCode(), money.minorUnits());
  }

  static String family(OperationId operationId) {
    return ProtocolCatalog.operationName(Objects.requireNonNull(operationId, "operationId"));
  }

  static String balanceState(boolean balanced) {
    return balanced ? "BALANCED" : "UNBALANCED";
  }

  static String instant(Instant instant) {
    return Objects.requireNonNull(instant, "instant").toString();
  }

  static @Nullable String date(@Nullable LocalDate date) {
    return date == null ? null : date.toString();
  }
}
