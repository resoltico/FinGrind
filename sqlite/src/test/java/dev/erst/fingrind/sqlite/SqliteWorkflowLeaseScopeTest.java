package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves exact ownership and close behavior for one acquired maintenance workflow scope. */
class SqliteWorkflowLeaseScopeTest {
  @TempDir Path tempDirectory;

  @Test
  void requiresAtLeastOneSourceLease() {
    assertThrows(IllegalArgumentException.class, () -> scope(List.of(), () -> {}, () -> {}));
  }

  @Test
  void targetAdmissionsCanTransferOnceAndCannotTransferAfterClose() {
    try (SqliteWorkflowLeaseScope scope =
            scope(List.of(lease("source", () -> {})), () -> {}, () -> {});
        SqliteTargetAdmissionLeases admissions = scope.takeTargetAdmissionLeases()) {
      assertThrows(IllegalStateException.class, scope::takeTargetAdmissionLeases);
      admissions.close();
      scope.close();
      assertThrows(IllegalStateException.class, scope::takeTargetAdmissionLeases);
    }
  }

  @Test
  void closeReleasesAllSourceLeasesInReverseAndPreservesLaterFailures() {
    AtomicInteger releases = new AtomicInteger();
    IllegalStateException first = new IllegalStateException("first source close");
    IllegalStateException second = new IllegalStateException("second source close");
    try (SqliteWorkflowLeaseScope scope =
        scope(
            List.of(
                lease(
                    "first",
                    () -> {
                      releases.incrementAndGet();
                      throw first;
                    }),
                lease(
                    "second",
                    () -> {
                      releases.incrementAndGet();
                      throw second;
                    })),
            () -> {},
            () -> {})) {
      IllegalStateException failure = assertThrows(IllegalStateException.class, scope::close);
      assertEquals(second, failure);
      assertEquals(List.of(first), List.of(failure.getSuppressed()));
      assertEquals(2, releases.get());
      scope.close();
      assertEquals(2, releases.get());
    }
  }

  private SqliteWorkflowLeaseScope scope(
      List<SqliteHeldLease> sources, Runnable bookRelease, Runnable secretRelease) {
    return new SqliteWorkflowLeaseScope(
        tempDirectory.resolve("source.sqlite"),
        sources,
        lease("target.sqlite", bookRelease),
        lease("target.key", secretRelease));
  }

  private SqliteHeldLease lease(String name, Runnable release) {
    return new SqliteHeldLease(tempDirectory.resolve(name), release);
  }
}
