package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Exact private-output operations used to acquire one cooperating publication-directory lease. */
interface PublicationTransactionDirectoryLeaseOperations {
  /** Returns the current stable physical identity of an admitted private output directory. */
  String physicalDirectoryIdentity(Path directory) throws IOException;

  /** Opens or creates the private no-replace lease-control artifact in the supplied directory. */
  LeaseControlArtifact openLeaseControlArtifact(Path path) throws IOException;

  /** Persists a prior lease-control name mutation in the supplied directory. */
  void forceDirectory(Path directory) throws IOException;

  /** One retained exact private lease-control artifact. */
  interface LeaseControlArtifact extends AutoCloseable {
    /** Force-confirms the artifact before its directory entry is force-confirmed. */
    void force() throws IOException;

    /** Attempts one exclusive cooperating-process lease. */
    PrivateOutputFile.@Nullable HeldLock tryExclusiveLock() throws IOException;

    /** Releases the retained private artifact handle. */
    @Override
    void close() throws IOException;
  }
}
