package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Focused branch coverage for SQLite protected-book verification support seams. */
class SqliteProtectedBookVerificationSupportCoverageTest
    extends SqliteArtifactPublicationTestSupport {
  private static final MethodHandle INSPECT_OPENED_BOOK = bindInspectOpenedBook();

  @Test
  void verifyResolvedBook_closesOneCallerSecretWhenPassphraseCopyRejects() {
    try (SqliteBookPassphrase closedPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "closed-passphrase", "secret".getBytes(StandardCharsets.UTF_8))) {
      closedPassphrase.close();

      ContractFailureException exception =
          assertThrows(
              ContractFailureException.class,
              () ->
                  VERIFICATION_SUPPORT.verifyResolvedBook(
                      Path.of("closed.sqlite"), closedPassphrase));

      assertEquals(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE,
          exception.failure().descriptor());
      assertPassphraseZeroized(closedPassphrase);
    }
  }

  @Test
  void mapInspection_rejectsInitializedSnapshotsAndMapsExistingFailures() {
    Path normalizedBookPath =
        tempDirectory.resolve("inspection.sqlite").toAbsolutePath().normalize();

    IllegalArgumentException initializedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                VERIFICATION_SUPPORT.mapInspection(
                    normalizedBookPath,
                    SqlitePostingFactFixtureSupport.initializedLifecycleInspection(
                        SqliteBookContract.APPLICATION_ID,
                        SqliteBookContract.FORMAT_VERSION,
                        SqliteBookContract.FORMAT_VERSION,
                        Instant.parse("2026-06-12T10:15:00Z"))));
    assertTrue(
        NullTestSupport.messageOf(initializedFailure)
            .contains("requires one resolved verified-book handle"));

    ProtectedBookMaintenanceStore.BookVerification blankVerification =
        VERIFICATION_SUPPORT.mapInspection(
            normalizedBookPath,
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.BLANK_SQLITE,
                0,
                0,
                SqliteBookContract.FORMAT_VERSION));
    assertVerificationFailure(
        blankVerification, normalizedBookPath, ProtectedBookVerificationFailure.BLANK_SQLITE);
  }

  @Test
  void inspectOpenedBook_mapsMissingLifecycleInspectionsAndClosesTheSession() {
    AtomicBoolean sessionClosed = new AtomicBoolean(false);
    Path normalizedBookPath =
        tempDirectory.resolve("missing-inspection.sqlite").toAbsolutePath().normalize();
    try (SqliteBookPassphrase bookPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "missing-inspection", "secret".getBytes(StandardCharsets.UTF_8))) {
      ProtectedBookMaintenanceStore.BookVerification verification =
          invokeInspectOpenedBook(
              normalizedBookPath,
              readSession(
                  () -> new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION),
                  () -> sessionClosed.set(true)),
              bookPassphrase);

      assertVerificationFailure(
          verification, normalizedBookPath, ProtectedBookVerificationFailure.MISSING);
      assertTrue(sessionClosed.get());
      assertPassphraseZeroized(bookPassphrase);
    }
  }

  @Test
  void inspectOpenedBook_rethrowsInspectionFailuresAfterClosingResources() {
    AtomicBoolean sessionClosed = new AtomicBoolean(false);
    Path normalizedBookPath =
        tempDirectory.resolve("inspection-failure.sqlite").toAbsolutePath().normalize();
    try (SqliteBookPassphrase bookPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "inspection-failure", "secret".getBytes(StandardCharsets.UTF_8))) {
      AssertionError inspectionFailure =
          assertThrows(
              AssertionError.class,
              () ->
                  invokeInspectOpenedBook(
                      normalizedBookPath,
                      readSession(
                          () -> {
                            throw new AssertionError("inspection-boom");
                          },
                          () -> sessionClosed.set(true)),
                      bookPassphrase));

      assertEquals("inspection-boom", inspectionFailure.getMessage());
      assertTrue(sessionClosed.get());
      assertPassphraseZeroized(bookPassphrase);
    }
  }

  @Test
  void maintenanceStore_reportsAMissingBackupArtifact() {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("replica-source").resolve("book.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path missingReplicaPath = tempDirectory.resolve("replica-target").resolve("missing.sqlite");

    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess(missingReplicaPath)),
                dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                    .BACKUP_TARGET)),
        missingReplicaPath,
        ProtectedBookVerificationFailure.MISSING);
  }

  @Test
  void inspectionMappers_coverEveryNoninitializedBookStateAndContractFailureBoundary() {
    Path normalizedBookPath = tempDirectory.resolve("all-inspection-states.sqlite");
    assertVerificationFailure(
        VERIFICATION_SUPPORT.mapInspection(
            normalizedBookPath,
            new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION)),
        normalizedBookPath,
        ProtectedBookVerificationFailure.MISSING);
    assertEquals(
        ProtectedBookVerificationFailure.FOREIGN_SQLITE,
        VERIFICATION_SUPPORT.mapInspectionFailure(BookLifecycleInspection.Status.FOREIGN_SQLITE));
    assertEquals(
        ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION,
        VERIFICATION_SUPPORT.mapInspectionFailure(
            BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION));
    assertEquals(
        ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND,
        VERIFICATION_SUPPORT.mapInspectionFailure(
            BookLifecycleInspection.Status.INCOMPLETE_FINGRIND));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            VERIFICATION_SUPPORT.mapInspectionFailure(BookLifecycleInspection.Status.INITIALIZED));

    assertEquals(
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        SqliteProtectedBookVerificationSupport.protectedBookVerificationFailure(
            ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                "book verification failed", null, null)));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookVerificationSupport.protectedBookVerificationFailure(
                ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
                    "invalid key", null, null)));
  }

  @Test
  void inspectOpenedBook_mapsExistingFailureAndRetainsOnlyAUsableInitializedHandle() {
    Path normalizedBookPath = tempDirectory.resolve("existing-inspection.sqlite");
    AtomicBoolean existingSessionClosed = new AtomicBoolean(false);
    try (SqliteBookPassphrase existingPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "existing-inspection", "secret".getBytes(StandardCharsets.UTF_8))) {
      assertVerificationFailure(
          invokeInspectOpenedBook(
              normalizedBookPath,
              readSession(
                  () ->
                      new BookLifecycleInspection.Existing(
                          BookLifecycleInspection.Status.FOREIGN_SQLITE,
                          0,
                          0,
                          SqliteBookContract.FORMAT_VERSION),
                  () -> existingSessionClosed.set(true)),
              existingPassphrase),
          normalizedBookPath,
          ProtectedBookVerificationFailure.FOREIGN_SQLITE);
      assertTrue(existingSessionClosed.get());
      assertPassphraseZeroized(existingPassphrase);
    }

    AtomicBoolean initializedSessionClosed = new AtomicBoolean(false);
    try (SqliteBookPassphrase initializedPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "initialized-inspection", "secret".getBytes(StandardCharsets.UTF_8))) {
      ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
          (ProtectedBookMaintenanceStore.VerifiedBook)
              invokeInspectOpenedBook(
                  normalizedBookPath,
                  readSession(
                      () ->
                          SqlitePostingFactFixtureSupport.initializedLifecycleInspection(
                              SqliteBookContract.APPLICATION_ID,
                              SqliteBookContract.FORMAT_VERSION,
                              SqliteBookContract.FORMAT_VERSION,
                              Instant.parse("2026-06-12T10:15:00Z")),
                      () -> initializedSessionClosed.set(true)),
                  initializedPassphrase);
      assertEquals(normalizedBookPath, verifiedBook.artifactPath());
      assertTrue(initializedSessionClosed.get());
      verifiedBook.close();
    }
  }

  private static ProtectedBookMaintenanceStore.BookVerification invokeInspectOpenedBook(
      Path normalizedBookPath, SqliteReadSession session, SqliteBookPassphrase bookPassphrase) {
    try {
      return (ProtectedBookMaintenanceStore.BookVerification)
          INSPECT_OPENED_BOOK.invoke(
              VERIFICATION_SUPPORT, normalizedBookPath, session, bookPassphrase);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite protected-book verification helper for tests.", throwable);
    }
  }

  private static SqliteReadSession readSession(
      Supplier<BookLifecycleInspection> inspectionSupplier, Runnable closeAction) {
    ClassLoader proxyClassLoader =
        Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader(), "context class loader");
    return (SqliteReadSession)
        Proxy.newProxyInstance(
            proxyClassLoader,
            new Class<?>[] {SqliteReadSession.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "inspectBook" -> inspectionSupplier.get();
                  case "close" -> {
                    closeAction.run();
                    yield null;
                  }
                  case "toString" -> "SqliteReadSession[test-double]";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" ->
                      arguments != null
                          && arguments.length == 1
                          && arguments[0] != null
                          && Proxy.isProxyClass(arguments[0].getClass())
                          && Objects.equals(
                              Proxy.getInvocationHandler(proxy),
                              Proxy.getInvocationHandler(arguments[0]));
                  default ->
                      throw new AssertionError("Unexpected SQLite read-session method: " + method);
                });
  }

  private static void assertPassphraseZeroized(SqliteBookPassphrase passphrase) {
    assertArrayEquals(new byte[passphrase.byteLength()], passphrase.utf8BytesCopy());
  }

  private static MethodHandle bindInspectOpenedBook() {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              SqliteProtectedBookVerificationSupport.class, MethodHandles.lookup());
      return lookup.findVirtual(
          SqliteProtectedBookVerificationSupport.class,
          "inspectOpenedBook",
          MethodType.methodType(
              ProtectedBookMaintenanceStore.BookVerification.class,
              Path.class,
              SqliteReadSession.class,
              SqliteBookPassphrase.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite protected-book verification helper for tests.", exception);
    }
  }
}
