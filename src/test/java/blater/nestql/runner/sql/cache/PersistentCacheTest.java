package blater.nestql.runner.sql.cache;

import blater.nestql.ParameterParser;
import blater.nestql.inputreader.InputType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentCacheTest {
  @TempDir
  Path tempDir;

  @BeforeEach
  void clearActiveCacheSelection() throws Exception {
    Files.deleteIfExists(PersistentCache.configFile());
  }

  @Test
  void reusesCacheWhenInputFileMatchesMetadata() throws Exception {
    Path input = write("input.json", "{\"name\":\"Fred\"}");
    Map<String, String> params = cacheParams();

    CacheHandle first = PersistentCache.prepare(source(input), params);
    assertTrue(first.needsLoad());
    PersistentCache.markLoaded(first);

    CacheHandle second = PersistentCache.prepare(source(input), params);

    assertFalse(second.needsLoad());
    assertEquals(first.cacheDir(), second.cacheDir());
  }

  @Test
  void reusesCacheWhenInputFileChanges() throws Exception {
    Path input = write("input.json", "{\"name\":\"Fred\"}");
    Map<String, String> params = cacheParams();

    CacheHandle first = PersistentCache.prepare(source(input), params);
    PersistentCache.markLoaded(first);

    Files.writeString(input, "{\"name\":\"Fred\",\"extra\":\"changed\"}");
    CacheHandle second = PersistentCache.prepare(source(input), params);

    assertFalse(second.needsLoad());
    assertEquals(first.cacheDir(), second.cacheDir());
  }

  @Test
  void reusesCacheWhenInputFileNoLongerExists() throws Exception {
    Path input = write("input.json", "{\"name\":\"Fred\"}");
    Map<String, String> params = cacheParams();

    CacheHandle first = PersistentCache.prepare(source(input), params);
    PersistentCache.markLoaded(first);
    Files.delete(input);

    CacheHandle second = PersistentCache.prepare(source(input), params);

    assertFalse(second.needsLoad());
    assertEquals(first.cacheDir(), second.cacheDir());
  }

  @Test
  void clearsSpecificCacheForInputFile() throws Exception {
    Path firstInput = write("first.json", "{\"name\":\"Fred\"}");
    Path secondInput = write("second.json", "{\"name\":\"Wilma\"}");
    Map<String, String> params = cacheParams();
    CacheHandle first = preparedCache(firstInput, params);
    CacheHandle second = preparedCache(secondInput, params);

    int cleared = PersistentCache.clearForInput(firstInput.toString(), params);

    assertEquals(1, cleared);
    assertFalse(Files.exists(first.cacheDir()));
    assertTrue(Files.exists(second.cacheDir()));
  }

  @Test
  void parquetRecordNamesCreateDistinctCacheVariantsForSameFile() throws Exception {
    Path input = write("customers.parquet", "not used by this test");
    Map<String, String> params = cacheParams();
    CacheHandle customer = preparedCache(parquetSource(input, "customer"), params);
    CacheHandle account = preparedCache(parquetSource(input, "account"), params);

    assertTrue(Files.exists(customer.cacheDir()));
    assertTrue(Files.exists(account.cacheDir()));
    assertFalse(customer.cacheDir().equals(account.cacheDir()));
  }

  @Test
  void parquetRootNameDoesNotCreateAnotherCacheVariant() throws Exception {
    Path input = write("customers.parquet", "not used by this test");
    Map<String, String> params = cacheParams();
    CacheHandle first = preparedCache(CacheSource.from(
        input.toString(),
        InputType.PARQUET,
        Map.of(ParameterParser.PARQUET_ROOT_PARAM, "customers")), params);
    CacheHandle second = PersistentCache.prepare(CacheSource.from(
        input.toString(),
        InputType.PARQUET,
        Map.of(ParameterParser.PARQUET_ROOT_PARAM, "accounts")), params);

    assertFalse(second.needsLoad());
    assertEquals(first.cacheDir(), second.cacheDir());
  }

  @Test
  void explicitEmptyParquetRecordNameIsDistinctFromNoOverride() throws Exception {
    Path input = write("customers.parquet", "not used by this test");

    CacheSource inferred = CacheSource.from(input.toString(), InputType.PARQUET);
    CacheSource emptyOverride = CacheSource.from(
        input.toString(),
        InputType.PARQUET,
        Map.of(ParameterParser.PARQUET_RECORD_PARAM, ""));

    assertFalse(inferred.directoryName().equals(emptyOverride.directoryName()));
  }

  @Test
  void clearForInputRemovesAllCacheVariantsForSameSourcePath() throws Exception {
    Path input = write("customers.parquet", "not used by this test");
    Path otherInput = write("other.parquet", "not used by this test");
    Map<String, String> params = cacheParams();
    CacheHandle customer = preparedCache(parquetSource(input, "customer"), params);
    CacheHandle account = preparedCache(parquetSource(input, "account"), params);
    CacheHandle other = preparedCache(parquetSource(otherInput, "customer"), params);

    int cleared = PersistentCache.clearForInput(input.toString(), params);

    assertEquals(2, cleared);
    assertFalse(Files.exists(customer.cacheDir()));
    assertFalse(Files.exists(account.cacheDir()));
    assertTrue(Files.exists(other.cacheDir()));
  }

  @Test
  void clearsAllCaches() throws Exception {
    Map<String, String> params = cacheParams();
    preparedCache(write("first.json", "{\"name\":\"Fred\"}"), params);
    preparedCache(write("second.json", "{\"name\":\"Wilma\"}"), params);

    int cleared = PersistentCache.clearAll(params);

    assertEquals(2, cleared);
    assertEquals(0, cacheDirectoryCount(params));
  }

  @Test
  void clearEntryPointClearsExistingCache() throws Exception {
    Map<String, String> params = cacheParams(ParameterParser.CACHE_CLEAR_ALL_PARAM, "true");
    CacheHandle handle = preparedCache(write("cached.json", "{\"name\":\"Fred\"}"), params);
    assertTrue(Files.exists(handle.cacheDir()));

    int cleared = PersistentCache.clear(params);

    assertEquals(1, cleared);
    assertFalse(Files.exists(handle.cacheDir()));
  }

  @Test
  void listsCachesFromCacheDatabase() throws Exception {
    Map<String, String> params = cacheParams();
    Path firstInput = write("first.json", "{\"name\":\"Fred\"}");
    Path secondInput = write("second.json", "{\"name\":\"Wilma\"}");
    preparedCache(secondInput, params);
    preparedCache(firstInput, params);
    Files.createDirectories(PersistentCache.cacheRoot(params).resolve("orphan-without-metadata"));

    var entries = PersistentCache.listCaches(params);

    assertEquals(2, entries.size());
    assertEquals(firstInput.toAbsolutePath().normalize().toString(), entries.get(0).sourcePath());
    assertEquals(InputType.JSON.name(), entries.get(0).inputType());
    assertTrue(entries.get(0).createdMillis() > 0);
    assertEquals(secondInput.toAbsolutePath().normalize().toString(), entries.get(1).sourcePath());
  }

  @Test
  void listEntryPointLeavesExistingCacheReusable() throws Exception {
    Map<String, String> params = cacheParams(ParameterParser.CACHE_LIST_PARAM, "true");
    Path input = write("cached.json", "{\"name\":\"Fred\"}");
    CacheHandle handle = preparedCache(input, params);

    PersistentCache.list(params);

    CacheHandle reused = PersistentCache.prepare(source(input), params);
    assertEquals(handle.cacheDir(), reused.cacheDir());
    assertFalse(reused.needsLoad());
  }

  @Test
  void clearsCachesOlderThanDurationUsingCreatedTime() throws Exception {
    Map<String, String> params = cacheParams();
    CacheHandle oldCache = preparedCache(write("old.json", "{\"name\":\"Fred\"}"), params);
    CacheHandle recentCache = preparedCache(write("recent.json", "{\"name\":\"Wilma\"}"), params);
    forceCreated(oldCache, "1");

    int cleared = PersistentCache.clearOlderThan(Duration.ofDays(1), params);

    assertEquals(1, cleared);
    assertFalse(Files.exists(oldCache.cacheDir()));
    assertTrue(Files.exists(recentCache.cacheDir()));
  }

  @Test
  void activeCacheIsListedAndClearedWithoutLosingOtherConfiguration() throws Exception {
    Map<String, String> params = cacheParams();
    Path input = write("active.json", "{\"name\":\"Fred\"}");
    CacheHandle handle = preparedCache(input, params);
    Path configFile = PersistentCache.configFile();
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "unrelated=value\n");

    PersistentCache.activate(handle);

    assertEquals(handle.cacheDir(), PersistentCache.active().orElseThrow().cacheDir());
    assertTrue(PersistentCache.listCaches(params).getFirst().active());
    assertTrue(Files.readString(configFile).contains("unrelated=value"));

    PersistentCache.clearForInput(input.toString(), params);

    assertTrue(PersistentCache.active().isEmpty());
    assertTrue(Files.readString(configFile).contains("unrelated=value"));
  }

  @Test
  void parsesMinuteHourAndDayDurations() {
    assertEquals(Duration.ofMinutes(30), PersistentCache.parseDuration("30m"));
    assertEquals(Duration.ofHours(6), PersistentCache.parseDuration("6hours"));
    assertEquals(Duration.ofDays(2), PersistentCache.parseDuration("2days"));
  }

  private CacheHandle preparedCache(Path input, Map<String, String> params) throws Exception {
    return preparedCache(source(input), params);
  }

  private CacheHandle preparedCache(CacheSource source, Map<String, String> params) throws Exception {
    CacheHandle handle = PersistentCache.prepare(source, params);
    PersistentCache.markLoaded(handle);
    return handle;
  }

  private void forceCreated(CacheHandle handle, String millis) throws Exception {
    try (var connection = DriverManager.getConnection(handle.jdbcUrl(), "sa", "");
         var statement = connection.prepareStatement("update cache_metadata set created_millis = ? where id = 1")) {
      statement.setLong(1, Long.parseLong(millis));
      statement.executeUpdate();
    }
  }

  private int cacheDirectoryCount(Map<String, String> params) throws Exception {
    try (var paths = Files.list(PersistentCache.cacheRoot(params))) {
      return (int) paths.filter(Files::isDirectory).count();
    }
  }

  private Map<String, String> cacheParams() {
    return Map.of(ParameterParser.CACHE_DIR_PARAM, tempDir.resolve("cache").toString());
  }

  private Map<String, String> cacheParams(String key, String value) {
    return Map.of(
        ParameterParser.CACHE_DIR_PARAM, tempDir.resolve("cache").toString(),
        key, value);
  }

  private CacheSource source(Path input) {
    return CacheSource.from(input.toString(), InputType.JSON);
  }

  private CacheSource parquetSource(Path input, String recordName) {
    return CacheSource.from(
        input.toString(),
        InputType.PARQUET,
        Map.of(ParameterParser.PARQUET_RECORD_PARAM, recordName));
  }

  private Path write(String name, String content) throws Exception {
    Path path = tempDir.resolve(name);
    Files.writeString(path, content);
    return path;
  }
}
