package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Current FinGrind bookkeeping policy pack for the built-in internal-management kernel. */
@NullMarked
public final class InternalManagementKernelAccountingRules implements KernelAccountingRules {
  private static final InternalManagementKernelAccountingRules CURRENT =
      new InternalManagementKernelAccountingRules();
  private static final ChartPolicy CHART_POLICY = () -> true;
  private static final ClosePostingPolicy CLOSE_POLICY =
      new ClosePostingPolicy() {
        @Override
        public boolean closesAccountType(AccountType accountType) {
          return switch (accountType) {
            case REVENUE, EXPENSE -> true;
            case ASSET, LIABILITY, EQUITY -> false;
          };
        }

        @Override
        public FinancialPositionLineClassification resultHoldingLineClassification(
            BookIdentity bookIdentity) {
          Objects.requireNonNull(bookIdentity, "bookIdentity");
          return FinancialPositionLineClassification.RESULT_HOLDING;
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
          return new DerivedEquityLine("current-period-result", "Current Period Result");
        }
      };

  private final StatementComparativePolicy statementComparativePolicy =
      new FiscalYearAnchoredStatementComparativePolicy();

  private InternalManagementKernelAccountingRules() {}

  /** Returns the built-in bookkeeping policy pack. */
  public static InternalManagementKernelAccountingRules current() {
    return CURRENT;
  }

  @Override
  public StatementComparativePolicy statementComparativePolicy() {
    return statementComparativePolicy;
  }

  @Override
  public ChartPolicy chartPolicy() {
    return CHART_POLICY;
  }

  @Override
  public ClosePostingPolicy closePostingPolicy() {
    return CLOSE_POLICY;
  }

  @Override
  public StatementPresentationPolicy statementPresentationPolicy() {
    return STATEMENT_PRESENTATION_POLICY;
  }
}
