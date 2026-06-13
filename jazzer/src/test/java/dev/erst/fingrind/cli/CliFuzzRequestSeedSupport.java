package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class CliFuzzRequestSeedSupport {
  private CliFuzzRequestSeedSupport() {}

  static byte[] basicValidRequestBytes() {
    return SqliteRoundTripWorkflowTestSupport.basicValidRequest().getBytes(UTF_8);
  }

  static byte[] validJpyRequestBytes() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
            new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                "2026-06-01",
                "1100",
                "2100",
                "JPY",
                "100",
                new CliFuzzHarnessTestSupport.RequestContext(
                    "document-idem-jpy-1",
                    "cash-receipt",
                    "2026-06-01",
                    "actor-jpy-1",
                    "AGENT",
                    "command-jpy-1",
                    "idem-jpy-1",
                    "cause-jpy-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] validBhdRequestBytes() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
            new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                "2026-06-02",
                "1200",
                "2200",
                "BHD",
                "1250",
                new CliFuzzHarnessTestSupport.RequestContext(
                    "document-idem-bhd-1",
                    "cash-receipt",
                    "2026-06-02",
                    "actor-bhd-1",
                    "AGENT",
                    "command-bhd-1",
                    "idem-bhd-1",
                    "cause-bhd-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] invalidExponentAmountRequestBytes() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
            new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                "2026-04-07",
                "1000",
                "2000",
                "EUR",
                "1e1000000100",
                new CliFuzzHarnessTestSupport.RequestContext(
                    "document-idem-1",
                    "cash-receipt",
                    "2026-04-07",
                    "actor-1",
                    "AGENT",
                    "command-1",
                    "idem-1",
                    "cause-1",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] invalidBlankActorRequestBytes() {
    return CliFuzzHarnessTestSupport.cashRevenueRequestJson(
            new CliFuzzHarnessTestSupport.CashRevenueRequestInput(
                "2026-04-07",
                "1000",
                "2000",
                "EUR",
                "1000",
                new CliFuzzHarnessTestSupport.RequestContext(
                    "document-idem-3",
                    "cash-receipt",
                    "2026-04-07",
                    "   ",
                    "AGENT",
                    "command-3",
                    "idem-3",
                    "cause-3",
                    null)))
        .getBytes(UTF_8);
  }

  static byte[] missingReversalReasonRequestBytes() {
    return CliFuzzHarnessTestSupport.reversalAdjustmentRequestJson(
            new CliFuzzHarnessTestSupport.ReversalAdjustmentRequestInput(
                "2026-04-08",
                """
                [
                  {
                    "accountCode": "5000",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "GBP",
                      "minorUnits": "12345"
                    }
                  },
                  {
                    "accountCode": "6000",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "GBP",
                      "minorUnits": "12345"
                    }
                  }
                ]
                """,
                new CliFuzzHarnessTestSupport.RequestContext(
                    "document-idem-2",
                    "credit-note",
                    "2026-04-08",
                    "actor-2",
                    "PERSON",
                    "command-2",
                    "idem-2",
                    "cause-2",
                    null),
                "posting-old",
                null))
        .getBytes(UTF_8);
  }

  static String reversalTargetMissingRequest() {
    return CliFuzzHarnessTestSupport.reversalAdjustmentRequestJson(
        new CliFuzzHarnessTestSupport.ReversalAdjustmentRequestInput(
            "2026-04-08",
            """
            [
              {
                "accountCode": "5000",
                "side": "CREDIT",
                "amount": {
                  "currencyCode": "GBP",
                  "minorUnits": "12345"
                }
              },
              {
                "accountCode": "6000",
                "side": "DEBIT",
                "amount": {
                  "currencyCode": "GBP",
                  "minorUnits": "12345"
                }
              }
            ]
            """,
            new CliFuzzHarnessTestSupport.RequestContext(
                "document-idem-5",
                "credit-note",
                "2026-04-08",
                "actor-5",
                "PERSON",
                "command-5",
                "idem-5",
                "cause-5",
                null),
            "posting-missing",
            "operator reversal"));
  }

  static byte[] reversalTargetMissingRequestBytes() {
    return reversalTargetMissingRequest().getBytes(UTF_8);
  }

  static byte[] invalidWrongTypeRequestBytes() {
    return """
        {
          "effectiveDate": 1,
          "lines": [],
          "provenance": {}
        }
        """
        .getBytes(UTF_8);
  }
}
