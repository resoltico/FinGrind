package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies exact-channel retained-stage creation rather than any delete-based cleanup model. */
class ArtifactPublicationStagesTest {
  private static final Set<PosixFilePermission> OWNER_ONLY =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void createAndWriteCreatesOneOwnerOnlyStageWithItsExactBytes() throws IOException {
    assumePosix(temporaryDirectory);
    byte[] expected = new byte[] {1, 2, 3, 4};

    Path stage =
        ArtifactPublicationStages.createAndWrite(
            temporaryDirectory, ".receipt-", ".fgar", expected);

    assertArrayEquals(expected, Files.readAllBytes(stage));
    assertEqualsOwnerOnlyPermissions(stage);
  }

  @Test
  void createAndCopyCreatesOneOwnerOnlyStageFromANonSymlinkSource() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    byte[] expected = new byte[] {9, 8, 7};
    Files.write(source, expected);

    Path stage =
        ArtifactPublicationStages.createAndCopy(temporaryDirectory, ".restore-", ".sqlite", source);

    assertArrayEquals(expected, Files.readAllBytes(stage));
    assertEqualsOwnerOnlyPermissions(stage);
  }

  @Test
  void createAndCopyRejectsASymlinkSourceBeforeCreatingAStage() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {1});
    Path symlink = temporaryDirectory.resolve("source-link.sqlite");
    try {
      Files.createSymbolicLink(symlink, source.getFileName());
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }

    assertThrows(
        IOException.class,
        () ->
            ArtifactPublicationStages.createAndCopy(
                temporaryDirectory, ".restore-", ".sqlite", symlink));
    try (var children = Files.list(temporaryDirectory)) {
      assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith(".restore-")));
    }
  }

  @Test
  void openNoFollowSourceTranslatesUnsupportedNofollowPrimitivesToIOException() {
    UnsupportedOperationException rejection =
        new UnsupportedOperationException("simulated unsupported nofollow source primitive");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                ArtifactPublicationStages.openNoFollowSource(
                    temporaryDirectory.resolve("source.bin"),
                    ignored -> {
                      throw rejection;
                    }));

    assertEquals(
        "The selected filesystem cannot enforce nofollow access for an artifact-publication source.",
        failure.getMessage());
    assertSame(rejection, failure.getCause());
  }

  @Test
  void openNoFollowSourceTranslatesIllegalArgumentNofollowPrimitivesToIOException() {
    IllegalArgumentException rejection =
        new IllegalArgumentException("simulated invalid nofollow source primitive");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                ArtifactPublicationStages.openNoFollowSource(
                    temporaryDirectory.resolve("source.bin"),
                    ignored -> {
                      throw rejection;
                    }));

    assertEquals(
        "The selected filesystem cannot enforce nofollow access for an artifact-publication source.",
        failure.getMessage());
    assertSame(rejection, failure.getCause());
  }

  @Test
  void createAndWriteRetainsTheExactStageWhenDestinationCloseFailsAfterWriting()
      throws IOException {
    assumePosix(temporaryDirectory);
    byte[] expected = new byte[] {1, 3, 5, 7};

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    expected,
                    stagedPath ->
                        PrivateOutputFile.wrap(
                            new CloseFailingFileChannel(
                                openNewTestStage(stagedPath), "destination close failed"))));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("destination close failed", primaryFailure.getMessage());
    Path retainedStage = exception.retainedStage().retainedStagePath();
    assertTrue(Files.exists(retainedStage, LinkOption.NOFOLLOW_LINKS));
    assertArrayEquals(expected, Files.readAllBytes(retainedStage));
  }

  @Test
  void createAndCopyRetainsTheExactStageWhenSourceCloseFailsAfterCopying() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    byte[] expected = new byte[] {2, 4, 6, 8};
    Files.write(source, expected);

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    ArtifactPublicationStagesTest::openNewTestPrivateStage,
                    sourcePath ->
                        new CloseFailingFileChannel(
                            FileChannel.open(sourcePath, StandardOpenOption.READ),
                            "source close failed")));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("source close failed", primaryFailure.getMessage());
    Path retainedStage = exception.retainedStage().retainedStagePath();
    assertTrue(Files.exists(retainedStage, LinkOption.NOFOLLOW_LINKS));
    assertArrayEquals(expected, Files.readAllBytes(retainedStage));
  }

  @Test
  void createAndCopyRetainsTheExactStageWhenTheSourceChannelCannotMakeReadProgress()
      throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {2, 4, 6, 8});

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    ArtifactPublicationStagesTest::openNewTestPrivateStage,
                    sourcePath ->
                        new ZeroReadFileChannel(
                            FileChannel.open(sourcePath, StandardOpenOption.READ),
                            "source close failed")));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals(
        "Failed to read the complete artifact-publication source.", primaryFailure.getMessage());
    assertTrue(
        Files.exists(exception.retainedStage().retainedStagePath(), LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void createAndCopyRetainsTheExactStageWhenTheDestinationChannelCannotMakeWriteProgress()
      throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {2, 4, 6, 8});

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    stagedPath ->
                        PrivateOutputFile.wrap(
                            new ZeroWriteFileChannel(
                                openNewTestStage(stagedPath), "destination close failed")),
                    sourcePath -> FileChannel.open(sourcePath, StandardOpenOption.READ)));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals(
        "Failed to write the complete private artifact stage.", primaryFailure.getMessage());
    assertTrue(
        Files.exists(exception.retainedStage().retainedStagePath(), LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void createAndWriteRetainsTheCandidateWhenStageOpeningFailsAfterMaterializingIt()
      throws IOException {
    assumePosix(temporaryDirectory);

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath -> {
                      Files.createFile(stagedPath);
                      throw new IOException("stage opening failed after materialization");
                    }));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals("stage opening failed after materialization", primaryFailure.getMessage());
    assertTrue(
        Files.exists(exception.retainedStage().retainedStagePath(), LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void createAndCopyPreservesAnOpeningFailureThatOccursBeforeAnyStageExists() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {2, 4, 6, 8});

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    ArtifactPublicationStagesTest::openNewTestPrivateStage,
                    ignored -> {
                      throw new IOException("source opening failed");
                    }));

    assertEquals("source opening failed", exception.getMessage());
  }

  @Test
  void createAndWriteRefusesToReuseCandidatesAfterEveryFreshNameCollides() throws IOException {
    assumePosix(temporaryDirectory);

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath -> {
                      throw new java.nio.file.FileAlreadyExistsException(stagedPath.toString());
                    }));

    assertEquals(
        "FinGrind could not allocate a fresh private artifact stage.", exception.getMessage());
  }

  @Test
  void createAndWritePropagatesAnOpeningFailureWhenNoStageWasMaterialized() throws IOException {
    assumePosix(temporaryDirectory);

    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath -> {
                      throw new IOException("stage opening failed");
                    }));

    assertEquals("stage opening failed", exception.getMessage());
  }

  @Test
  void createAndWriteRetainsTheStageWhenTheStageWriterFailsFatally() throws IOException {
    assumePosix(temporaryDirectory);

    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath ->
                        PrivateOutputFile.wrap(
                            new FatalForceFileChannel(openNewTestStage(stagedPath)))));

    assertRetainedStageSuppressed(failure);
  }

  @Test
  void createAndCopyRetainsTheCompletedStageWhenSourceCloseFailsFatally() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {2, 4, 6, 8});

    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    ArtifactPublicationStagesTest::openNewTestPrivateStage,
                    sourcePath ->
                        new CloseFailingFileChannel(
                            FileChannel.open(sourcePath, StandardOpenOption.READ),
                            new AssertionError("source close failed"))));

    assertRetainedStageSuppressed(failure);
  }

  @Test
  void createAndWriteLeavesNoRetainedEvidenceWhenOpeningFailsFatallyBeforeMaterialization()
      throws IOException {
    assumePosix(temporaryDirectory);

    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath -> {
                      throw new AssertionError("stage opening failed");
                    }));

    assertEquals("stage opening failed", failure.getMessage());
    assertEquals(0, failure.getSuppressed().length);
  }

  @Test
  void createAndCopyPropagatesAFatalOpeningFailureBeforeCompletingAStage() throws IOException {
    assumePosix(temporaryDirectory);
    Path source = temporaryDirectory.resolve("source.sqlite");
    Files.write(source, new byte[] {2, 4, 6, 8});

    AssertionError failure =
        assertThrows(
            AssertionError.class,
            () ->
                ArtifactPublicationStages.createAndCopy(
                    temporaryDirectory,
                    ".restore-",
                    ".sqlite",
                    source,
                    stagedPath -> {
                      throw new AssertionError("stage opening failed");
                    },
                    sourcePath -> FileChannel.open(sourcePath, StandardOpenOption.READ)));

    assertEquals("stage opening failed", failure.getMessage());
  }

  @Test
  void createAndWriteRetainsTheExactStageWhenWritingCannotMakeProgress() throws IOException {
    assumePosix(temporaryDirectory);

    ArtifactPublicationRetainedStageException exception =
        assertThrows(
            ArtifactPublicationRetainedStageException.class,
            () ->
                ArtifactPublicationStages.createAndWrite(
                    temporaryDirectory,
                    ".receipt-",
                    ".fgar",
                    new byte[] {9},
                    stagedPath ->
                        PrivateOutputFile.wrap(
                            new ZeroWriteFileChannel(
                                openNewTestStage(stagedPath), "destination close failed"))));

    IOException primaryFailure = assertInstanceOf(IOException.class, exception.getCause());
    assertEquals(
        "Failed to write the complete private artifact stage.", primaryFailure.getMessage());
  }

  @Test
  void stageNamePartsMustBeSingleNonemptyPathComponents() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtifactPublicationStages.createAndWrite(temporaryDirectory, "", ".fgar", new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtifactPublicationStages.createAndWrite(
                temporaryDirectory, ".receipt-", "nested\\stage", new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ArtifactPublicationStages.createAndWrite(
                temporaryDirectory, ".receipt-", "nested/stage", new byte[0]));
  }

  private static void assumePosix(Path directory) throws IOException {
    assumeTrue(
        Files.getFileStore(directory).supportsFileAttributeView("posix"),
        "The filesystem does not support exact POSIX owner-only creation.");
  }

  private static void assertEqualsOwnerOnlyPermissions(Path path) throws IOException {
    org.junit.jupiter.api.Assertions.assertEquals(OWNER_ONLY, Files.getPosixFilePermissions(path));
  }

  private static FileChannel openNewTestStage(Path stagedPath) throws IOException {
    return FileChannel.open(
        stagedPath,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE);
  }

  private static PrivateOutputFile.OpenedFile openNewTestPrivateStage(Path stagedPath)
      throws IOException {
    return PrivateOutputFile.wrap(openNewTestStage(stagedPath));
  }

  private static void assertRetainedStageSuppressed(AssertionError failure) {
    assertTrue(
        java.util.Arrays.stream(failure.getSuppressed())
            .anyMatch(ArtifactPublicationRetainedStageException.class::isInstance));
  }

  /** Delegates channel operations while making close report the supplied failure. */
  private static class CloseFailingFileChannel extends FileChannel {
    private final FileChannel delegate;
    private final Throwable closeFailure;

    private CloseFailingFileChannel(FileChannel delegate, String closeFailureMessage) {
      this(delegate, new IOException(closeFailureMessage));
    }

    private CloseFailingFileChannel(FileChannel delegate, Throwable closeFailure) {
      this.delegate = delegate;
      this.closeFailure = closeFailure;
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
      return delegate.read(destination);
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
      return delegate.read(destinations, offset, length);
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
      return delegate.write(source);
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
      return delegate.write(sources, offset, length);
    }

    @Override
    public long position() throws IOException {
      return delegate.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
      delegate.position(newPosition);
      return this;
    }

    @Override
    public long size() throws IOException {
      return delegate.size();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
      delegate.truncate(size);
      return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
      delegate.force(metaData);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
        throws IOException {
      return delegate.transferTo(position, count, target);
    }

    @Override
    public long transferFrom(ReadableByteChannel source, long position, long count)
        throws IOException {
      return delegate.transferFrom(source, position, count);
    }

    @Override
    public int read(ByteBuffer destination, long position) throws IOException {
      return delegate.read(destination, position);
    }

    @Override
    public int write(ByteBuffer source, long position) throws IOException {
      return delegate.write(source, position);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
      return delegate.map(mode, position, size);
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
      return delegate.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
      return delegate.tryLock(position, size, shared);
    }

    @Override
    protected void implCloseChannel() throws IOException {
      try {
        delegate.close();
      } catch (IOException delegateFailure) {
        closeFailure.addSuppressed(delegateFailure);
      }
      if (closeFailure instanceof IOException failure) {
        throw failure;
      }
      throw (Error) closeFailure;
    }
  }

  /** Simulates a channel that cannot make read progress. */
  private static final class ZeroReadFileChannel extends CloseFailingFileChannel {
    private ZeroReadFileChannel(FileChannel delegate, String closeFailureMessage) {
      super(delegate, closeFailureMessage);
    }

    @Override
    public int read(ByteBuffer destination) {
      return 0;
    }
  }

  /** Simulates a channel that cannot make write progress. */
  private static final class ZeroWriteFileChannel extends CloseFailingFileChannel {
    private ZeroWriteFileChannel(FileChannel delegate, String closeFailureMessage) {
      super(delegate, closeFailureMessage);
    }

    @Override
    public int write(ByteBuffer source) {
      return 0;
    }
  }

  /** Simulates a channel whose final force is an unrecoverable JVM failure. */
  private static final class FatalForceFileChannel extends CloseFailingFileChannel {
    private FatalForceFileChannel(FileChannel delegate) {
      super(delegate, "destination close failed");
    }

    @Override
    public void force(boolean metaData) {
      throw new AssertionError("destination force failed");
    }
  }
}
