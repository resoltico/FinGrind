package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Injected path-identity outcomes for the ACL fixture filesystem. */
final class AclFixturePathIdentityPlan {
  private @Nullable IOException realPathFailure;
  private final Deque<AclFixturePlannedIOException> plannedRealPathFailures = new ArrayDeque<>();
  private @Nullable Path realPathOverride;
  private @Nullable IOException sameFileFailure;
  private final Map<String, IOException> sameFileFailuresByOtherPath = new ConcurrentHashMap<>();

  void failToRealPathWith(IOException exception) {
    realPathFailure = Objects.requireNonNull(exception, "exception");
  }

  void failToRealPathAfterSuccessfulCallsWith(
      int successfulCallsBeforeFailure, IOException exception) {
    plannedRealPathFailures.addLast(
        new AclFixturePlannedIOException(successfulCallsBeforeFailure, exception));
  }

  void returnRealPath(Path path) {
    realPathOverride = Objects.requireNonNull(path, "path");
  }

  Path resolveRealPath(RealPathResolver resolver) throws IOException {
    IOException failure = realPathFailure;
    if (failure != null) {
      throw failure;
    }
    IOException plannedFailure = AclFixturePlannedIOException.nextFailure(plannedRealPathFailures);
    if (plannedFailure != null) {
      throw plannedFailure;
    }
    Path override = realPathOverride;
    return override == null ? resolver.resolve() : override;
  }

  void failSameFileWith(IOException exception) {
    sameFileFailure = Objects.requireNonNull(exception, "exception");
  }

  @Nullable IOException sameFileFailure() {
    return sameFileFailure;
  }

  void failSameFileAgainst(Path otherPath, IOException exception) {
    sameFileFailuresByOtherPath.put(
        Objects.requireNonNull(otherPath, "otherPath").toString(),
        Objects.requireNonNull(exception, "exception"));
  }

  @Nullable IOException sameFileFailureAgainst(Path otherPath) {
    return sameFileFailuresByOtherPath.get(
        Objects.requireNonNull(otherPath, "otherPath").toString());
  }

  /** Resolves the fixture path only when no planned identity outcome takes precedence. */
  @FunctionalInterface
  interface RealPathResolver {
    Path resolve() throws IOException;
  }
}
