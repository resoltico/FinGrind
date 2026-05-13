package dev.erst.fingrind.jazzer.tool;

final class JazzerReplayLedgerPlanFixtures {
  private JazzerReplayLedgerPlanFixtures() {}

  static String basicValidLedgerPlan() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "accountType": "ASSET",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "declare-revenue",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2000",
                "accountName": "Revenue",
                "accountType": "REVENUE",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": {
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
        """;
  }

  static String validLedgerPlanWithQueries() {
    return """
        {
          "planId": "plan-query-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "accountType": "ASSET",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "declare-revenue",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2000",
                "accountName": "Revenue",
                "accountType": "REVENUE",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "post-sale",
              "kind": "post-entry",
              "posting": {
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
        """;
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
