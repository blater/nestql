package blater.nestql;

import blater.nestql.testsupport.ParquetTestFiles;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.schema.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
  @TempDir
  Path tempDir;

  @BeforeEach
  void clearActiveCacheSelection() throws Exception {
    Files.deleteIfExists(Path.of(
        System.getProperty("user.home"), ".nestql", "config.properties"));
  }

  @Test
  void sqlToXmlScriptDoesNotReadUnusedLoadFile() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    Path script = write("query.nql", "select 1 into {result.value}\\G\n");
    Path missingLoadFile = tempDir.resolve("missing.xml");

    Main.main(script.toString(), missingLoadFile.toString(), "-p", properties.toString());
  }

  @Test
  void dmlScriptLoadsXmlWhenMappedInputIsReached() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      execute(connection, "create table audit_log (personid integer, firstname varchar(80))");

      Path script = write("insert.nql", """
          autocommit on
          \\g
          insert into audit_log (personid, firstname)
          values ({message.person.id}, {message.person.firstname})
          \\g
          """);
      Path input = write("input.xml", """
          <message>
            <person>
              <id>7</id>
              <firstname>Fred</firstname>
            </person>
          </message>
          """);

      Main.main(script.toString(), input.toString(), "-p", properties.toString());

      assertEquals(1, queryInt(connection, "select count(*) from audit_log"));
      assertEquals("Fred", queryString(connection, "select firstname from audit_log where personid = 7"));
    }
  }

@Test
  void missingLoadFileFailsWhenDmlMappingNeedsXml() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      execute(connection, "create table audit_log (personid integer)");
      Path script = write("insert.nql", """
          insert into audit_log (personid)
          values ({message.person.id})
          \\g
          """);

      assertThrows(IllegalStateException.class,
          () -> Main.main(script.toString(), "-p", properties.toString()));
    }
  }

@Test
  void argumentsCanAppearInAnyUnambiguousOrder() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      execute(connection, "create table audit_log (actor varchar(80))");
      Path script = write("literal.nql", """
          autocommit on
          \\g
          insert into audit_log (actor) values ('${actor}');
          \\g
          """);

      Main.main("actor=Fred", "-p", properties.toString(), script.toString());

      assertEquals("Fred", queryString(connection, "select actor from audit_log"));
    }
  }

  @Test
  void scriptOutputDirectiveSelectsOutputWriter() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    Path script = write("query.nql", """
        output json;
        select 1 into {result.value};
        """);

    String output = captureStdout(() -> Main.main(script.toString(), "-p", properties.toString()));

    assertEquals("""
        {"result":{"value":"1"}}
        """, output);
  }

  @Test
  void commandLineOutputFlagOverridesScriptOutputDirective() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    Path script = write("query.nql", """
        output xml;
        select 1 into {result.value};
        """);

    String output = captureStdout(
        () -> Main.main(script.toString(), "-p", properties.toString(), "--output", "JSON"));

    assertEquals("""
        {"result":{"value":"1"}}
        """, output);
  }

  @Test
  void markdownOutputCanBeSelectedByScriptOrCommandLine() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    Path markdownScript = write("markdown-query.nql", """
        output markdown;
        select 1 into {result.value};
        """);
    Path overriddenScript = write("overridden-query.nql", """
        output xml;
        select 1 into {result.value};
        """);

    String scriptOutput = captureStdout(
        () -> Main.main(markdownScript.toString(), "-p", properties.toString()));
    String commandOutput = captureStdout(
        () -> Main.main(
            overriddenScript.toString(), "-p", properties.toString(), "--output", "markdown"));

    assertEquals(scriptOutput, commandOutput);
    assertEquals(3, scriptOutput.lines().count());
    assertTrue(scriptOutput.contains("1"));
  }

  @Test
  void inlineScriptArgumentCanReplaceScriptFile() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);

    String output = captureStdout(() -> Main.main(
        "output json; select 1 into {result.value};",
        "-p",
        properties.toString()));

    assertEquals("""
        {"result":{"value":"1"}}
        """, output);
  }

  @Test
  void inlineScriptArgumentCanFollowInputFile() throws Exception {
    Path input = write("input.json", """
        {
          "data": {
            "customer": [
              { "id": "C1", "country": "GB" }
            ]
          }
        }
        """);

    String output = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir",
        tempDir.resolve("cache-inline").toString(),
        input.toString(),
        "output json; catalog;"));

    assertTrue(output.contains("\"catalog\""));
    assertTrue(output.contains("\"CUSTOMER\""));
  }

  @Test
  void shortOutputFlagIsCaseInsensitive() throws Exception {
    String url = databaseUrl();
    Path properties = propertiesFile(url);
    Path script = write("query.nql", "select 1 into {result.value};");

    String output = captureStdout(
        () -> Main.main("-o", "csv", script.toString(), "-p", properties.toString()));

    assertEquals("""
        value
        1
        """, output);
  }

  @Test
  void cacheFlagCanAppearBeforeScriptAndInputFile() throws Exception {
    Path script = write("query.nql", "select 1 into {result.value};");
    Path input = write("input.json", "{}");

    var params = ParameterParser.parse(
        "--cache",
        "--cache-dir", tempDir.resolve("cache").toString(),
        script.toString(),
        input.toString());

    assertEquals("true", params.get(ParameterParser.CACHE_MODE_PARAM));
    assertEquals(tempDir.resolve("cache").toString(), params.get(ParameterParser.CACHE_DIR_PARAM));
    assertEquals(script.toString(), params.get(ParameterParser.SCRIPT_FILE_PARAM));
    assertEquals(input.toString(), params.get(ParameterParser.INPUT_FILENAME));
  }

  @Test
  void cacheFlagWithOneInputFileIsAStandaloneCacheCommand() throws Exception {
    Path input = write("standalone.json", "{}");

    var params = ParameterParser.parse("--cache", input.toString());

    assertEquals("true", params.get(ParameterParser.CACHE_MODE_PARAM));
    assertEquals(input.toString(), params.get(ParameterParser.INPUT_FILENAME));
    assertFalse(params.containsKey(ParameterParser.SCRIPT_FILE_PARAM));
    assertFalse(params.containsKey(ParameterParser.SCRIPT_TEXT_PARAM));
  }

  @Test
  void catalogCommandStoresItsOptionalPatternAndConnectionSelection() {
    var summary = ParameterParser.parse("catalog");
    var details = ParameterParser.parse("--output=json", "catalog", "customer*");
    var cache = ParameterParser.parse("catalog", "customer*", "--cache", "customers.json");
    var jdbc = ParameterParser.parse("catalog", "*", "--db", "h2", "--database", "mem:catalog");

    assertEquals("", summary.get(ParameterParser.CATALOG_PATTERN_PARAM));
    assertFalse(summary.containsKey(ParameterParser.SCRIPT_FILE_PARAM));
    assertFalse(summary.containsKey(ParameterParser.SCRIPT_TEXT_PARAM));
    assertEquals("customer*", details.get(ParameterParser.CATALOG_PATTERN_PARAM));
    assertEquals("json", details.get(ParameterParser.OUTPUT_TYPE_PARAM));
    assertEquals("customer*", cache.get(ParameterParser.CATALOG_PATTERN_PARAM));
    assertEquals("customers.json", cache.get(ParameterParser.INPUT_FILENAME));
    assertEquals("*", jdbc.get(ParameterParser.CATALOG_PATTERN_PARAM));
    assertEquals("jdbc:h2:mem:catalog", jdbc.get(ParameterParser.JDBC_DATABASE_PARAM));
  }

  @Test
  void cacheClearFlagsDoNotRequireScriptFile() {
    var all = ParameterParser.parse("--clear-cache", "--cache-dir", tempDir.resolve("cache").toString());
    var target = ParameterParser.parse("--clear-cache", "input.json", "--cache-dir", tempDir.resolve("cache").toString());
    var older = ParameterParser.parse("--clear-cache-older-than", "30m", "--cache-dir", tempDir.resolve("cache").toString());
    var list = ParameterParser.parse("--list-caches", "--cache-dir", tempDir.resolve("cache").toString());

    assertEquals("true", all.get(ParameterParser.CACHE_CLEAR_ALL_PARAM));
    assertEquals("input.json", target.get(ParameterParser.CACHE_CLEAR_TARGET_PARAM));
    assertEquals("30m", older.get(ParameterParser.CACHE_CLEAR_OLDER_THAN_PARAM));
    assertEquals("true", list.get(ParameterParser.CACHE_LIST_PARAM));
  }

  @Test
  void parquetNamingFlagsAreStoredAsSystemParameters() throws Exception {
    Path script = write("query.nql", "catalog;");
    Path input = write("input.parquet", "");

    var params = ParameterParser.parse(
        "--parquet-root", "customers",
        "--parquet-record=customer",
        script.toString(),
        input.toString());

    assertEquals("customers", params.get(ParameterParser.PARQUET_ROOT_PARAM));
    assertEquals("customer", params.get(ParameterParser.PARQUET_RECORD_PARAM));
    assertEquals(script.toString(), params.get(ParameterParser.SCRIPT_FILE_PARAM));
    assertEquals(input.toString(), params.get(ParameterParser.INPUT_FILENAME));
  }

  @Test
  void parquetNamingFlagsSupportEqualsAndRejectMissingValues() throws Exception {
    Path script = write("query.nql", "catalog;");
    Path input = write("input.parquet", "");

    var params = ParameterParser.parse(
        "--parquet-root=customers",
        "--parquet-record=customer",
        script.toString(),
        input.toString());

    assertEquals("customers", params.get(ParameterParser.PARQUET_ROOT_PARAM));
    assertEquals("customer", params.get(ParameterParser.PARQUET_RECORD_PARAM));
    assertThrows(IllegalArgumentException.class, () -> ParameterParser.parse("--parquet-root"));
    assertThrows(IllegalArgumentException.class, () -> ParameterParser.parse("--parquet-record", "--cache"));
  }

  @Test
  void nonJdbcLongOptionsShareEqualsValueSyntax() throws Exception {
    Path script = write("query.nql", "catalog;");

    var params = ParameterParser.parse(
        script.toString(),
        "--output=json",
        "--cache-dir=" + tempDir.resolve("cache"),
        "--parquet-root=customers",
        "--parquet-record=customer");
    var clearTarget = ParameterParser.parse("--clear-cache=input.json");
    var clearOlder = ParameterParser.parse("--clear-cache-older-than=6h");

    assertEquals("json", params.get(ParameterParser.OUTPUT_TYPE_PARAM));
    assertEquals(tempDir.resolve("cache").toString(), params.get(ParameterParser.CACHE_DIR_PARAM));
    assertEquals("customers", params.get(ParameterParser.PARQUET_ROOT_PARAM));
    assertEquals("customer", params.get(ParameterParser.PARQUET_RECORD_PARAM));
    assertEquals("input.json", clearTarget.get(ParameterParser.CACHE_CLEAR_TARGET_PARAM));
    assertEquals("6h", clearOlder.get(ParameterParser.CACHE_CLEAR_OLDER_THAN_PARAM));
  }

  @Test
  void propertiesFileCannotSetParquetSystemParameters() {
    Map<String, String> params = new LinkedHashMap<>();

    ParameterParser.addParameterFromMainPropsFile(
        params,
        ParameterParser.PARQUET_ROOT_PARAM + "=customers");
    ParameterParser.addParameterFromMainPropsFile(
        params,
        ParameterParser.PARQUET_RECORD_PARAM + "=customer");

    assertEquals(Map.of(), params);
  }

  @Test
  void cacheModeQueriesJsonInputThroughGeneratedTables() throws Exception {
    Path script = write("query.nql", """
        output json;
        select
          c.id into {result.customer.id},
          c.country into {result.customer.country}
        from customer c
        where c.country = 'GB'
        order by c.id asc createsNew {result.customer};
        """);
    Path input = write("input.json", """
        {
          "data": {
            "customer": [
              { "id": "C1", "country": "GB" },
              { "id": "C2", "country": "US" }
            ]
          }
        }
        """);

    String output = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", tempDir.resolve("cache-json").toString(),
        script.toString(),
        input.toString()));

    assertEquals("""
        {"result":{"customer":{"id":"C1","country":"GB"}}}
        """, output);
  }

  @Test
  void standaloneCacheLoadReportsReuseAndSuppliesTheActiveCache() throws Exception {
    Path cacheDir = tempDir.resolve("active-cache");
    Path input = write("active.json", """
        {
          "data": {
            "customer": [
              { "id": "C1", "country": "GB" }
            ]
          }
        }
        """);
    Path script = write("active-query.nql", """
        output json;
        select c.id into {result.customer.id}
        from customer c
        where c.country = 'GB';
        """);

    String loaded = captureStdout(() -> Main.main(
        "--cache-dir", cacheDir.toString(), "--cache", input.toString()));
    String reused = captureStdout(() -> Main.main(
        "--cache-dir", cacheDir.toString(), "--cache", input.toString()));
    String queryOutput = captureStdout(() -> Main.main(script.toString()));

    String source = input.toAbsolutePath().normalize().toString();
    assertEquals("Loaded cache for " + source + System.lineSeparator(), loaded);
    assertEquals("Using existing cache for " + source + System.lineSeparator(), reused);
    assertEquals("""
        {"result":{"customer":{"id":"C1"}}}
        """, queryOutput);
  }

  @Test
  void explicitCacheSelectionBecomesActiveForLaterQueries() throws Exception {
    Path cacheDir = tempDir.resolve("selected-cache");
    Path first = write("first-active.json", """
        { "data": { "customer": [{ "id": "FIRST" }] } }
        """);
    Path second = write("second-active.json", """
        { "data": { "customer": [{ "id": "SECOND" }] } }
        """);
    Path script = write("selected-query.nql", """
        output json;
        select c.id into {result.id} from customer c;
        """);

    Main.main("--cache-dir", cacheDir.toString(), "--cache", first.toString());
    Main.main("--cache-dir", cacheDir.toString(), "--cache", second.toString());

    String selected = captureStdout(() -> Main.main(
        script.toString(), "--cache-dir", cacheDir.toString(), "--cache", first.toString()));
    String active = captureStdout(() -> Main.main(script.toString()));

    assertEquals("""
        {"result":{"id":"FIRST"}}
        """, selected);
    assertEquals(selected, active);
  }

  @Test
  void explicitCacheWinsOverJdbcSettingsAndKeepsRuntimeProperties() throws Exception {
    Path cacheDir = tempDir.resolve("cache-wins");
    Path input = write("cache-wins.json", """
        { "data": { "customer": [{ "id": "C1", "country": "GB" }] } }
        """);
    Path script = write("cache-wins.nql", """
        output json;
        select c.id into {result.id}
        from customer c
        where c.country = '${region}';
        """);
    Path properties = write("external.properties", """
        jdbc.driver=postgresql
        jdbc.database=jdbc:postgresql://invalid/external
        jdbc.username=external
        jdbc.password=secret
        region=GB
        """);

    String output = captureStdout(() -> Main.main(
        script.toString(), input.toString(), "--cache",
        "--cache-dir", cacheDir.toString(), "-p", properties.toString()));

    assertEquals("""
        {"result":{"id":"C1"}}
        """, output);
  }

  @Test
  void jdbcSettingsWinOverTheActiveCacheWhenCacheIsNotExplicit() throws Exception {
    Path cacheDir = tempDir.resolve("jdbc-wins");
    Path input = write("jdbc-wins.json", """
        { "data": { "customer": [{ "id": "CACHED" }] } }
        """);
    Main.main("--cache-dir", cacheDir.toString(), "--cache", input.toString());

    String url = databaseUrl();
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      execute(connection, "create table source (result_value varchar(20))");
      execute(connection, "insert into source (result_value) values ('EXTERNAL')");
    }
    Path properties = propertiesFile(url);
    Path script = write("jdbc-wins.nql", """
        output json;
        select result_value into {result.value} from source;
        """);

    String output = captureStdout(() -> Main.main(script.toString(), "-p", properties.toString()));

    assertEquals("""
        {"result":{"value":"EXTERNAL"}}
        """, output);
  }

  @Test
  void cacheModeReusesExistingCacheWithoutReadingSourceFile() throws Exception {
    Path cacheDir = tempDir.resolve("cache-reuse");
    Path script = write("query.nql", """
        output json;
        select c.id into {result.customer.id}
        from customer c
        where c.country = 'GB';
        """);
    Path input = write("input.json", """
        {
          "data": {
            "customer": [
              { "id": "C1", "country": "GB" },
              { "id": "C2", "country": "US" }
            ]
          }
        }
        """);

    String firstOutput = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", cacheDir.toString(),
        script.toString(),
        input.toString()));
    assertEquals("""
        {"result":{"customer":{"id":"C1"}}}
        """, firstOutput);
    Files.delete(input);

    String output = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", cacheDir.toString(),
        script.toString(),
        input.toString()));

    assertEquals("""
        {"result":{"customer":{"id":"C1"}}}
        """, output);
  }

  @Test
  void cacheModeReusesExistingParquetCacheWithoutReadingSourceFile() throws Exception {
    Path cacheDir = tempDir.resolve("cache-parquet-reuse");
    Path script = write("query.nql", """
        output json;
        select c.id into {result.customer.id}
        from customer c;
        """);
    MessageType schema = ParquetTestFiles.schema("""
        message customer {
          required binary id (STRING);
        }
        """);
    SimpleGroupFactory factory = ParquetTestFiles.factory(schema);
    Path input = tempDir.resolve("input.parquet");
    ParquetTestFiles.write(input, schema, factory.newGroup().append("id", "C1"));

    String firstOutput = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", cacheDir.toString(),
        script.toString(),
        input.toString()));
    assertEquals("""
        {"result":{"customer":{"id":"C1"}}}
        """, firstOutput);
    Files.delete(input);

    String output = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", cacheDir.toString(),
        script.toString(),
        input.toString()));

    assertEquals("""
        {"result":{"customer":{"id":"C1"}}}
        """, output);
  }

  @Test
  void cacheModeSupportsNaturalKeyJoinsOverInputStructureTables() throws Exception {
    Path script = write("query.nql", """
        output json;
        select cn.name into {result.countryName}
        from customer cu
        inner join country cn on cn.ccode = cu.ccode
        where cu.id = 'C1';
        """);
    Path input = write("input.json", """
        {
          "data": {
            "customer": [
              {
                "id": "C1",
                "ccode": "90",
                "wallet": [
                  { "symbol": "GBP", "balance": "1.93" },
                  { "symbol": "AUD", "balance": "998.33" }
                ]
              },
              {
                "id": "C2",
                "ccode": "90",
                "wallet": [
                  { "symbol": "GBP", "balance": "89933.00" }
                ]
              }
            ],
            "country": [
              { "ccode": "89", "name": "vietnam" },
              { "ccode": "90", "name": "vatican city" }
            ]
          }
        }
        """);

    String output = captureStdout(() -> Main.main(
        script.toString(),
        input.toString(),
        "--cache",
        "--cache-dir", tempDir.resolve("cache-nested").toString()));

    assertEquals("""
        {"result":{"countryName":"vatican city"}}
        """, output);
  }

  @Test
  void cacheModeSupportsContainmentJoinsOverGeneratedRelationshipColumns() throws Exception {
    Path script = write("query.nql", """
        output json;
        select w.balance into {result.balance}
        from customer cu
        inner join wallet w on w.customer_id = cu.id
        where cu.id = 'C1'
          and w.symbol = 'AUD';
        """);
    Path input = write("input.json", """
        {
          "data": {
            "customer": [
              {
                "id": "C1",
                "wallet": [
                  { "symbol": "GBP", "balance": "1.93" },
                  { "symbol": "AUD", "balance": "998.33" }
                ]
              },
              {
                "id": "C2",
                "wallet": [
                  { "symbol": "GBP", "balance": "89933.00" }
                ]
              }
            ]
          }
        }
        """);

    String output = captureStdout(() -> Main.main(
        script.toString(),
        input.toString(),
        "--cache",
        "--cache-dir", tempDir.resolve("cache-contained").toString()));

    assertEquals("""
        {"result":{"balance":"998.33"}}
        """, output);
  }

  @Test
  void cacheModeMapsXmlAttributesToStructureColumns() throws Exception {
    Path script = write("query.nql", """
        output json;
        select
          id into {result.item.id},
          name into {result.item.name}
        from item
        where id = '7';
        """);
    Path input = write("input.xml", """
        <root>
          <item id="7"><name>Fred</name></item>
          <item id="8"><name>Wilma</name></item>
        </root>
        """);

    String output = captureStdout(() -> Main.main(
        "--cache",
        "--cache-dir", tempDir.resolve("cache-xml").toString(),
        script.toString(),
        input.toString()));

    assertEquals("""
        {"result":{"item":{"id":"7","name":"Fred"}}}
        """, output);
  }

  @Test
  void cacheModeWithoutInputRequiresAnActiveCache() throws Exception {
    Path script = write("query.nql", "select 1 into {result.value};");

    assertThrows(IllegalStateException.class,
        () -> Main.main(script.toString(), "--cache", "--cache-dir", tempDir.resolve("cache-missing").toString()));
  }

  @Test
  void unknownFlagFails() {
    assertThrows(Exception.class,
        () -> Main.main("-out", "result.xml"));
  }

  @Test
  void thirdPositionalArgumentFails() throws Exception {
    Path properties = propertiesFile(databaseUrl());
    Path script = write("script.nql", "select 1 as ONE\\G\n");
    Path firstLoadFile = write("first.xml", "<root/>");
    Path secondLoadFile = write("second.xml", "<root/>");

    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Main.main(
            script.toString(),
            firstLoadFile.toString(),
            secondLoadFile.toString(),
            "-p",
            properties.toString()));

    assertEquals("Unexpected argument: " + secondLoadFile, thrown.getMessage());
  }

  private String databaseUrl() {
    return "jdbc:h2:mem:hiql_" + UUID.randomUUID().toString().replace("-", "")
        + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
  }

  private Path propertiesFile(String databaseUrl) throws Exception {
    return write("hiql.properties", """
        jdbc.class.name=org.h2.Driver
        jdbc.database=%s
        jdbc.username=sa
        jdbc.password=
        """.formatted(databaseUrl));
  }

  private Path write(String name, String content) throws Exception {
    Path path = tempDir.resolve(name);
    Files.writeString(path, content);
    return path;
  }

  private void execute(Connection connection, String... sqlStatements) throws Exception {
    try (Statement statement = connection.createStatement()) {
      for (String sql : sqlStatements) {
        statement.execute(sql);
      }
    }
  }

  private int queryInt(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement();
         var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private String queryString(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement();
         var resultSet = statement.executeQuery(sql)) {
      if (!resultSet.next()) {
        return null;
      }
      return resultSet.getString(1);
    }
  }

  private String captureStdout(ThrowingRunnable runnable) throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setOut(capture);
      runnable.run();
    } finally {
      System.setOut(original);
    }
    return output.toString(StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
