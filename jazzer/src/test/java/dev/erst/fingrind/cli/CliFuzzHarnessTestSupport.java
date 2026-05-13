package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

final class CliFuzzHarnessTestSupport {
  private CliFuzzHarnessTestSupport() {}

  static FuzzedDataProvider fuzzedBytes(byte[] input) {
    InvocationHandler handler = new FuzzedBytesDataProviderHandler(input);
    return (FuzzedDataProvider)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {FuzzedDataProvider.class},
            handler);
  }

  static void invokeFuzzHarness(String className, String methodName, byte[] input) {
    try {
      Class<?> harnessClass =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Object harness = harnessClass.getDeclaredConstructor().newInstance();
      Method method = harnessClass.getDeclaredMethod(methodName, FuzzedDataProvider.class);
      method.setAccessible(true);
      method.invoke(harness, fuzzedBytes(input));
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Fuzz harness invocation failed.", cause);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to invoke compiled fuzz harness.", exception);
    }
  }

  static byte[] basicValidRequestBytes() {
    return SqliteRoundTripWorkflowTestSupport.basicValidRequest().getBytes(UTF_8);
  }

  static byte[] validJpyRequestBytes() {
    return """
        {
          "effectiveDate": "2026-06-01",
          "lines": [
            {
              "accountCode": "1100",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "JPY",
                "minorUnits": "100"
              }
            },
            {
              "accountCode": "2100",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "JPY",
                "minorUnits": "100"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-jpy-1",
            "actorType": "AGENT",
            "commandId": "command-jpy-1",
            "idempotencyKey": "idem-jpy-1",
            "causationId": "cause-jpy-1"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] validBhdRequestBytes() {
    return """
        {
          "effectiveDate": "2026-06-02",
          "lines": [
            {
              "accountCode": "1200",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "BHD",
                "minorUnits": "1250"
              }
            },
            {
              "accountCode": "2200",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "BHD",
                "minorUnits": "1250"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-bhd-1",
            "actorType": "AGENT",
            "commandId": "command-bhd-1",
            "idempotencyKey": "idem-bhd-1",
            "causationId": "cause-bhd-1"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidExponentAmountRequestBytes() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1e1000000100"
              }
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "100"
              }
            }
          ],
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] invalidBlankActorRequestBytes() {
    return """
        {
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
            "actorId": "   ",
            "actorType": "AGENT",
            "commandId": "command-3",
            "idempotencyKey": "idem-3",
            "causationId": "cause-3"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] missingReversalReasonRequestBytes() {
    return """
        {
          "effectiveDate": "2026-04-08",
          "lines": [
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
          ],
          "reversal": {
            "priorPostingId": "posting-old"
          },
          "provenance": {
            "actorId": "actor-2",
            "actorType": "HUMAN",
            "commandId": "command-2",
            "idempotencyKey": "idem-2",
            "causationId": "cause-2"
          }
        }
        """
        .getBytes(UTF_8);
  }

  static String reversalTargetMissingRequest() {
    return """
        {
          "effectiveDate": "2026-04-08",
          "lines": [
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
          ],
          "reversal": {
            "priorPostingId": "posting-missing",
            "reason": "operator reversal"
          },
          "provenance": {
            "actorId": "actor-5",
            "actorType": "HUMAN",
            "commandId": "command-5",
            "idempotencyKey": "idem-5",
            "causationId": "cause-5"
          }
        }
        """;
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

  static byte[] basicValidLedgerPlanBytes() {
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
            }
          ]
        }
        """
        .getBytes(UTF_8);
  }

  static byte[] validJpyLedgerPlanBytes() {
    return """
        {
          "planId": "plan-jpy-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash-jpy",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1100",
                "accountName": "Cash JPY",
                "accountType": "ASSET",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "declare-sales-jpy",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2100",
                "accountName": "Sales JPY",
                "accountType": "REVENUE",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "post-jpy",
              "kind": "post-entry",
              "posting": {
                "effectiveDate": "2026-06-03",
                "lines": [
                  {
                    "accountCode": "1100",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "JPY",
                      "minorUnits": "100"
                    }
                  },
                  {
                    "accountCode": "2100",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "JPY",
                      "minorUnits": "100"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-jpy-plan-1",
                  "actorType": "AGENT",
                  "commandId": "command-jpy-plan-1",
                  "idempotencyKey": "idem-jpy-plan-1",
                  "causationId": "cause-jpy-plan-1"
                }
              }
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
        .getBytes(UTF_8);
  }

  static byte[] validBhdLedgerPlanBytes() {
    return """
        {
          "planId": "plan-bhd-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare-cash-bhd",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1200",
                "accountName": "Cash BHD",
                "accountType": "ASSET",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "declare-sales-bhd",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "2200",
                "accountName": "Sales BHD",
                "accountType": "REVENUE",
                "accountRole": "ORDINARY"
              }
            },
            {
              "stepId": "post-bhd",
              "kind": "post-entry",
              "posting": {
                "effectiveDate": "2026-06-04",
                "lines": [
                  {
                    "accountCode": "1200",
                    "side": "DEBIT",
                    "amount": {
                      "currencyCode": "BHD",
                      "minorUnits": "1250"
                    }
                  },
                  {
                    "accountCode": "2200",
                    "side": "CREDIT",
                    "amount": {
                      "currencyCode": "BHD",
                      "minorUnits": "1250"
                    }
                  }
                ],
                "provenance": {
                  "actorId": "agent-bhd-plan-1",
                  "actorType": "AGENT",
                  "commandId": "command-bhd-plan-1",
                  "idempotencyKey": "idem-bhd-plan-1",
                  "causationId": "cause-bhd-plan-1"
                }
              }
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
        .getBytes(UTF_8);
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

  private static final class FuzzedBytesDataProviderHandler implements InvocationHandler {
    private final byte[] input;

    private FuzzedBytesDataProviderHandler(byte[] input) {
      this.input = input.clone();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "consumeRemainingAsBytes" -> input.clone();
        case "toString" -> "FuzzedBytesDataProvider";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" ->
            args != null
                && args.length == 1
                && args[0] != null
                && Proxy.isProxyClass(args[0].getClass())
                && Objects.equals(Proxy.getInvocationHandler(args[0]), this);
        default ->
            throw new UnsupportedOperationException(
                "Unsupported FuzzedDataProvider method: " + method.getName());
      };
    }
  }
}
