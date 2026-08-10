package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionService;
import java.io.IOException;
import java.nio.file.Path;

/** Creates new owner-only UTF-8 key files for protected FinGrind books. */
public final class SqliteBookKeyFileGenerator {
  static final String GENERATED_ENCODING = "base64url-no-padding";
  static final int GENERATED_ENTROPY_BITS = 256;

  /** Opens the sole publication-transaction authority for one generated key-file request. */
  @FunctionalInterface
  interface PublicationTransactionServiceFactory {
    /** Opens the canonical transaction service without exposing a private stage path. */
    PublicationTransactionService open() throws IOException;
  }

  private SqliteBookKeyFileGenerator() {}

  /** Creates one new key file and returns non-secret metadata about the created artifact. */
  public static GeneratedBookKeyFile generate(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath).requireAccepted();
  }

  /** Creates one new key file and returns the explicit accepted/rejected result. */
  public static ContractDecision<GeneratedBookKeyFile> generateDecision(Path bookKeyFilePath) {
    return generateDecision(bookKeyFilePath, PublicationTransactionPublisher::openCanonical);
  }

  /**
   * Creates one key file through the single transaction owner.
   *
   * <p>This package-visible seam proves the public recovery-only failure contract without
   * substituting an unjournaled filesystem stage or recovery pathname.
   */
  static ContractDecision<GeneratedBookKeyFile> generateDecision(
      Path bookKeyFilePath, PublicationTransactionServiceFactory transactionServiceFactory) {
    return SqliteBookKeyFileGenerationWorkflow.generateDecision(
        bookKeyFilePath, transactionServiceFactory);
  }

  /**
   * Generates a key directly into one fresh maintenance-owned stage without changing its access
   * control list or POSIX permissions by pathname.
   */
  static void generateIntoExistingOwnedStage(Path stagedPath) {
    SqliteBookKeyFileMaterial.generateIntoExistingOwnedStage(stagedPath);
  }
}
