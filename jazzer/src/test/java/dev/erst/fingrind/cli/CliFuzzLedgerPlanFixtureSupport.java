package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import org.jspecify.annotations.Nullable;

public final class CliFuzzLedgerPlanFixtureSupport {
  private CliFuzzLedgerPlanFixtureSupport() {}

  static byte[] basicValidLedgerPlanBytes() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "ensure-book",
              "ensureBook": %s
            },
            %s
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading())
        .getBytes(UTF_8);
  }

  static byte[] validJpyLedgerPlanBytes() {
    return """
        {
          "planId": "plan-jpy-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "ensure-book",
              "ensureBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-jpy",
              "kind": "record-sale-settled",
              "posting": %s
            },
            {
              "stepId": "assert-jpy",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1100",
                "netAmount": {
                  "currencyCode": "JPY",
                  "minorUnits": "100"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson(
                    "declare-cash-jpy", "1100", "Cash JPY", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-sales-jpy", "2100", "Sales JPY", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            CliFuzzHarnessTestSupport.cashRevenueRequestJson(
                    new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-06-03",
                        "1100",
                        "2100",
                        "JPY",
                        "100",
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-jpy-plan-1",
                            "cash-receipt",
                            "2026-06-03",
                            "agent-jpy-plan-1",
                            "AGENT",
                            "command-jpy-plan-1",
                            "idem-jpy-plan-1",
                            "cause-jpy-plan-1",
                            null)))
                .indent(16)
                .stripLeading())
        .getBytes(UTF_8);
  }

  static byte[] validBhdLedgerPlanBytes() {
    return """
        {
          "planId": "plan-bhd-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "ensure-book",
              "ensureBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-bhd",
              "kind": "record-sale-settled",
              "posting": %s
            },
            {
              "stepId": "assert-bhd",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1200",
                "netAmount": {
                  "currencyCode": "BHD",
                  "minorUnits": "1250"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson(
                    "declare-cash-bhd", "1200", "Cash BHD", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-sales-bhd", "2200", "Sales BHD", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            CliFuzzHarnessTestSupport.cashRevenueRequestJson(
                    new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-06-04",
                        "1200",
                        "2200",
                        "BHD",
                        "1250",
                        new CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-bhd-plan-1",
                            "cash-receipt",
                            "2026-06-04",
                            "agent-bhd-plan-1",
                            "AGENT",
                            "command-bhd-plan-1",
                            "idem-bhd-plan-1",
                            "cause-bhd-plan-1",
                            null)))
                .indent(16)
                .stripLeading())
        .getBytes(UTF_8);
  }

  public static String canonicalOpenBookJson(String functionalCurrency) {
    return """
        {
          "entityName": "Acme Studio",
          "bookTemplateId": "OWNER_MANAGED_SERVICE",
          "accountingBasis": "CASH",
          "functionalCurrency": "%s",
          "fiscalYearStart": "01-01",
          "bookStartEffectiveDate": "2026-01-01"
        }
        """
        .formatted(functionalCurrency)
        .indent(14)
        .stripLeading();
  }

  public static String declareOrdinaryAccountStepJson(
      String stepId, String accountCode, String accountName, AccountType accountType) {
    return declareAccountStepJson(stepId, accountCode, accountName, accountType);
  }

  public static String declareAccountStepJson(
      String stepId, String accountCode, String accountName, AccountType accountType) {
    return """
        {
          "stepId": "%s",
          "kind": "declare-account",
          "declareAccount": %s
        }
        """
        .formatted(
            stepId,
            declareAccountJson(accountCode, accountName, accountType).indent(4).stripLeading());
  }

  public static String declareAccountJson(
      String accountCode, String accountName, AccountType accountType) {
    return """
        {
          "accountCode": "%s",
          "accountName": "%s",
          "accountType": "%s",
          "accountNodeKind": "POSTABLE",
          "financialPositionLineClassification": %s,
          "profitAndLossLineClassification": %s,
          "cashFlowAssetClassification": %s
        }
        """
        .formatted(
            accountCode,
            accountName,
            accountType.name(),
            quotedOrNull(financialPositionLineClassificationWireValue(accountType)),
            quotedOrNull(profitAndLossLineClassificationWireValue(accountType)),
            quotedOrNull(cashFlowAssetClassificationWireValue(accountType)));
  }

  static byte[] rejectedMissingBookListPostingsLedgerPlanBytes() {
    return """
        {
          "planId": "missing-book-list-postings",
          "steps": [
            {
              "stepId": "list-postings",
              "kind": "list-postings",
              "listPostings": {
                "limit": 10
              }
            }
          ]
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidLedgerPlanBytes() {
    return """
        {
          "planId": "bad-plan",
          "steps": []
        }
        """
        .getBytes(UTF_8);
  }

  private static String quotedOrNull(@Nullable String value) {
    if (value == null) {
      return "null";
    }
    return "\"" + value + "\"";
  }

  private static @Nullable String financialPositionLineClassificationWireValue(
      AccountType accountType) {
    return switch (accountType) {
      case ASSET -> FinancialPositionLineClassification.CURRENT_ASSET.name();
      case LIABILITY -> FinancialPositionLineClassification.CURRENT_LIABILITY.name();
      case EQUITY -> FinancialPositionLineClassification.OTHER_EQUITY.name();
      case REVENUE, EXPENSE -> null;
    };
  }

  private static @Nullable String profitAndLossLineClassificationWireValue(
      AccountType accountType) {
    return switch (accountType) {
      case REVENUE -> ProfitAndLossLineClassification.OPERATING_REVENUE.name();
      case EXPENSE -> ProfitAndLossLineClassification.OPERATING_EXPENSE.name();
      case ASSET, LIABILITY, EQUITY -> null;
    };
  }

  private static @Nullable String cashFlowAssetClassificationWireValue(AccountType accountType) {
    return accountType == AccountType.ASSET
        ? CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT.name()
        : null;
  }
}
