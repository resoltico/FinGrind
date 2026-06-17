package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public managed-SQLite contract catalog for the runtime dependency surface. */
public final class ProtocolManagedSqliteCatalog {
  static final ProtocolManagedSqliteCatalog INSTANCE = new ProtocolManagedSqliteCatalog();

  private ProtocolManagedSqliteCatalog() {}

  /** Returns the canonical minimum SQLite version for the managed runtime surface. */
  public String requiredMinimumSqliteVersion() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.requiredMinimumSqliteVersion();
  }

  /** Returns the canonical SQLite3 Multiple Ciphers version for the managed runtime surface. */
  public String requiredSqlite3mcVersion() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.requiredSqlite3mcVersion();
  }

  /** Returns the canonical SQLite source identifier for the managed runtime surface. */
  public String requiredSqliteSourceId() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.requiredSqliteSourceId();
  }

  /** Returns the canonical required compile options for the managed runtime surface. */
  public List<String> requiredCompileOptions() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.requiredCompileOptions();
  }

  /** Returns compile options that must be absent for the managed runtime surface. */
  public List<String> forbiddenCompileOptions() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.forbiddenCompileOptions();
  }

  /** Returns whether the managed runtime contract requires SQLite3MC secure-memory support. */
  public boolean requiresSecureMemorySupport() {
    return ProtocolCatalogFacts.MANAGED_SQLITE_CONTRACT.requiresSecureMemorySupport();
  }
}
