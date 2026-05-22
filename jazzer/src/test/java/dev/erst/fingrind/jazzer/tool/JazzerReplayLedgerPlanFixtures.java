package dev.erst.fingrind.jazzer.tool;

import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.canonicalOpenBookJson;
import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.cashRevenueRequestJson;
import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.declareOrdinaryAccountStepJson;

import dev.erst.fingrind.core.AccountType;

final class JazzerReplayLedgerPlanFixtures {
  private JazzerReplayLedgerPlanFixtures() {}

  static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": %s
            },
            {
              "stepId": "assert-cash",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1000",
                "netAmount": {
                  "currencyCode": "EUR",
                  "minorUnits": "1000"
                },
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-04-07",
                        "1000",
                        "2000",
                        "EUR",
                        "1000",
                        new dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-plan-1",
                            "invoice",
                            "2026-04-07",
                            "agent-1",
                            "AGENT",
                            "command-1",
                            "idem-plan-1",
                            "cause-1",
                            null)))
                .indent(16)
                .stripLeading());
  }

  static String validLedgerPlanWithQueries() {
    return """
        {
          "planId": "plan-query-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book",
              "openBook": %s
            },
            %s,
            %s,
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": %s
            },
            {
              "stepId": "page-accounts",
              "kind": "list-accounts",
              "query": {
                "limit": 1
              }
            },
            {
              "stepId": "page-postings",
              "kind": "list-postings",
              "query": {
                "limit": 1
              }
            }
          ]
        }
        """
        .formatted(
            canonicalOpenBookJson("EUR"),
            declareOrdinaryAccountStepJson("declare-cash", "1000", "Cash", AccountType.ASSET)
                .indent(12)
                .stripLeading(),
            declareOrdinaryAccountStepJson(
                    "declare-revenue", "2000", "Revenue", AccountType.REVENUE)
                .indent(12)
                .stripLeading(),
            cashRevenueRequestJson(
                    new dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                        "2026-04-07",
                        "1000",
                        "2000",
                        "EUR",
                        "1000",
                        new dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.RequestContext(
                            "document-idem-query-1",
                            "invoice",
                            "2026-04-07",
                            "agent-1",
                            "AGENT",
                            "command-query-1",
                            "idem-query-1",
                            "cause-query-1",
                            null)))
                .indent(16)
                .stripLeading());
  }

  static String invalidExecutionPolicyLedgerPlan() {
    return """
        {
          "planId": "plan-policy",
          "executionPolicy": {
            "journalLevel": "VERBOSE"
          },
          "steps": [
            {
              "stepId": "inspect",
              "kind": "inspect-book"
            }
          ]
        }
        """;
  }

  static String invalidUnknownKindLedgerPlan() {
    return """
        {
          "planId": "plan-typo",
          "steps": [
            {
              "stepId": "bad-kind",
              "kind": "post_entry"
            }
          ]
        }
        """;
  }

  static String rejectedMissingBookListPostingsLedgerPlan() {
    return """
        {
          "planId": "play-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "list-postings"
            }
          ]
        }
        """;
  }
}
