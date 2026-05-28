package dev.erst.fingrind.sqlite;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Minimal ACL-capable filesystem for exercising platform-specific security code. */
public final class AclFixtureFileSystem extends FileSystem {
  private final FileSystemProvider provider;
  private final Map<String, AclFixturePath> paths = new ConcurrentHashMap<>();
  private final Set<String> views;
  public final UserPrincipal owner = new AclFixturePrincipal("owner");
  public final GroupPrincipal group = new AclFixtureGroup("group");
  private @Nullable Consumer<AclFixturePath> pathInitializer;
  private boolean open = true;

  private AclFixtureFileSystem(Set<String> views) {
    this.views = Set.copyOf(views);
    this.provider = new AclFixtureFileSystemProvider(this);
  }

  public static AclFixtureFileSystem withViews(Set<String> views) {
    return new AclFixtureFileSystem(views);
  }

  public AclFixtureFileSystem onPathCreated(Consumer<AclFixturePath> initializer) {
    pathInitializer = Objects.requireNonNull(initializer, "initializer");
    return this;
  }

  public AclFixturePath path(String value) {
    return paths.computeIfAbsent(
        value,
        key -> {
          AclFixturePath createdPath = new AclFixturePath(this, key);
          @Nullable Consumer<AclFixturePath> initializer = pathInitializer;
          if (initializer != null) {
            initializer.accept(createdPath);
          }
          return createdPath;
        });
  }

  Map<String, AclFixturePath> registeredPaths() {
    return paths;
  }

  Set<String> supportedViews() {
    return views;
  }

  public UserPrincipal owner() {
    return owner;
  }

  public GroupPrincipal group() {
    return group;
  }

  @Override
  public FileSystemProvider provider() {
    return provider;
  }

  @Override
  public void close() {
    open = false;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getSeparator() {
    return "\\";
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return List.of(path("\\"));
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return List.of(new AclFixtureFileStore(views));
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return views;
  }

  @Override
  public Path getPath(String first, String... more) {
    StringBuilder joined = new StringBuilder(first);
    for (String part : more) {
      joined.append(getSeparator()).append(part);
    }
    return path(joined.toString());
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    throw new UnsupportedOperationException("path matching is not used by this test filesystem");
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException("principal lookup is not used by this test filesystem");
  }

  @Override
  public WatchService newWatchService() {
    throw new UnsupportedOperationException("watch service is not used by this test filesystem");
  }
}
