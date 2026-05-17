package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Guards the SQLite FFM seam so unsupported Java launches fail before native lookup begins. */
final class SqliteNativeAccessGate {
  private SqliteNativeAccessGate() {}

  static Module runtimeModule() {
    return SqliteNativeAccessGate.class.getModule();
  }

  static boolean isEnabled(Module module) {
    Objects.requireNonNull(module, "module");
    return module.isNativeAccessEnabled();
  }

  static void requireEnabled() {
    requireEnabled(runtimeModule());
  }

  static void requireEnabled(Module module) {
    Objects.requireNonNull(module, "module");
    if (!isEnabled(module)) {
      throw new ManagedSqliteRuntimeUnavailableException(failureMessage(module));
    }
  }

  static String failureMessage(Module module) {
    Objects.requireNonNull(module, "module");
    if (module.isNamed()) {
      return "SQLite native access is disabled for module "
          + module.getName()
          + ". Rerun with "
          + requiredFlag(module)
          + " or use one of FinGrind's supported launchers.";
    }
    return "SQLite native access is disabled for the current unnamed-module Java launch. Rerun with "
        + requiredFlag(module)
        + " or use one of FinGrind's supported launchers.";
  }

  static String requiredFlag(Module module) {
    Objects.requireNonNull(module, "module");
    return "--enable-native-access=" + (module.isNamed() ? module.getName() : "ALL-UNNAMED");
  }
}
