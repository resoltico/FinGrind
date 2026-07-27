package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable operation-bound identity for one recoverable protected-book pair publication. */
final class SqliteProtectedBookPairPublicationRecord {
  final UUID pairId;
  final Path bookTargetPath;
  final Path secretTargetPath;
  final Path bookStagePath;
  final Path secretStagePath;
  final byte[] bookDigest;
  final byte[] secretDigest;
  final byte @org.jspecify.annotations.Nullable [] replaceTargetDigest;
  final RestoredBookTargetPolicy bookTargetPolicy;
  final ProtectedBookPairPublicationBinding binding;

  /** A durable evidence copy exists, but its stable-storage confirmation is unknown. */
  static final class RecoveryRecordDurabilityUnconfirmedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RecoveryRecordDurabilityUnconfirmedException(Throwable cause) {
      super("Protected-book pair recovery-record durability could not be confirmed.", cause);
    }
  }

  /** Force-confirms one exact evidence file through a nofollow regular-file boundary. */
  @FunctionalInterface
  interface RecoveryRecordFileForcer {
    /** Force-confirms the immutable evidence record at the exact supplied path. */
    void force(Path recordPath) throws IOException;
  }

  /** Creates one immutable evidence hard link from its forced temporary file. */
  @FunctionalInterface
  interface EvidenceLinkCreator {
    /** Creates the immutable evidence link without replacing an existing final name. */
    void create(Path evidencePath, Path temporaryPath) throws IOException;
  }

  SqliteProtectedBookPairPublicationRecord(Components components) {
    Components checkedComponents = Objects.requireNonNull(components, "components");
    this.pairId = Objects.requireNonNull(checkedComponents.pairId, "pairId");
    this.bookTargetPath =
        SqlitePairPublicationRecordIntegrity.normalized(
            checkedComponents.paths.bookTargetPath(), "bookTargetPath");
    this.secretTargetPath =
        SqlitePairPublicationRecordIntegrity.normalized(
            checkedComponents.paths.secretTargetPath(), "secretTargetPath");
    this.bookStagePath =
        SqlitePairPublicationRecordIntegrity.normalized(
            checkedComponents.paths.bookStagePath(), "bookStagePath");
    this.secretStagePath =
        SqlitePairPublicationRecordIntegrity.normalized(
            checkedComponents.paths.secretStagePath(), "secretStagePath");
    this.bookDigest =
        SqlitePairPublicationRecordIntegrity.checkedDigest(
            checkedComponents.digests.bookDigest, "bookDigest");
    this.secretDigest =
        SqlitePairPublicationRecordIntegrity.checkedDigest(
            checkedComponents.digests.secretDigest, "secretDigest");
    this.bookTargetPolicy =
        Objects.requireNonNull(checkedComponents.bookTargetPolicy, "bookTargetPolicy");
    this.replaceTargetDigest =
        switch (this.bookTargetPolicy) {
          case REPLACE_SELECTED ->
              SqlitePairPublicationRecordIntegrity.checkedDigest(
                  Objects.requireNonNull(
                      checkedComponents.digests.replaceTargetDigest, "replaceTargetDigest"),
                  "replaceTargetDigest");
          case REQUIRE_ABSENT -> {
            SqlitePairPublicationRecordIntegrity.requireAbsent(
                checkedComponents.digests.replaceTargetDigest);
            yield null;
          }
        };
    this.binding = Objects.requireNonNull(checkedComponents.binding, "binding");
    SqlitePairPublicationRecordIntegrity.validateDistinctMembers(
        bookTargetPath, secretTargetPath, bookStagePath, secretStagePath);
  }

  /** Values that become the immutable identity of a protected-book pair publication. */
  static final class Components {
    private final UUID pairId;
    private final PairPaths paths;
    private final PairDigests digests;
    private final RestoredBookTargetPolicy bookTargetPolicy;
    private final ProtectedBookPairPublicationBinding binding;

    Components(
        UUID pairId,
        PairPaths paths,
        PairDigests digests,
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookPairPublicationBinding binding) {
      this.pairId = pairId;
      this.paths = paths;
      this.digests = digests;
      this.bookTargetPolicy = bookTargetPolicy;
      this.binding = binding;
    }
  }

  /** The four immutable location claims carried by one pair-publication record. */
  record PairPaths(
      Path bookTargetPath, Path secretTargetPath, Path bookStagePath, Path secretStagePath) {}

  /** Defensive ownership of the three digest inputs that identify a pair publication. */
  static final class PairDigests {
    private final byte[] bookDigest;
    private final byte[] secretDigest;
    private final byte @org.jspecify.annotations.Nullable [] replaceTargetDigest;

    PairDigests(
        byte[] bookDigest,
        byte[] secretDigest,
        byte @org.jspecify.annotations.Nullable [] replaceTargetDigest) {
      this.bookDigest = Objects.requireNonNull(bookDigest, "bookDigest").clone();
      this.secretDigest = Objects.requireNonNull(secretDigest, "secretDigest").clone();
      this.replaceTargetDigest = replaceTargetDigest == null ? null : replaceTargetDigest.clone();
    }
  }

  /**
   * Persists mirrored claim, intent, then recovery evidence before either final member is visible.
   *
   * <p>Claim-only residue is intentionally inert: it cannot authorize final publication and no
   * lifecycle append has occurred at that boundary. Complete claims are nevertheless required
   * before any intent copy exists, so a partial intent or recovery copy blocks every alternate
   * request that shares either target parent.
   */
  static SqliteProtectedBookPairPublicationRecord create(
      Path bookTargetPath,
      Path secretTargetPath,
      Path bookStagePath,
      Path secretStagePath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationBinding binding,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    return create(
        bookTargetPath,
        secretTargetPath,
        bookStagePath,
        secretStagePath,
        bookTargetPolicy,
        binding,
        directoryForcer,
        Files::createLink);
  }

  static SqliteProtectedBookPairPublicationRecord create(
      Path bookTargetPath,
      Path secretTargetPath,
      Path bookStagePath,
      Path secretStagePath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationBinding binding,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    return SqliteProtectedBookPairPublicationEvidenceLifecycle.create(
        bookTargetPath,
        secretTargetPath,
        bookStagePath,
        secretStagePath,
        bookTargetPolicy,
        binding,
        directoryForcer,
        evidenceLinkCreator);
  }

  static SqlitePairPublicationEvidenceScan scanForAdmission(
      Path bookTargetPath, Path secretTargetPath) {
    return SqliteProtectedBookPairPublicationEvidenceScanner.scan(bookTargetPath, secretTargetPath);
  }

  OperationId recoveryOperation() {
    return switch (binding) {
      case ProtectedBookPairPublicationBinding.Backup _ -> OperationId.BACKUP_BOOK;
      case ProtectedBookPairPublicationBinding.Restore _ -> OperationId.RESTORE_BOOK;
      case ProtectedBookPairPublicationBinding.Rekey _ -> OperationId.REKEY_BOOK;
    };
  }

  boolean finalBookMatches() {
    return SqlitePairPublicationRecordIntegrity.regularFileMatches(bookTargetPath, bookDigest);
  }

  boolean finalSecretMatches() {
    return SqlitePairPublicationRecordIntegrity.regularFileMatches(secretTargetPath, secretDigest);
  }

  boolean stagedBookMatches() {
    return SqlitePairPublicationRecordIntegrity.regularFileMatches(bookStagePath, bookDigest);
  }

  boolean stagedSecretMatches() {
    return SqlitePairPublicationRecordIntegrity.regularFileMatches(secretStagePath, secretDigest);
  }

  boolean replaceTargetMatches() {
    return replaceTargetDigest != null
        && SqlitePairPublicationRecordIntegrity.regularFileMatches(
            bookTargetPath, replaceTargetDigest);
  }

  List<Path> evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind kind) {
    return SqliteProtectedBookPairPublicationEvidencePaths.paths(this, kind);
  }

  boolean sameImmutableRecord(SqliteProtectedBookPairPublicationRecord candidate) {
    SqliteProtectedBookPairPublicationRecord checkedCandidate =
        Objects.requireNonNull(candidate, "candidate");
    return pairId.equals(checkedCandidate.pairId)
        && SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
                this, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .equals(
                SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
                    checkedCandidate, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
  }

  static byte[] digest(java.io.InputStream input, String name) throws IOException {
    return SqlitePairPublicationRecordIntegrity.digest(input, name);
  }
}
