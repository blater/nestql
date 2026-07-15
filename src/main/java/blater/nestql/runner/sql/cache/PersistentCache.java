package blater.nestql.runner.sql.cache;

import blater.nestql.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import static blater.nestql.ParameterParser.*;

/*
 * Responsibility: Owns persistent --cache storage, cache listing,
 * and cache clearing operations.
 */
public final class PersistentCache {
  private static final String CACHE_DATABASE = "cache";
  private static final String H2_DATABASE_FILE = CACHE_DATABASE + ".mv.db";
  private static final int METADATA_ROW_ID = 1;
  private static final String ACTIVE_CACHE_ROOT = "active.cache.root";
  private static final String ACTIVE_CACHE_DIRECTORY = "active.cache.directory";

  private PersistentCache() { }


  public static int clear(Map<String, String> params) {
    int cnt = 0;
    if (params.containsKey(CACHE_CLEAR_TARGET_PARAM)) {
      cnt = PersistentCache.clearForInput(params.get(CACHE_CLEAR_TARGET_PARAM), params);
    }
    else if (params.containsKey(CACHE_CLEAR_OLDER_THAN_PARAM)) {
      Duration duration = PersistentCache.parseDuration(params.get(CACHE_CLEAR_OLDER_THAN_PARAM));
      cnt = PersistentCache.clearOlderThan(duration, params);
    }
    else {
      cnt = PersistentCache.clearAll(params);
    }
    Log.info("Cleared " + cnt + " cache(s).");
    return cnt;
  }

  public static void list(Map<String, String> params) {
    System.out.println("Cache root: " + PersistentCache.cacheRoot(params));
    var entries = listCaches(params);
    if (entries.isEmpty()) {
      Log.info("No caches found.");
      return;
    }

    Log.info("  inputType\tcreated\tsource");
    for (var entry : entries) {
      Log.info((entry.active() ? "* " : "  ") + entry.inputType()
          + "\t" + ((entry.createdMillis()  <= 0) ? "-": Instant.ofEpochMilli(entry.createdMillis()).toString())
          + "\t" + entry.sourcePath());
    }
  }


  public static CacheHandle prepare(CacheSource source, Map<String, String> params)
  {
    Path cacheDir = cacheRoot(params).resolve(source.directoryName());

    if (readMetadata(cacheDir).filter(source::matches).isPresent())
    {
      return new CacheHandle(cacheDir, jdbcUrl(cacheDir), false, source);
    }

    deleteDirectory(cacheDir);
    createDirectories(cacheDir);
    return new CacheHandle(cacheDir, jdbcUrl(cacheDir), true, source);
  }

  public static void markLoaded(CacheHandle handle) {
    createDirectories(handle.cacheDir());
    writeMetadata(handle.cacheDir(), handle.source());
  }

  public static void activate(CacheHandle handle) {
    Path cacheDir = handle.cacheDir().toAbsolutePath().normalize();
    Path root = cacheDir.getParent();
    if (root == null || cacheDir.getFileName() == null) {
      Log.fatal(IllegalArgumentException.class, "Invalid cache directory: " + cacheDir);
    }
    Properties config = readConfig();
    config.setProperty(ACTIVE_CACHE_ROOT, root.toString());
    config.setProperty(ACTIVE_CACHE_DIRECTORY, cacheDir.getFileName().toString());
    writeConfig(config);
  }

  public static Optional<CacheHandle> active() {
    Optional<Path> configured = configuredActiveCacheDirectory();
    if (configured.isEmpty()) {
      return Optional.empty();
    }

    Path cacheDir = configured.get();
    Optional<Metadata> metadata = readMetadata(cacheDir);
    if (metadata.isEmpty()) {
      clearActiveSelection();
      return Optional.empty();
    }

    try {
      CacheSource source = metadata.get().source();
      return Optional.of(new CacheHandle(cacheDir, jdbcUrl(cacheDir), false, source));
    } catch (RuntimeException ex) {
      clearActiveSelection();
      return Optional.empty();
    }
  }

  static int clearAll(Map<String, String> params) {
    int cleared = 0;
    for (Path cacheDir : cacheDirectories(params)) {
      deleteDirectory(cacheDir);
      cleared++;
    }
    clearActiveIfMissing();
    return cleared;
  }

  static List<CacheEntry> listCaches(Map<String, String> params) {
    Path root = cacheRoot(params);
    if (!Files.exists(root)) {
      return List.of();
    }

    Optional<Path> activeCache = active().map(CacheHandle::cacheDir);
    try (Stream<Path> paths = Files.list(root)) {
      return paths.filter(Files::isDirectory)
          .map(cacheDir -> readMetadata(cacheDir).map(metadata -> new CacheEntry(
              metadata.sourcePath(),
              metadata.inputType(),
              metadata.createdMillis(),
              activeCache.map(cacheDir::equals).orElse(false))))
          .flatMap(Optional::stream)
          .sorted(Comparator.comparing(CacheEntry::sourcePath))
          .toList();
    } catch (IOException ex) {
      return Log.fatal(IllegalStateException.class, "Could not list cache directory: " + root, ex);
    }
  }

  public static int clearForInput(String inputFilename, Map<String, String> params) {
    String sourcePath = CacheSource.normalizedSourcePath(inputFilename).toString();
    int cleared = 0;
    for (Path cacheDir : cacheDirectories(params)) {
      if (readMetadata(cacheDir)
          .map(metadata -> sourcePath.equals(metadata.sourcePath()))
          .orElse(false)) {
        deleteDirectory(cacheDir);
        cleared++;
      }
    }
    Path legacyCacheDir = cacheRoot(params).resolve(Integer.toUnsignedString(sourcePath.hashCode(), 16));
    if (Files.exists(legacyCacheDir)) {
      deleteDirectory(legacyCacheDir);
      cleared++;
    }
    clearActiveIfMissing();
    return cleared;
  }

  public static int clearOlderThan(Duration duration, Map<String, String> params) {
    long cutoffMillis = Instant.now().minus(duration).toEpochMilli();
    int cleared = 0;
    for (Path cacheDir : cacheDirectories(params)) {
      if (readMetadata(cacheDir).map(metadata -> metadata.createdMillis() < cutoffMillis).orElse(false)) {
        deleteDirectory(cacheDir);
        cleared++;
      }
    }
    clearActiveIfMissing();
    return cleared;
  }

  public static Duration parseDuration(String value) {
    if (value == null || value.isBlank()) {
      return Log.fatal(IllegalArgumentException.class, "cache age duration is required");
    }
    String normalized = value.trim().toLowerCase();
    int split = 0;
    while (split < normalized.length() && Character.isDigit(normalized.charAt(split))) {
      split++;
    }
    if (split == 0 || split == normalized.length()) {
      return Log.fatal(IllegalArgumentException.class, "Unsupported cache age duration: " + value);
    }
    long amount = Long.parseLong(normalized.substring(0, split));
    String unit = normalized.substring(split).trim();
    return switch (unit) {
      case "m", "min", "mins", "minute", "minutes" -> Duration.ofMinutes(amount);
      case "h", "hr", "hrs", "hour", "hours" -> Duration.ofHours(amount);
      case "d", "day", "days" -> Duration.ofDays(amount);
      default -> Log.fatal(IllegalArgumentException.class, "Unsupported cache age duration: " + value);
    };
  }

  public static Path cacheRoot(Map<String, String> params) {
    String configured = params == null ? null : params.get(CACHE_DIR_PARAM);
    if (configured == null || configured.isBlank()) {
      configured = Path.of(System.getProperty("user.home"), ".nestql", "cache").toString();
    }
    return Path.of(configured).toAbsolutePath().normalize();
  }

  static Path configFile() {
    return Path.of(System.getProperty("user.home"), ".nestql", "config.properties")
        .toAbsolutePath()
        .normalize();
  }

  private static String jdbcUrl(Path cacheDir) {
    return "jdbc:h2:file:" + cacheDir.resolve(CACHE_DATABASE).toAbsolutePath().normalize() + ";MODE=MySQL;NON_KEYWORDS=VALUE";
  }

  private static String existingJdbcUrl(Path cacheDir) {
    return jdbcUrl(cacheDir) + ";IFEXISTS=TRUE";
  }

  private static Path h2DatabaseFile(Path cacheDir) {
    return cacheDir.resolve(H2_DATABASE_FILE);
  }

  private static Optional<Metadata> readMetadata(Path cacheDir) {
    if (!Files.exists(h2DatabaseFile(cacheDir))) {
      return Optional.empty();
    }

    try (Connection connection = connect(cacheDir, true)) {
      return readMetadata(connection);
    } catch (SQLException ex) {
      Log.debug("Could not read cache metadata [{}]: {}", cacheDir, ex.getMessage());
      return Optional.empty();
    }
  }

  private static Optional<Metadata> readMetadata(Connection connection) {
    String sql = """
        select source_path, input_type, identity_text, created_millis
        from cache_metadata
        where id = ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, METADATA_ROW_ID);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(new Metadata(
            resultSet.getString("source_path"),
            resultSet.getString("input_type"),
            resultSet.getString("identity_text"),
            resultSet.getLong("created_millis")));
      }
    } catch (SQLException ex) {
      return Optional.empty();
    }
  }

  private static void writeMetadata(Path cacheDir, CacheSource source) {
    try (Connection connection = connect(cacheDir, false)) {
      createMetadataTable(connection);
      long now = Instant.now().toEpochMilli();
      try (PreparedStatement delete = connection.prepareStatement("delete from cache_metadata where id = ?")) {
        delete.setInt(1, METADATA_ROW_ID);
        delete.executeUpdate();
      }
      String sql = """
          insert into cache_metadata (
            id, source_path, input_type, identity_text, created_millis
          ) values (?, ?, ?, ?, ?)
          """;
      try (PreparedStatement insert = connection.prepareStatement(sql)) {
        insert.setInt(1, METADATA_ROW_ID);
        insert.setString(2, source.sourcePath());
        insert.setString(3, source.inputType().name());
        insert.setString(4, source.identityText());
        insert.setLong(5, now);
        insert.executeUpdate();
      }
    } catch (SQLException ex) {
      Log.fatal(IllegalStateException.class, "Could not write cache metadata: " + cacheDir, ex);
    }
  }

  private static void createMetadataTable(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          create table if not exists cache_metadata (
            id integer primary key,
            source_path varchar(4096) not null,
            input_type varchar(32) not null,
            identity_text varchar(65535) not null,
            created_millis bigint not null
          )
          """);
    }
  }

  private static Connection connect(Path cacheDir, boolean existingOnly) throws SQLException {
    try {
      Class.forName("org.h2.Driver");
    } catch (ClassNotFoundException ex) {
      return Log.fatal(IllegalStateException.class, "H2 driver is not available", ex);
    }
    return DriverManager.getConnection(existingOnly ? existingJdbcUrl(cacheDir) : jdbcUrl(cacheDir), "sa", "");
  }

  private static List<Path> cacheDirectories(Map<String, String> params) {
    Path root = cacheRoot(params);
    if (!Files.exists(root)) {
      return List.of();
    }

    try (Stream<Path> paths = Files.list(root)) {
      return paths.filter(Files::isDirectory).toList();
    } catch (IOException ex) {
      return Log.fatal(IllegalStateException.class, "Could not list cache directory: " + root, ex);
    }
  }

  private static Optional<Path> configuredActiveCacheDirectory() {
    Properties config = readConfig();
    String rootValue = config.getProperty(ACTIVE_CACHE_ROOT);
    String directoryValue = config.getProperty(ACTIVE_CACHE_DIRECTORY);
    boolean rootMissing = rootValue == null || rootValue.isBlank();
    boolean directoryMissing = directoryValue == null || directoryValue.isBlank();
    if (rootMissing && directoryMissing) {
      return Optional.empty();
    }
    if (rootMissing || directoryMissing) {
      clearActiveSelection();
      return Optional.empty();
    }

    try {
      Path directoryName = Path.of(directoryValue);
      if (directoryName.getNameCount() != 1 || directoryName.isAbsolute()) {
        clearActiveSelection();
        return Optional.empty();
      }
      Path root = Path.of(rootValue).toAbsolutePath().normalize();
      Path cacheDir = root.resolve(directoryName).normalize();
      if (!root.equals(cacheDir.getParent())) {
        clearActiveSelection();
        return Optional.empty();
      }
      return Optional.of(cacheDir);
    } catch (RuntimeException ex) {
      clearActiveSelection();
      return Optional.empty();
    }
  }

  private static Properties readConfig() {
    Properties config = new Properties();
    Path file = configFile();
    if (!Files.exists(file)) {
      return config;
    }
    try (InputStream input = Files.newInputStream(file)) {
      config.load(input);
      return config;
    } catch (IOException ex) {
      return Log.fatal(IllegalStateException.class, "Could not read nestQL configuration: " + file, ex);
    }
  }

  private static void writeConfig(Properties config) {
    Path file = configFile();
    try {
      Files.createDirectories(file.getParent());
      try (OutputStream output = Files.newOutputStream(file)) {
        config.store(output, "nestQL configuration");
      }
    } catch (IOException ex) {
      Log.fatal(IllegalStateException.class, "Could not write nestQL configuration: " + file, ex);
    }
  }

  private static void clearActiveIfMissing() {
    Optional<Path> activeCache = configuredActiveCacheDirectory();
    if (activeCache.isPresent() && !Files.exists(activeCache.get())) {
      clearActiveSelection();
    }
  }

  private static void clearActiveSelection() {
    Properties config = readConfig();
    boolean changed = config.remove(ACTIVE_CACHE_ROOT) != null;
    changed = config.remove(ACTIVE_CACHE_DIRECTORY) != null || changed;
    if (!changed) {
      return;
    }

    Path file = configFile();
    if (config.isEmpty()) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException ex) {
        Log.fatal(IllegalStateException.class, "Could not update nestQL configuration: " + file, ex);
      }
    } else {
      writeConfig(config);
    }
  }

  private static void createDirectories(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (IOException ex) {
      Log.fatal(IllegalStateException.class, "Could not create cache directory: " + dir, ex);
    }
  }

  private static void deleteDirectory(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder())
          .forEach(PersistentCache::deletePath);
    } catch (IOException ex) {
      Log.fatal(IllegalStateException.class, "Could not delete cache directory: " + dir, ex);
    }
  }

  private static void deletePath(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      Log.fatal(IllegalStateException.class, "Could not delete cache path: " + path, ex);
    }
  }

}
