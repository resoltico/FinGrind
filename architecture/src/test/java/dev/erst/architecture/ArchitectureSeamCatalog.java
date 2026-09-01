package dev.erst.architecture;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Canonical imported-class ownership for FinGrind's architecture rules. */
@NullMarked
final class ArchitectureSeamCatalog {
  static final String PRODUCTION_PACKAGE = "dev.erst.fingrind";
  static final String PUBLIC_PATH_HINT = "dev.erst.fingrind.contract.runtime.PublicPathHint";
  static final Set<String> CRYPTOGRAPHIC_PRIMITIVE_SEAM =
      Set.of(
          "dev.erst.fingrind.core.CryptographicPrimitives",
          "dev.erst.fingrind.core.CryptographicChannelDigest",
          "dev.erst.fingrind.core.attestation.AttestationEd25519",
          "dev.erst.fingrind.core.attestation.AttestationFilePkcs8Custodian");
  static final Set<String> PRIVATE_OUTPUT_DIRECTORY_NATIVE_INTEROP_SEAM =
      Set.of(
          "dev.erst.fingrind.core.PrivateOutputDirectoryFfmTransport",
          "dev.erst.fingrind.core.PrivateOutputDirectoryPlatformSpec");
  static final String PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT =
      "dev.erst.fingrind.core.PrivateOutputDirectoryFfmTransport";
  static final String WINDOWS_PRIVATE_OUTPUT_FILE_NATIVE_INTEROP_PREFIX =
      "dev.erst.fingrind.core.WindowsPrivateOutputFile";
  static final String WINDOWS_CURRENT_TOKEN_ACL_PRINCIPAL_MATCHER =
      "dev.erst.fingrind.core.WindowsCurrentTokenAclPrincipalMatcher";
  static final String WINDOWS_TRUSTED_ACL_PRINCIPAL_MATCHER =
      "dev.erst.fingrind.core.WindowsTrustedAclPrincipalMatcher";
  static final String WINDOWS_PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT =
      "dev.erst.fingrind.core.WindowsPrivateOutputDirectoryFfmTransport";
  static final String ATTESTATION_OPERATION_KIND =
      "dev.erst.fingrind.core.attestation.AttestationOperationKind";
  static final String ATTESTATION_EVIDENCE_STORE =
      "dev.erst.fingrind.sqlite.SqliteAttestationEvidenceStore";
  static final String PRIVATE_OUTPUT_DIRECTORY_DURABILITY =
      "dev.erst.fingrind.core.PrivateOutputDirectoryDurability";
  static final String PUBLICATION_TRANSACTION_SERVICE =
      "dev.erst.fingrind.core.PublicationTransactionService";
  static final String PUBLICATION_TRANSACTION_COMMITTER =
      "dev.erst.fingrind.core.PublicationTransactionCommitter";
  static final String PUBLICATION_TRANSACTION_RUNTIME =
      "dev.erst.fingrind.core.PublicationTransactionRuntime";
  static final String PUBLICATION_TRANSACTION_DIRECTORY_DURABILITY =
      "dev.erst.fingrind.core.PublicationTransactionDirectoryDurability";
  static final String PUBLICATION_TRANSACTION_RUNTIME_ENVIRONMENT =
      "dev.erst.fingrind.core.PublicationTransactionRuntimeEnvironment";
  static final String PUBLICATION_TRANSACTION_PAIR =
      "dev.erst.fingrind.sqlite.SqlitePublicationTransactionPair";
  static final String JOURNALED_STAGED_BACKUP_PAIR =
      "dev.erst.fingrind.sqlite.SqliteJournaledStagedBackupPair";
  static final String JOURNALED_STAGED_RESTORED_BOOK_PAIR =
      "dev.erst.fingrind.sqlite.SqliteJournaledStagedRestoredBookPair";
  static final String RUNTIME_CLOCK_SEAM = "dev.erst.fingrind.core.SystemUtcClock";
  static final Set<String> RUNTIME_IO_SEAM =
      Set.of(
          "dev.erst.fingrind.cli.App",
          "dev.erst.fingrind.cli.CliRuntimeEnvironment",
          "dev.erst.fingrind.cli.CliPromptingConsoles",
          "dev.erst.fingrind.cli.LauncherInvocationArguments",
          PUBLICATION_TRANSACTION_RUNTIME_ENVIRONMENT);
  static final Set<String> MUTATION_ATTESTATION_BOUNDARIES =
      Set.of(
          "dev.erst.fingrind.sqlite.SqliteStoreBookOpeningOperations",
          "dev.erst.fingrind.sqlite.SqliteStoreAdministrationMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteStoreAccountRegistryMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteStorePostingMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteClosePostingPersistence",
          "dev.erst.fingrind.sqlite.SqlitePlanExecutionCapabilityView",
          "dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore");
  static final Set<String> TYPED_OPERATION_CATALOG_BOUNDARIES =
      Set.of(
          "dev.erst.fingrind.sqlite.SqliteStoreAdministrationMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteStoreAccountRegistryMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteStorePostingMutationOperations",
          "dev.erst.fingrind.sqlite.SqliteClosePostingPersistence",
          "dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore");
  static final Set<String> DURABLE_MUTATION_WRITERS =
      Set.of(
          "dev.erst.fingrind.sqlite.SqliteMutationWriter",
          "dev.erst.fingrind.sqlite.SqliteAccountRegistryMutationWriter",
          "dev.erst.fingrind.sqlite.SqliteAuditEventWriter",
          "dev.erst.fingrind.sqlite.SqliteAccrualCutoffWriter");
  static final Set<String> DURABLE_MUTATION_WRITER_HELPERS =
      Set.of(
          "dev.erst.fingrind.sqlite.SqliteBookIntegrityVerifier",
          "dev.erst.fingrind.sqlite.SqliteAcceptedPostingPersistence");
  static final Set<String> PREFIX_ARCHITECTURE_SEAMS =
      Set.of(WINDOWS_PRIVATE_OUTPUT_FILE_NATIVE_INTEROP_PREFIX);

  private ArchitectureSeamCatalog() {}

  static Set<String> exactArchitectureClasses() {
    Set<String> classNames = new LinkedHashSet<>();
    classNames.add(PUBLIC_PATH_HINT);
    classNames.addAll(CRYPTOGRAPHIC_PRIMITIVE_SEAM);
    classNames.addAll(PRIVATE_OUTPUT_DIRECTORY_NATIVE_INTEROP_SEAM);
    classNames.addAll(
        Set.of(
            WINDOWS_CURRENT_TOKEN_ACL_PRINCIPAL_MATCHER,
            WINDOWS_TRUSTED_ACL_PRINCIPAL_MATCHER,
            WINDOWS_PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT,
            ATTESTATION_OPERATION_KIND,
            ATTESTATION_EVIDENCE_STORE,
            PRIVATE_OUTPUT_DIRECTORY_DURABILITY,
            PUBLICATION_TRANSACTION_SERVICE,
            PUBLICATION_TRANSACTION_COMMITTER,
            PUBLICATION_TRANSACTION_RUNTIME,
            PUBLICATION_TRANSACTION_DIRECTORY_DURABILITY,
            PUBLICATION_TRANSACTION_RUNTIME_ENVIRONMENT,
            PUBLICATION_TRANSACTION_PAIR,
            JOURNALED_STAGED_BACKUP_PAIR,
            JOURNALED_STAGED_RESTORED_BOOK_PAIR,
            RUNTIME_CLOCK_SEAM));
    classNames.addAll(RUNTIME_IO_SEAM);
    classNames.addAll(MUTATION_ATTESTATION_BOUNDARIES);
    classNames.addAll(TYPED_OPERATION_CATALOG_BOUNDARIES);
    classNames.addAll(DURABLE_MUTATION_WRITERS);
    classNames.addAll(DURABLE_MUTATION_WRITER_HELPERS);
    return Set.copyOf(classNames);
  }
}
