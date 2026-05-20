package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/** Minimal file channel wrapper for fixture-backed file I/O tests. */
final class AclFixtureFileChannel extends FileChannel {
  private final AclFixtureSeekableByteChannel delegate;

  AclFixtureFileChannel(AclFixturePath path, AclFixtureSeekableByteChannel delegate) {
    java.util.Objects.requireNonNull(path, "path");
    this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    return delegate.read(dst);
  }

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) {
    throw new UnsupportedOperationException("scattered reads are not used by this test fixture");
  }

  @Override
  public int write(ByteBuffer src) throws IOException {
    return delegate.write(src);
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) {
    throw new UnsupportedOperationException("gathered writes are not used by this test fixture");
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
  public void force(boolean metaData) {}

  @Override
  public long transferTo(long position, long count, WritableByteChannel target) {
    throw new UnsupportedOperationException("transferTo is not used by this test fixture");
  }

  @Override
  public long transferFrom(ReadableByteChannel src, long position, long count) {
    throw new UnsupportedOperationException("transferFrom is not used by this test fixture");
  }

  @Override
  public int read(ByteBuffer dst, long position) {
    throw new UnsupportedOperationException("positional reads are not used by this test fixture");
  }

  @Override
  public int write(ByteBuffer src, long position) {
    throw new UnsupportedOperationException("positional writes are not used by this test fixture");
  }

  @Override
  public MappedByteBuffer map(MapMode mode, long position, long size) {
    throw new UnsupportedOperationException("memory mapping is not used by this test fixture");
  }

  @Override
  public FileLock lock(long position, long size, boolean shared) throws IOException {
    return new FixtureFileLock(this, position, size, shared);
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) throws IOException {
    return new FixtureFileLock(this, position, size, shared);
  }

  @Override
  protected void implCloseChannel() throws IOException {
    delegate.close();
  }

  /** Minimal lock handle so the ACL fixture can exercise file-lock paths. */
  private static final class FixtureFileLock extends FileLock {
    private boolean valid = true;

    FixtureFileLock(FileChannel channel, long position, long size, boolean shared) {
      super(channel, position, size, shared);
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public void release() {
      valid = false;
    }
  }
}
