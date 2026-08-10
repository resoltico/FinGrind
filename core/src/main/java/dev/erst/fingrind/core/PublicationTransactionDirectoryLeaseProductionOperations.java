package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Production exact-channel implementation of publication-directory lease operations. */
final class PublicationTransactionDirectoryLeaseProductionOperations
    implements PublicationTransactionDirectoryLeaseOperations {
  static final PublicationTransactionDirectoryLeaseProductionOperations INSTANCE =
      new PublicationTransactionDirectoryLeaseProductionOperations();

  private PublicationTransactionDirectoryLeaseProductionOperations() {}

  @Override
  public String physicalDirectoryIdentity(Path directory) throws IOException {
    return PrivateOutputDirectory.physicalObjectIdentity(
        Objects.requireNonNull(directory, "directory"));
  }

  @Override
  public LeaseControlArtifact openLeaseControlArtifact(Path path) throws IOException {
    return new PrivateArtifact(
        PrivateOutputFile.openOrCreate(Objects.requireNonNull(path, "path")));
  }

  @Override
  public void forceDirectory(Path directory) throws IOException {
    PrivateOutputDirectoryDurability.force(Objects.requireNonNull(directory, "directory"));
  }

  /** Adapts one retained private output channel to the narrow lease-control contract. */
  private static final class PrivateArtifact implements LeaseControlArtifact {
    private final PrivateOutputFile.OpenedFile opened;

    private PrivateArtifact(PrivateOutputFile.OpenedFile opened) {
      this.opened = Objects.requireNonNull(opened, "opened");
    }

    @Override
    public void force() throws IOException {
      opened.force();
    }

    @Override
    public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock() throws IOException {
      return opened.tryExclusiveLock(0L, 1L);
    }

    @Override
    public void close() throws IOException {
      opened.close();
    }
  }
}
