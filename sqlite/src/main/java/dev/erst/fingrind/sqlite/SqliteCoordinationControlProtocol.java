package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable geometry and identity rules shared by retained SQLite coordination controls.
 *
 * <p>These rules deliberately contain no I/O or transport selection. Keeping byte ranges, magic
 * headers, and identity bindings separate from native handle lifecycle makes every control
 * transport consume the same protocol without becoming a mixed policy-and-I/O utility.
 */
final class SqliteCoordinationControlProtocol {
  static final long CONTROL_LOCK_BASE = 4_096L;

  private SqliteCoordinationControlProtocol() {}

  /** Returns the first byte available to the slot numbered by this protocol. */
  static long activitySlotPosition(int slot) {
    if (slot < 0) {
      throw new IllegalArgumentException("Coordination activity slot must not be negative.");
    }
    return Math.addExact(CONTROL_LOCK_BASE, slot);
  }

  /** Returns the first byte of the sole full-control maintenance exclusion. */
  static long maintenanceLockPosition() {
    return CONTROL_LOCK_BASE;
  }

  /** Returns the length of the full-control exclusion without crossing the signed long boundary. */
  static long maintenanceLockLength() {
    return Long.MAX_VALUE - CONTROL_LOCK_BASE;
  }

  /** Returns a stable bounded hash for a caller-owned identity description. */
  static String sha256Hex(String identity) {
    return CryptographicPrimitives.sha256HexUtf8(Objects.requireNonNull(identity, "identity"));
  }

  /** Produces immutable bounded header text binding one protocol version to one identity. */
  static byte[] magic(String protocol, String binding) {
    return (Objects.requireNonNull(protocol, "protocol")
            + ":"
            + Objects.requireNonNull(binding, "binding")
            + "\n")
        .getBytes(StandardCharsets.US_ASCII);
  }

  /** Returns the stable binding for a canonical real directory path. */
  static String canonicalDirectoryBinding(Path canonicalDirectory) {
    Path checkedDirectory = Objects.requireNonNull(canonicalDirectory, "canonicalDirectory");
    return sha256Hex(
        "FinGrind-maintenance-directory-v4\u0000" + checkedDirectory.toAbsolutePath().normalize());
  }

  /** Validates one lock range against the immutable header and signed-long bounds. */
  static void requireLockRange(long position, long size) {
    if (position < CONTROL_LOCK_BASE || size <= 0L || size > Long.MAX_VALUE - position) {
      throw new IllegalArgumentException(
          "FinGrind coordination locks must remain wholly after the immutable control header.");
    }
  }

  /** Copies and validates one immutable header before native transport receives it. */
  static byte[] checkedMagic(byte[] magic) {
    byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
    if (checkedMagic.length == 0 || checkedMagic.length >= CONTROL_LOCK_BASE) {
      throw new IllegalArgumentException(
          "Coordination control-file magic must fit wholly inside the immutable header.");
    }
    return checkedMagic;
  }

  /** Returns whether the current supported host uses the Windows control transport. */
  static boolean isWindows() {
    return isWindows(System.getProperty("os.name", ""));
  }

  /** Returns whether one supported operating-system name selects the Windows control transport. */
  static boolean isWindows(String operatingSystemName) {
    return "windows"
        .equals(SqliteHostPlatformDescriptor.supportedOperatingSystemId(operatingSystemName));
  }
}
