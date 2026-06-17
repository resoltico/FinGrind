package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Public runtime and distribution catalog for FinGrind delivery surfaces. */
public final class ProtocolRuntimeCatalog {
  static final ProtocolRuntimeCatalog INSTANCE = new ProtocolRuntimeCatalog();

  private ProtocolRuntimeCatalog() {}

  /** Returns the canonical storage engine identifiers. */
  public List<StorageEngine> storageEngines() {
    return ProtocolCatalogFacts.STORAGE_ENGINES;
  }

  /** Returns the canonical storage-driver identifier. */
  public StorageDriver storageDriver() {
    return ProtocolCatalogFacts.RUNTIME_SURFACE_CONTRACT.storageDriver();
  }

  /** Returns the canonical storage-engine identifier. */
  public StorageEngine storageEngine() {
    return ProtocolCatalogFacts.RUNTIME_SURFACE_CONTRACT.storageEngine();
  }

  /** Returns the canonical book-protection mode. */
  public BookProtectionMode bookProtectionMode() {
    return ProtocolCatalogFacts.RUNTIME_SURFACE_CONTRACT.bookProtectionMode();
  }

  /** Returns the canonical protected-book format contract. */
  public ProtectedBookFormatContract protectedBookFormat() {
    return ProtocolCatalogFacts.PROTECTED_BOOK_FORMAT_CONTRACT;
  }

  /** Returns the canonical default book cipher. */
  public BookCipher defaultBookCipher() {
    return protectedBookFormat().cipher();
  }

  /** Returns the canonical SQLite library mode. */
  public SqliteLibraryMode sqliteLibraryMode() {
    return ProtocolCatalogFacts.RUNTIME_SURFACE_CONTRACT.sqliteLibraryMode();
  }

  /** Returns the canonical bundle-home system property name. */
  public String sqliteBundleHomeSystemProperty() {
    return ProtocolCatalogFacts.RUNTIME_SURFACE_CONTRACT.sqliteBundleHomeSystemProperty();
  }
}
