package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PrivateOutputDirectoryDurability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;

  /** Injectable directory-force boundary for the one post-link durability proof. */
  @FunctionalInterface
  interface ParentDirectoryForcer {
    /** Force-confirms the final-name mutation in the exact selected parent directory. */
    void force(Path parentDirectory) throws IOException;
  }

  /** Injectable private-stage creation boundary for retained-stage failure evidence. */
  @FunctionalInterface
  interface RetainedStageCreator {
    /** Creates and returns the exact retained stage for one normalized final key-file path. */
    ArtifactPublicationRetention create(Path normalizedBookKeyFilePath, byte[] encodedPassphrase);
  }

  /** Injectable retained-witness acquisition boundary for publication-capability fault tests. */
  @FunctionalInterface
  interface PublicationCapabilityWitnessAcquirer {
    /** Acquires the exact no-replace publication witness for one normalized key-file target. */
    SqlitePublicationCapabilityWitness.Set acquire(Path normalizedBookKeyFilePath)
        throws IOException;
  }

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedBookKeyFile generate(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath).requireAccepted();
  }

  /** Creates one new key file and returns the explicit accepted/rejected result. */
  public static ContractDecision<GeneratedBookKeyFile> generateDecision(Path bookKeyFilePath) {
    return generateDecision(
        bookKeyFilePath, Files::createLink, PrivateOutputDirectoryDurability::force);
  }

  /**
   * Creates one key file through explicit final-link and directory-durability boundaries.
   *
   * <p>This package-visible overload exists so fault tests can prove that an uncertain final link
   * or an unconfirmed directory force retains and reports the exact stage rather than deleting it.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer) {
    return generateDecision(
        bookKeyFilePath,
        finalLinkCreator,
        parentDirectoryForcer,
        SqliteBookKeyFileMaterial::createRetainedStage);
  }

  /**
   * Creates one key file through explicit final-link, directory-durability, and stage boundaries.
   *
   * <p>The package-visible stage creator keeps retained-stage failure semantics independently
   * executable: a failed publication must report the exact already-materialized private stage.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer,
      RetainedStageCreator retainedStageCreator) {
    return generateDecision(
        bookKeyFilePath,
        finalLinkCreator,
        parentDirectoryForcer,
        retainedStageCreator,
        SqliteBookKeyFilePublicationWorkflow::acquirePublicationCapabilityWitness);
  }

  /**
   * Creates one key file with an explicit retained-witness acquisition boundary.
   *
   * <p>The package-visible acquisition seam proves that storage capability failures are classified
   * before secret material is staged, and that only recognized primitive refusals become caller
   * path rejections.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator finalLinkCreator,
      ParentDirectoryForcer parentDirectoryForcer,
      RetainedStageCreator retainedStageCreator,
      PublicationCapabilityWitnessAcquirer capabilityWitnessAcquirer) {
    return SqliteBookKeyFileGenerationWorkflow.generateDecision(
        bookKeyFilePath,
        finalLinkCreator,
        parentDirectoryForcer,
        retainedStageCreator,
        capabilityWitnessAcquirer);
  }

  /**
   * Generates a key directly into one fresh maintenance-owned stage without changing its access
   * control list or POSIX permissions by pathname.
   */
  static void generateIntoExistingOwnedStage(Path stagedPath) {
    SqliteBookKeyFileMaterial.generateIntoExistingOwnedStage(stagedPath);
  }
}
