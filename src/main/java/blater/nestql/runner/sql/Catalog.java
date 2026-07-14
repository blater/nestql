package blater.nestql.runner.sql;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;
import blater.nestql.util.Log;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/*
 * Responsibility: Reads JDBC database metadata into the standard output hierarchy.
 */
public final class Catalog {
  private static final Set<String> USER_TABLE_TYPES = Set.of("TABLE", "BASE TABLE");
  private static final Set<String> SYSTEM_NAMES = Set.of(
      "CTXSYS",
      "DBSNMP",
      "INFORMATION_SCHEMA",
      "MDSYS",
      "MYSQL",
      "OUTLN",
      "PERFORMANCE_SCHEMA",
      "PG_CATALOG",
      "PG_TOAST",
      "SYS",
      "SYSCAT",
      "SYSFUN",
      "SYSIBM",
      "SYSIBMADM",
      "SYSPROC",
      "SYSSTAT",
      "SYSTEM",
      "SYSTEM_LOBS",
      "XDB");

  private Catalog() { }

  public static Hierarchy read(Connection connection) {
    Node root = new Node("catalog");
    try {
      DatabaseMetaData metadata = connection.getMetaData();
      try (ResultSet tables = metadata.getTables(null, null, "%", USER_TABLE_TYPES.toArray(String[]::new))) {
        while (tables.next()) {
          addUserTable(root, metadata, tables);
        }
      }
      return new Hierarchy(root);
    } catch (SQLException ex) {
      return Log.fatal(IllegalStateException.class, "Could not read database catalog.", ex);
    }
  }

  private static void addUserTable(Node root, DatabaseMetaData metadata, ResultSet tables) throws SQLException {
    String catalog = tables.getString("TABLE_CAT");
    String schema = tables.getString("TABLE_SCHEM");
    if (!isUserSchema(catalog, schema)) {
      return;
    }

    root.addNode(tableNode(
        metadata,
        catalog,
        schema,
        tables.getString("TABLE_NAME"),
        tables.getString("TABLE_TYPE")));
  }

  private static Node tableNode(
      DatabaseMetaData metadata,
      String catalog,
      String schema,
      String tableName,
      String tableType) throws SQLException {

    Node tableNode = new Node("table");
    addValue(tableNode, "name", tableName);
    addOptionalValue(tableNode, "catalog", catalog);
    addOptionalValue(tableNode, "schema", schema);
    addValue(tableNode, "type", tableType);

    Node columnsNode = new Node("columns");
    tableNode.addNode(columnsNode);
    String searchEscape = metadata.getSearchStringEscape();
    try (ResultSet columns = metadata.getColumns(
        catalog,
        exactPattern(schema, searchEscape),
        exactPattern(tableName, searchEscape),
        "%")) {
      while (columns.next()) {
        columnsNode.addNode(columnNode(columns));
      }
    }
    return tableNode;
  }

  private static Node columnNode(ResultSet columns) throws SQLException {
    Node columnNode = new Node("column");
    addValue(columnNode, "name", columns.getString("COLUMN_NAME"));
    addValue(columnNode, "type", columns.getString("TYPE_NAME"));
    addValue(columnNode, "nullable", Boolean.toString(columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable));
    addValue(columnNode, "position", columns.getString("ORDINAL_POSITION"));
    return columnNode;
  }

  private static boolean isUserSchema(String catalog, String schema) {
    return !isSystemName(catalog) && !isSystemName(schema);
  }

  private static boolean isSystemName(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    String normalized = normalize(name);
    return SYSTEM_NAMES.contains(normalized)
        || normalized.startsWith("SYS_");
  }

  private static void addOptionalValue(Node parent, String name, String value) {
    if (value != null && !value.isBlank()) {
      addValue(parent, name, value);
    }
  }

  private static void addValue(Node parent, String name, String value) {
    Node child = new Node(name);
    child.setValue(value == null ? "" : value);
    parent.addNode(child);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String exactPattern(String value, String searchEscape) {
    if (value == null || searchEscape == null || searchEscape.isEmpty()) {
      return value;
    }
    return value
        .replace(searchEscape, searchEscape + searchEscape)
        .replace("%", searchEscape + "%")
        .replace("_", searchEscape + "_");
  }

}
