package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Current FinGrind bookkeeping policy pack for the built-in country-agnostic kernel. */
@NullMarked
public final class CoreBookkeepingPolicyPack implements BookkeepingPolicyPack {
  private static final CoreBookkeepingPolicyPack CURRENT = new CoreBookkeepingPolicyPack();
  private static final AccountingBasisPolicy ACCOUNTING_BASIS_POLICY =
      () -> List.of(AccountingBasis.CASH, AccountingBasis.ACCRUAL);
  private static final ChartPolicy CHART_POLICY = () -> true;
  private static final ClosePolicy CLOSE_POLICY =
      new ClosePolicy() {
        @Override
        public boolean closesAccountType(AccountType accountType) {
          return switch (accountType) {
            case REVENUE, EXPENSE -> true;
            case ASSET, LIABILITY, EQUITY -> false;
          };
        }

        @Override
        public FinancialPositionLineClassification closingEquityLineClassification(
            BookIdentity bookIdentity) {
          Objects.requireNonNull(bookIdentity, "bookIdentity");
          return switch (bookIdentity.entityForm()) {
            case FREELANCER, SOLE_PROPRIETORSHIP ->
                FinancialPositionLineClassification.OWNER_CAPITAL;
            case COMPANY, BRANCH -> FinancialPositionLineClassification.RETAINED_EARNINGS;
            case PARTNERSHIP -> FinancialPositionLineClassification.PARTNER_CURRENT;
            case NONPROFIT -> FinancialPositionLineClassification.ACCUMULATED_SURPLUS;
            case OTHER -> FinancialPositionLineClassification.OTHER_EQUITY;
          };
        }
      };
  private static final StatementPresentationPolicy STATEMENT_PRESENTATION_POLICY =
      new StatementPresentationPolicy() {
        @Override
        public boolean supportsRichClassification() {
          return true;
        }

        @Override
        public DerivedEquityLine currentPeriodResultLine(BookIdentity bookIdentity) {
          Objects.requireNonNull(bookIdentity, "bookIdentity");
          return new DerivedEquityLine(
              "current-period-result",
              "Current Period Result",
              FinancialPositionLineClassification.CURRENT_PERIOD_RESULT);
        }
      };

  private final StatementComparativePolicy statementComparativePolicy =
      new FiscalYearAnchoredStatementComparativePolicy();

  private CoreBookkeepingPolicyPack() {}

  /** Returns the built-in bookkeeping policy pack. */
  public static CoreBookkeepingPolicyPack current() {
    return CURRENT;
  }

  @Override
  public StatementComparativePolicy statementComparativePolicy() {
    return statementComparativePolicy;
  }

  @Override
  public AccountingBasisPolicy accountingBasisPolicy() {
    return ACCOUNTING_BASIS_POLICY;
  }

  @Override
  public ChartPolicy chartPolicy() {
    return CHART_POLICY;
  }

  @Override
  public ClosePolicy closePolicy() {
    return CLOSE_POLICY;
  }

  @Override
  public StatementPresentationPolicy statementPresentationPolicy() {
    return STATEMENT_PRESENTATION_POLICY;
  }
}
