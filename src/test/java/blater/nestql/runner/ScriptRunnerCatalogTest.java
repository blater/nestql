package blater.nestql.runner;

import blater.nestql.ParameterParser;
import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.HierarchyPath;
import blater.nestql.domain.Node;
import blater.nestql.outputwriter.JsonOutputWriter;
import blater.nestql.parser.ScriptParser;
import blater.nestql.testsupport.H2Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptRunnerCatalogTest {
  @TempDir
  Path tempDir;

  @Test
  void catalogStatementOutputsUserTablesAndColumnsFromConfiguredDatabase() throws Exception {
    try (H2Database database = new H2Database()) {
      database.execute(
          "create table person (personid integer not null, firstname varchar(80))",
          "create table audit_log (id integer primary key, message varchar(80))",
          "create view person_view as select personid, firstname from person");

      Hierarchy catalog = ScriptRunner.run(ScriptParser.parse("catalog;"), database.jdbcProperties());

      assertEquals("catalog", catalog.getRoot().getName());
      assertTrue(containsValue(values(catalog, "catalog.table.name"), "person"));
      assertTrue(containsValue(values(catalog, "catalog.table.name"), "audit_log"));
      assertFalse(containsValue(values(catalog, "catalog.table.name"), "person_view"));
      assertTrue(containsValue(values(catalog, "catalog.table.columns.column.name"), "personid"));
      assertTrue(containsValue(values(catalog, "catalog.table.columns.column.name"), "firstname"));

      String json = JsonOutputWriter.map(catalog);
      assertTrue(json.contains("\"catalog\""));
      assertTrue(json.contains("\"table\""));
      assertTrue(json.contains("\"columns\""));
      assertTrue(json.contains("\"column\""));
    }
  }

  @Test
  void catalogStatementOutputsCacheDatabaseTablesAndColumns() throws Exception {
    Path input = tempDir.resolve("customers.json");
    Files.writeString(input, """
        {
          "customers": {
            "person": [
              { "id": "P1", "firstname": "Fred" },
              { "id": "P2", "firstname": "Wilma" }
            ]
          }
        }
        """, StandardCharsets.UTF_8);

    Map<String, String> params = new LinkedHashMap<>();
    params.put(ParameterParser.CACHE_MODE_PARAM, "true");
    params.put(ParameterParser.INPUT_FILENAME, input.toString());
    params.put(ParameterParser.CACHE_DIR_PARAM, tempDir.resolve("cache").toString());

    Hierarchy catalog = ScriptRunner.run(ScriptParser.parse("catalog;"), params);

    assertTrue(containsValue(values(catalog, "catalog.table.name"), "person"));
    assertTrue(containsValue(values(catalog, "catalog.table.columns.column.name"), "id"));
    assertTrue(containsValue(values(catalog, "catalog.table.columns.column.name"), "firstname"));
  }

  private List<String> values(Hierarchy hierarchy, String path) {
    return hierarchy.select(HierarchyPath.fromDottedPath(path)).stream()
        .map(Node::getValue)
        .toList();
  }

  private boolean containsValue(List<String> values, String expected) {
    return values.stream().anyMatch(value -> expected.equalsIgnoreCase(value));
  }
}
