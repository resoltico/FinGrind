package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Test-only adapter for exercising retained-output consumers with controlled NIO channels. */
final class SqliteTestPrivateOutputFile implements PrivateOutputFile.OpenedFile {
  private final FileChannel channel;
  private final boolean created;

  private SqliteTestPrivateOutputFile(FileChannel channel, boolean created) {
    this.channel = Objects.requireNonNull(channel, "channel");
    this.created = created;
  }

  static SqliteTestPrivateOutputFile wrap(FileChannel channel) {
    return new SqliteTestPrivateOutputFile(channel, false);
  }

  @Override
  public boolean created() {
    return created;
  }

  @Override
  public int read(ByteBuffer destination) throws IOException {
    return channel.read(destination);
  }

  @Override
  public int write(ByteBuffer source) throws IOException {
    return channel.write(source);
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public long size() throws IOException {
    return channel.size();
  }

  @Override
  public void truncate(long size) throws IOException {
    channel.truncate(size);
  }

  @Override
  public void position(long position) throws IOException {
    channel.position(position);
  }

  @Override
  public void force() throws IOException {
    channel.force(true);
  }

  @Override
  public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
      throws IOException {
    try {
      FileLock lock = channel.tryLock(position, size, false);
      return lock == null ? null : lock::release;
    } catch (OverlappingFileLockException unavailable) {
      return null;
    }
  }

  @Override
  public String physicalObjectIdentity() {
    return "test-private-output";
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
