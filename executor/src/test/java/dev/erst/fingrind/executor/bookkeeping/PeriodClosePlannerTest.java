package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.Provider;
import java.security.Security;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Coverage-focused tests for private close-planner helpers that guard durable evidence facts. */
class PeriodResultTransferPlannerTest {
  private static final MethodHandle SHA256_HEX = plannerHelper("sha256Hex");

  @Test
  @ResourceLock("java.security.providers")
  void sha256Hex_reportsUnavailableDigestAlgorithm() {
    Provider[] originalProviders = Security.getProviders();
    try {
      removeSha256Providers();
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> sha256Hex("period-result-transfer-material"));

      assertEquals("SHA-256 is unavailable in this Java runtime.", exception.getMessage());
    } finally {
      restoreProviders(originalProviders);
    }
  }

  private static String sha256Hex(String value) {
    try {
      return (String) SHA256_HEX.invokeExact(value);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke close-planner digest helper.", throwable);
    }
  }

  private static MethodHandle plannerHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(PeriodResultTransferPlanner.class, MethodHandles.lookup());
      return lookup.findStatic(
          PeriodResultTransferPlanner.class,
          methodName,
          MethodType.methodType(String.class, String.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind close-planner helper: " + methodName, exception);
    }
  }

  private static void removeSha256Providers() {
    for (Provider provider : Security.getProviders()) {
      if (provider.getService("MessageDigest", "SHA-256") != null) {
        Security.removeProvider(provider.getName());
      }
    }
  }

  private static void restoreProviders(Provider[] providers) {
    for (Provider provider : Security.getProviders()) {
      Security.removeProvider(provider.getName());
    }
    for (int index = 0; index < providers.length; index++) {
      Security.insertProviderAt(providers[index], index + 1);
    }
  }
}
