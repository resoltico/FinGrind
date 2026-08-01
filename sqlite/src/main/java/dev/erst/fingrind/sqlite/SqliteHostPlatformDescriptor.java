package dev.erst.fingrind.sqlite;

import java.util.Locale;

/** Host-platform naming and published managed-library filename conventions. */
final class SqliteHostPlatformDescriptor {
  private SqliteHostPlatformDescriptor() {}

  static String supportedNativeLibraryFileName() {
    return supportedNativeLibraryFileName(
        supportedOperatingSystemId(System.getProperty("os.name", "")),
        System.getProperty("os.name"));
  }

  static String supportedNativeLibraryFileName(String operatingSystemId, String detectedOsName) {
    if ("macos".equals(operatingSystemId)) {
      return "libsqlite3.dylib";
    }
    if ("linux".equals(operatingSystemId)) {
      return "libsqlite3.so.0";
    }
    if ("windows".equals(operatingSystemId)) {
      return "sqlite3.dll";
    }
    throw new ManagedSqliteRuntimeUnavailableException(
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + detectedOsName);
  }

  static String supportedHostClassifier() {
    return supportedHostClassifier(
        System.getProperty("os.name", ""), System.getProperty("os.arch", "unknown"));
  }

  static String supportedHostClassifier(String operatingSystemName, String architectureName) {
    return supportedOperatingSystemId(operatingSystemName)
        + "-"
        + supportedArchitectureId(architectureName);
  }

  static String supportedOperatingSystemId(String operatingSystemName) {
    String operatingSystem = operatingSystemName.toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "macos";
    }
    if (operatingSystem.contains("linux")) {
      return "linux";
    }
    if (operatingSystem.contains("windows")) {
      return "windows";
    }
    throw new ManagedSqliteRuntimeUnavailableException(
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + operatingSystemName);
  }

  static String supportedArchitectureId(String architectureName) {
    String architecture = architectureName.toLowerCase(Locale.ROOT);
    return switch (architecture) {
      case "arm64", "aarch64" -> "aarch64";
      case "amd64", "x86_64", "x64" -> "x86_64";
      default -> architecture.replaceAll("[^a-z0-9]+", "-");
    };
  }
}
