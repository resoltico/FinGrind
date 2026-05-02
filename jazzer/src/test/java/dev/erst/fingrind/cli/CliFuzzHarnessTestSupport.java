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

  static byte[] invalidExponentAmountRequestBytes() {
    return """
        {
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "currencyCode": "EUR",
              "amount": "1e1000000100"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "1.00"
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
              "currencyCode": "EUR",
              "amount": "10.00"
            },
            {
              "accountCode": "2000",
              "side": "CREDIT",
              "currencyCode": "EUR",
              "amount": "10.00"
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
              "currencyCode": "GBP",
              "amount": "123.45"
            },
            {
              "accountCode": "6000",
              "side": "DEBIT",
              "currencyCode": "GBP",
              "amount": "123.45"
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
              "currencyCode": "GBP",
              "amount": "123.45"
            },
            {
              "accountCode": "6000",
              "side": "DEBIT",
              "currencyCode": "GBP",
              "amount": "123.45"
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
                "normalBalance": "DEBIT"
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
