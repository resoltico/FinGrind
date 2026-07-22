package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Lifecycle seam for opening, protecting, backing up, and rolling back books. */
interface CliBookLifecycleWorkflow {
  /** Opens or initializes one protected book through the selected access route. */
  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command);

  /** Replaces the passphrase material protecting one existing book. */
  ContractDecision<RekeyBookResult> rekeyBook(BookAccess bookAccess, Path newBookKeyFilePath);

  /** Creates a backup copy plus its companion key artifact. */
  ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath, UUID backupId);

  /** Restores one protected book from a backup artifact set. */
  ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      List<AttestationCredentialSource> attestationCredentialSources);

  /** Appends one public credential-registry or authorization-policy mutation. */
  ContractDecision<AttestationRegistryMutationResult> mutateRegistry(
      BookAccess bookAccess, AttestationRegistryMutation mutation);
}
