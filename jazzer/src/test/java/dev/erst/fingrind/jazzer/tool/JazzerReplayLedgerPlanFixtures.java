package dev.erst.fingrind.jazzer.tool;

import static dev.erst.fingrind.cli.CliFuzzHarnessTestSupport.canonicalOpenBookJson;
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
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-1",
                  "actorType": "AGENT",
                  "commandId": "command-1",
                  "idempotencyKey": "idem-plan-1",
                  "causationId": "cause-1"
                }
              }
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
              "posting": {
                "postingKind": "STANDARD",
                "effectiveDate": "2026-04-07",
                "lines": [
                  {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  },
                  {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-1",
                  "actorType": "AGENT",
                  "commandId": "command-query-1",
                  "idempotencyKey": "idem-query-1",
                  "causationId": "cause-query-1"
                }
              }
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
