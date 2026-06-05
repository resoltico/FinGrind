package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Tests for configured-target SQLite runtime probes and native-access gating. */
class SqliteRuntimeConfiguredTargetProbeTest extends SqliteNativeBridgeTestSupport {
  @Test
  void sqliteRuntimeProbe_reportsUnavailableWhenConfiguredLibraryTargetIsInvalid() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probeConfiguredTarget(
            () -> {
              throw new IllegalStateException("bundle launcher misconfigured");
            });
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertNull(runtimeProbe.runtimeProvenance());
    assertNull(runtimeProbe.runtimeTrustBasis());
    assertTrue(requireIssue(runtimeProbe).contains("bundle launcher misconfigured"));
  }

  @Test
  void
      sqliteRuntimeProbe_twoArgumentOverloadReportsUnavailableWhenConfiguredLibraryTargetIsInvalid() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probeConfiguredTarget(
            () -> {
              throw new IllegalStateException("two-arg bundle launcher misconfigured");
            },
            SqliteNativeAccessGate.runtimeModule());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertNull(runtimeProbe.runtimeProvenance());
    assertNull(runtimeProbe.runtimeTrustBasis());
    assertTrue(requireIssue(runtimeProbe).contains("two-arg bundle launcher misconfigured"));
  }

  @Test
  void sqliteRuntimeProbe_reportsUnavailableWhenNativeAccessIsDisabled() {
    Module unnamedModule = Thread.currentThread().getContextClassLoader().getUnnamedModule();

    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probeConfiguredTarget(
            () ->
                new SqliteLibraryTarget(
                    SqliteRuntime.LIBRARY_MODE,
                    dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance
                        .SOURCE_CHECKOUT_MANAGED,
                    "/tmp/libsqlite3.dylib"),
            unnamedModule,
            false);

    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals(
        SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
        runtimeProbe.compileOptionsVerification());
    assertNull(runtimeProbe.runtimeProvenance());
    assertNull(runtimeProbe.runtimeTrustBasis());
    assertNull(runtimeProbe.loadedLibraryPath());
    assertTrue(requireIssue(runtimeProbe).contains("--enable-native-access=ALL-UNNAMED"));
  }

  private static String requireIssue(SqliteRuntime.Probe runtimeProbe) {
    return Objects.requireNonNull(runtimeProbe.issue());
  }
}
