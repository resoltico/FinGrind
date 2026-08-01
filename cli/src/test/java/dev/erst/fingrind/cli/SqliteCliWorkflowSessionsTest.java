package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers preservation of deterministic workflow failures across session cleanup. */
class SqliteCliWorkflowSessionsTest {
  @Test
  void sessionCleanup_suppressesItsFailureWithoutReplacingThePrimaryContractFailure() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException closeFailure = new IllegalStateException("close failed");
    ContractFailureException primaryFailure =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));

    ContractFailureException thrown =
        assertThrows(
            ContractFailureException.class,
            () ->
                SqliteCliWorkflowSessions.withReadSession(
                    ContractDecision.accepted(failingCloseSession(closeCalls, closeFailure)),
                    ignored -> {
                      throw primaryFailure;
                    }));

    assertSame(primaryFailure, thrown);
    assertEquals(1, closeCalls.get());
    assertEquals(1, primaryFailure.getSuppressed().length);
    assertSame(closeFailure, primaryFailure.getSuppressed()[0]);
  }

  @Test
  void newBookSessionCleanup_mergesReturnedRejectionWithItsCloseFailure() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException closeFailure = new IllegalStateException("close failed");
    var original = ContractErrors.unsupportedBookFormatVersionFailure(7, 8);
    var merged = ContractErrors.protectedBookVerificationFailure();

    ContractDecision<String> result =
        SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
            ContractDecision.accepted(failingNewBookCloseSession(closeCalls, closeFailure)),
            ignored -> ContractDecision.rejected(original),
            (rejection, observedCloseFailure) -> {
              assertSame(original, rejection);
              assertSame(closeFailure, observedCloseFailure);
              return ContractDecision.rejected(merged);
            },
            (workFailure, observedCloseFailure) -> {
              throw new AssertionError("Returned rejection must use the rejection close callback.");
            },
            (accepted, observedCloseFailure) -> {
              throw new AssertionError(
                  "Returned rejection must not use the accepted close callback.");
            });

    var rejected = assertInstanceOf(ContractDecision.Rejected.class, result);
    assertSame(merged, rejected.failure());
    assertEquals(1, closeCalls.get());
  }

  @Test
  void newBookSessionCleanup_convertsThrownWorkAndCloseFailureThroughTheDedicatedCallback() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException closeFailure = new IllegalStateException("close failed");
    IllegalStateException workFailure = new IllegalStateException("work failed");
    var merged = ContractErrors.protectedBookVerificationFailure();

    ContractDecision<String> result =
        SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
            ContractDecision.accepted(failingNewBookCloseSession(closeCalls, closeFailure)),
            ignored -> {
              throw workFailure;
            },
            (rejection, observedCloseFailure) -> {
              throw new AssertionError("Thrown work must not use the returned-rejection callback.");
            },
            (observedWorkFailure, observedCloseFailure) -> {
              assertSame(workFailure, observedWorkFailure);
              assertSame(closeFailure, observedCloseFailure);
              return ContractDecision.rejected(merged);
            },
            (accepted, observedCloseFailure) -> {
              throw new AssertionError("Thrown work must not use the accepted close callback.");
            });

    var rejected = assertInstanceOf(ContractDecision.Rejected.class, result);
    assertSame(merged, rejected.failure());
    assertEquals(1, closeCalls.get());
  }

  @Test
  void newBookSessionCleanup_rethrowsWorkFailureWhenTheCloseCompletes() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException workFailure = new IllegalStateException("work failed");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
                    ContractDecision.accepted(closingNewBookSession(closeCalls)),
                    ignored -> {
                      throw workFailure;
                    },
                    (rejection, observedCloseFailure) -> {
                      throw new AssertionError("Thrown work must not use the rejection callback.");
                    },
                    (observedWorkFailure, observedCloseFailure) -> {
                      throw new AssertionError(
                          "A successful close must not use the work-and-close callback.");
                    },
                    (accepted, observedCloseFailure) -> {
                      throw new AssertionError("Thrown work must not use the accepted callback.");
                    }));

    assertSame(workFailure, thrown);
    assertEquals(1, closeCalls.get());
  }

  @Test
  void newBookSessionCleanup_suppressesCloseFailureWithoutReplacingAnError() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException closeFailure = new IllegalStateException("close failed");
    AssertionError workFailure = new AssertionError("work failed");

    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
                    ContractDecision.accepted(failingNewBookCloseSession(closeCalls, closeFailure)),
                    ignored -> {
                      throw workFailure;
                    },
                    (rejection, observedCloseFailure) -> {
                      throw new AssertionError("Errors must not use the rejection callback.");
                    },
                    (observedWorkFailure, observedCloseFailure) -> {
                      throw new AssertionError("Errors must not use the work-and-close callback.");
                    },
                    (accepted, observedCloseFailure) -> {
                      throw new AssertionError("Errors must not use the accepted callback.");
                    }));

    assertSame(workFailure, thrown);
    assertEquals(1, closeCalls.get());
    assertEquals(1, workFailure.getSuppressed().length);
    assertSame(closeFailure, workFailure.getSuppressed()[0]);
  }

  @Test
  void newBookSessionCleanup_convertsAcceptedResultAndCloseFailureThroughTheDedicatedCallback() {
    AtomicInteger closeCalls = new AtomicInteger();
    IllegalStateException closeFailure = new IllegalStateException("close failed");
    var uncertainCompletion = ContractErrors.protectedBookVerificationFailure();

    ContractDecision<String> result =
        SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
            ContractDecision.accepted(failingNewBookCloseSession(closeCalls, closeFailure)),
            ignored -> ContractDecision.accepted("opened"),
            (rejection, observedCloseFailure) -> {
              throw new AssertionError(
                  "Accepted work must not use the returned-rejection callback.");
            },
            (workFailure, observedCloseFailure) -> {
              throw new AssertionError("Accepted work must not use the thrown-work callback.");
            },
            (accepted, observedCloseFailure) -> {
              assertEquals("opened", accepted);
              assertSame(closeFailure, observedCloseFailure);
              return ContractDecision.rejected(uncertainCompletion);
            });

    var rejected = assertInstanceOf(ContractDecision.Rejected.class, result);
    assertSame(uncertainCompletion, rejected.failure());
    assertEquals(1, closeCalls.get());
  }

  private static SqliteReadSession failingCloseSession(
      AtomicInteger closeCalls, IllegalStateException closeFailure) {
    return (SqliteReadSession)
        Proxy.newProxyInstance(
            proxyClassLoader(),
            new Class<?>[] {SqliteReadSession.class},
            (ignored, method, arguments) -> {
              if ("close".equals(method.getName())) {
                closeCalls.incrementAndGet();
                throw closeFailure;
              }
              throw new AssertionError("Unexpected session method: " + method.getName());
            });
  }

  private static SqliteAdministrationSession failingNewBookCloseSession(
      AtomicInteger closeCalls, IllegalStateException closeFailure) {
    return (SqliteAdministrationSession)
        Proxy.newProxyInstance(
            proxyClassLoader(),
            new Class<?>[] {SqliteAdministrationSession.class},
            (ignored, method, arguments) -> {
              if ("close".equals(method.getName())) {
                closeCalls.incrementAndGet();
                throw closeFailure;
              }
              throw new AssertionError("Unexpected session method: " + method.getName());
            });
  }

  private static SqliteAdministrationSession closingNewBookSession(AtomicInteger closeCalls) {
    return (SqliteAdministrationSession)
        Proxy.newProxyInstance(
            proxyClassLoader(),
            new Class<?>[] {SqliteAdministrationSession.class},
            (ignored, method, arguments) -> {
              if ("close".equals(method.getName())) {
                closeCalls.incrementAndGet();
                return null;
              }
              throw new AssertionError("Unexpected session method: " + method.getName());
            });
  }

  private static ClassLoader proxyClassLoader() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
  }
}
