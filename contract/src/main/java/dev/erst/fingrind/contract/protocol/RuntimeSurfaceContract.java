package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Protocol-owned runtime and storage facts that span Java code, shell checks, and bundles. */
record RuntimeSurfaceContract(
    RuntimeDistribution directJavaRuntimeDistribution,
    RuntimeDistribution sourceCheckoutRuntimeDistribution,
    RuntimeDistribution containerRuntimeDistribution,
    RuntimeDistribution bundleRuntimeDistribution,
    PublicCliDistribution publicCliDistribution,
    StorageDriver storageDriver,
    StorageEngine storageEngine,
    BookProtectionMode bookProtectionMode,
    BookCipher defaultBookCipher,
    SqliteLibraryMode sqliteLibraryMode,
    String sqliteLibraryEnvironmentVariable,
    String sqliteBundleHomeSystemProperty) {
  RuntimeSurfaceContract {
    directJavaRuntimeDistribution =
        ContractDescriptorValidation.requireValue(
            directJavaRuntimeDistribution, "directJavaRuntimeDistribution");
    sourceCheckoutRuntimeDistribution =
        ContractDescriptorValidation.requireValue(
            sourceCheckoutRuntimeDistribution, "sourceCheckoutRuntimeDistribution");
    containerRuntimeDistribution =
        ContractDescriptorValidation.requireValue(
            containerRuntimeDistribution, "containerRuntimeDistribution");
    bundleRuntimeDistribution =
        ContractDescriptorValidation.requireValue(
            bundleRuntimeDistribution, "bundleRuntimeDistribution");
    publicCliDistribution =
        ContractDescriptorValidation.requireValue(publicCliDistribution, "publicCliDistribution");
    storageDriver = ContractDescriptorValidation.requireValue(storageDriver, "storageDriver");
    storageEngine = ContractDescriptorValidation.requireValue(storageEngine, "storageEngine");
    bookProtectionMode =
        ContractDescriptorValidation.requireValue(bookProtectionMode, "bookProtectionMode");
    defaultBookCipher =
        ContractDescriptorValidation.requireValue(defaultBookCipher, "defaultBookCipher");
    sqliteLibraryMode =
        ContractDescriptorValidation.requireValue(sqliteLibraryMode, "sqliteLibraryMode");
    sqliteLibraryEnvironmentVariable =
        ContractDescriptorValidation.requireText(
            sqliteLibraryEnvironmentVariable, "sqliteLibraryEnvironmentVariable");
    sqliteBundleHomeSystemProperty =
        ContractDescriptorValidation.requireText(
            sqliteBundleHomeSystemProperty, "sqliteBundleHomeSystemProperty");
  }
}
