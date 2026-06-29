package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
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
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maps SQLite tax rows into public FinGrind tax contract records. */
final class SqliteTaxMapper {
  private SqliteTaxMapper() {}

  static DeclaredTaxRegistration declaredTaxRegistration(
      TaxRegistrationCoreRow coreRow, List<TaxCodeDefinition> taxCodes) {
    Objects.requireNonNull(coreRow, "coreRow");
    return new DeclaredTaxRegistration(
        new TaxRegistrationId(coreRow.taxRegistrationId()),
        new TaxRegistrationName(coreRow.taxRegistrationName()),
        new TaxJurisdiction(coreRow.jurisdiction()),
        coreRow.registrationNumber().map(TaxRegistrationNumber::new).orElse(null),
        new AccountCode(coreRow.payableAccountCode()),
        new AccountCode(coreRow.recoverableAccountCode()),
        TaxObligationFrequency.fromWireValue(coreRow.obligationFrequency()),
        coreRow.dueDaysAfterPeriodEnd(),
        taxCodes,
        CanonicalTemporalText.parseUtcInstant(coreRow.declaredAt(), "taxRegistration.declaredAt"));
  }

  static TaxCodeDefinition taxCodeDefinition(SqliteNativeStatement statement) {
    return new TaxCodeDefinition(
        new TaxCode(SqlitePostingMapper.requiredText(statement, 0)),
        new TaxCodeName(SqlitePostingMapper.requiredText(statement, 1)),
        new TaxRate(statement.columnInt(2)),
        TaxInclusionMode.fromWireValue(SqlitePostingMapper.requiredText(statement, 3)),
        TaxApplicationKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 4)));
  }

  static AppliedTax appliedTax(SqliteNativeStatement statement) {
    CurrencyUnit currencyUnit = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 6));
    return new AppliedTax(
        new TaxRegistrationId(SqlitePostingMapper.requiredText(statement, 0)),
        new TaxCode(SqlitePostingMapper.requiredText(statement, 1)),
        new TaxCodeName(SqlitePostingMapper.requiredText(statement, 2)),
        new TaxRate(statement.columnInt(3)),
        TaxInclusionMode.fromWireValue(SqlitePostingMapper.requiredText(statement, 4)),
        TaxApplicationKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 5)),
        MonetaryAmount.of(Money.ofMinorUnits(currencyUnit, statement.columnLong(7))),
        MonetaryAmount.of(Money.ofMinorUnits(currencyUnit, statement.columnLong(8))),
        MonetaryAmount.of(Money.ofMinorUnits(currencyUnit, statement.columnLong(9))),
        optionalText(statement, 10).map(AccountCode::new).orElse(null));
  }

  static Optional<String> optionalText(SqliteNativeStatement row, int columnIndex) {
    String value = row.columnText(columnIndex);
    return value == null ? Optional.empty() : Optional.of(value);
  }

  /** Core scalar row for one declared tax registration before its tax-code children load. */
  record TaxRegistrationCoreRow(
      String taxRegistrationId,
      String taxRegistrationName,
      String jurisdiction,
      Optional<String> registrationNumber,
      String payableAccountCode,
      String recoverableAccountCode,
      String obligationFrequency,
      int dueDaysAfterPeriodEnd,
      String declaredAt) {
    TaxRegistrationCoreRow {
      Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
      Objects.requireNonNull(taxRegistrationName, "taxRegistrationName");
      Objects.requireNonNull(jurisdiction, "jurisdiction");
      Objects.requireNonNull(registrationNumber, "registrationNumber");
      Objects.requireNonNull(payableAccountCode, "payableAccountCode");
      Objects.requireNonNull(recoverableAccountCode, "recoverableAccountCode");
      Objects.requireNonNull(obligationFrequency, "obligationFrequency");
      Objects.requireNonNull(declaredAt, "declaredAt");
    }
  }
}
