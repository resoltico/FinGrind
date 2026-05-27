package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public runtime and distribution catalog for FinGrind delivery surfaces. */
public final class ProtocolRuntimeCatalog {
  static final ProtocolRuntimeCatalog INSTANCE = new ProtocolRuntimeCatalog();

  private ProtocolRuntimeCatalog() {}

  /** Returns the canonical storage engine identifiers. */
  public List<StorageEngine> storageEngines() {
    return ProtocolCatalogFacts.storageEngines();
  }

  /** Returns the canonical storage-driver identifier. */
  public StorageDriver storageDriver() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().storageDriver();
  }

  /** Returns the canonical storage-engine identifier. */
  public StorageEngine storageEngine() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().storageEngine();
  }

  /** Returns the canonical book-protection mode. */
  public BookProtectionMode bookProtectionMode() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().bookProtectionMode();
  }

  /** Returns the canonical protected-book format contract. */
  public ProtectedBookFormatContract protectedBookFormat() {
    return ProtocolCatalogFacts.protectedBookFormatContract();
  }

  /** Returns the canonical default book cipher. */
  public BookCipher defaultBookCipher() {
    return protectedBookFormat().cipher();
  }

  /** Returns the canonical SQLite library mode. */
  public SqliteLibraryMode sqliteLibraryMode() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().sqliteLibraryMode();
  }

  /** Returns the canonical bundle-home system property name. */
  public String sqliteBundleHomeSystemProperty() {
    return ProtocolCatalogFacts.runtimeSurfaceContract().sqliteBundleHomeSystemProperty();
  }
}
