package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import org.junit.jupiter.api.Test;

/** Direct coverage tests for SQLite runtime-state normalization. */
class SqliteRuntimeStateValidatorTest {
  @Test
  void runtimeFactoryDerivesTrustBasisForReadyManagedBundleRuntime() {
    EnvironmentSqliteDescriptor.ReadyRuntime readyRuntime =
        assertInstanceOf(
            EnvironmentSqliteDescriptor.ReadyRuntime.class,
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntimeStatus.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                null,
                "/tmp/libsqlite3mc.dylib",
                "3.53.4",
                "2.4.0",
                ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                null));

    assertEquals(
        SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY, readyRuntime.runtimeTrustBasis());
  }

  @Test
  void runtimeFactoryDerivesTrustBasisForIncompatibleSourceCheckoutRuntime() {
    EnvironmentSqliteDescriptor.IncompatibleRuntime incompatibleRuntime =
        assertInstanceOf(
            EnvironmentSqliteDescriptor.IncompatibleRuntime.class,
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.FAILED,
                SqliteRuntimeStatus.INCOMPATIBLE,
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                null,
                "/tmp/libsqlite3mc.dylib",
                "3.53.4",
                "2.4.0",
                ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
                "compile options mismatch"));

    assertEquals(
        SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
        incompatibleRuntime.runtimeTrustBasis());
  }
}
