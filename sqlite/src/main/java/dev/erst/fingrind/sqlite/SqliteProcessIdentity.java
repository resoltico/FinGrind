package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.SystemUtcClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Stable current-process identity used for same-host coordination artifacts. */
final class SqliteProcessIdentity {
  static final long UNKNOWN_START_EPOCH_MILLIS = -1L;
  private static final SqliteProcessIdentity CURRENT = currentIdentity();
  private static final String ACTIVITY_MARKER_PID_PREFIX = "pid-";
  private static final String ACTIVITY_MARKER_START_PREFIX = "-start-";

  private final long pid;
  private final long startEpochMillis;

  private SqliteProcessIdentity(long pid, long startEpochMillis) {
    this.pid = pid;
    this.startEpochMillis = startEpochMillis;
  }

  static SqliteProcessIdentity current() {
    return CURRENT;
  }

  static @Nullable SqliteProcessIdentity fromLeaseMetadata(String leaseMetadataText) {
    Objects.requireNonNull(leaseMetadataText, "leaseMetadataText");
    Long parsedPid = null;
    Long parsedStartEpochMillis = null;
    for (String line : leaseMetadataText.lines().toList()) {
      if (line.startsWith("pid=")) {
        parsedPid = parseLong(line.substring("pid=".length()));
      } else if (line.startsWith("startEpochMillis=")) {
        parsedStartEpochMillis = parseLong(line.substring("startEpochMillis=".length()));
      }
    }
    if (parsedPid == null) {
      return null;
    }
    return new SqliteProcessIdentity(
        parsedPid,
        parsedStartEpochMillis == null ? UNKNOWN_START_EPOCH_MILLIS : parsedStartEpochMillis);
  }

  static @Nullable SqliteProcessIdentity fromActivityMarkerFileName(String markerFileName) {
    return fromCoordinationToken(markerFileName);
  }

  String leaseMetadataText() {
    return "pid=" + pid + "\nstartEpochMillis=" + startEpochMillis + "\n";
  }

  String coordinationToken() {
    return coordinationToken(pid, startEpochMillis);
  }

  static String coordinationToken(long pid, long startEpochMillis) {
    return ACTIVITY_MARKER_PID_PREFIX + pid + ACTIVITY_MARKER_START_PREFIX + startEpochMillis;
  }

  static @Nullable SqliteProcessIdentity fromCoordinationToken(String token) {
    Objects.requireNonNull(token, "token");
    if (!token.startsWith(ACTIVITY_MARKER_PID_PREFIX)) {
      return null;
    }
    int separatorIndex = token.indexOf(ACTIVITY_MARKER_START_PREFIX);
    if (separatorIndex < 0) {
      return null;
    }
    Long parsedPid =
        parseLong(token.substring(ACTIVITY_MARKER_PID_PREFIX.length(), separatorIndex));
    Long parsedStartEpochMillis =
        parseLong(token.substring(separatorIndex + ACTIVITY_MARKER_START_PREFIX.length()));
    if (parsedPid == null || parsedStartEpochMillis == null) {
      return null;
    }
    return new SqliteProcessIdentity(parsedPid, parsedStartEpochMillis);
  }

  String activityMarkerFileToken() {
    return coordinationToken();
  }

  static String activityMarkerFileToken(long pid, long startEpochMillis) {
    return coordinationToken(pid, startEpochMillis);
  }

  boolean isCurrentProcess() {
    return equals(CURRENT);
  }

  boolean isLive() {
    Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
    if (processHandle.isEmpty()) {
      return false;
    }
    if (startEpochMillis == UNKNOWN_START_EPOCH_MILLIS) {
      return true;
    }
    Optional<Instant> liveStartInstant = processHandle.get().info().startInstant();
    return liveStartInstant.map(instant -> instant.toEpochMilli() == startEpochMillis).orElse(true);
  }

  boolean isLiveWhenUnlocked(Instant markerLastModified, Duration unknownStartGracePeriod) {
    Objects.requireNonNull(markerLastModified, "markerLastModified");
    Objects.requireNonNull(unknownStartGracePeriod, "unknownStartGracePeriod");
    Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
    if (processHandle.isEmpty()) {
      return false;
    }
    if (startEpochMillis == UNKNOWN_START_EPOCH_MILLIS) {
      return markerLastModified
          .plus(unknownStartGracePeriod)
          .isAfter(Instant.now(SystemUtcClock.instance()));
    }
    Optional<Instant> liveStartInstant = processHandle.get().info().startInstant();
    return liveStartInstant.map(instant -> instant.toEpochMilli() == startEpochMillis).orElse(true);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SqliteProcessIdentity identity)) {
      return false;
    }
    return pid == identity.pid && startEpochMillis == identity.startEpochMillis;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pid, startEpochMillis);
  }

  private static SqliteProcessIdentity currentIdentity() {
    ProcessHandle currentProcess = ProcessHandle.current();
    long currentStartEpochMillis =
        currentProcess
            .info()
            .startInstant()
            .map(Instant::toEpochMilli)
            .orElse(UNKNOWN_START_EPOCH_MILLIS);
    return new SqliteProcessIdentity(currentProcess.pid(), currentStartEpochMillis);
  }

  private static @Nullable Long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
