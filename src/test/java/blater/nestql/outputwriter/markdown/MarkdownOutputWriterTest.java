package blater.nestql.outputwriter.markdown;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;
import blater.nestql.outputwriter.MarkdownOutputWriter;
import blater.nestql.outputwriter.OutputType;
import blater.nestql.outputwriter.OutputWriter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MarkdownOutputWriterTest {
  private static final int COLUMN_WIDTH = 35;

  @Test
  void factoryReturnsMarkdownOutputWriter() {
    assertInstanceOf(MarkdownOutputWriter.class, OutputWriter.of(OutputType.MARKDOWN));
  }

  @Test
  void repeatedRootChildrenRenderAsRowsWithStableColumns() {
    Node root = new Node("people");
    Node alice = person("1", "Alice");
    Node address = new Node("address");
    address.addNode(valueNode("city", "London"));
    alice.addNode(address);
    root.addNode(alice);
    root.addNode(person("2", "Bob"));

    assertEquals(table(
        List.of("id", "name", "address.city"),
        List.of("1", "Alice", "London"),
        List.of("2", "Bob", "")), MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void repeatedObjectsExpandIntoReadableRows() {
    Node root = new Node("person");
    root.addNode(valueNode("id", "1"));
    root.addNode(phone("111"));
    root.addNode(phone("222"));

    assertEquals(table(
        List.of("id", "phone.number"),
        List.of("1", "111"),
        List.of("1", "222")), MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void catalogColumnsRenderAsRowsInsteadOfJson() {
    Node root = new Node("catalog");
    Node table = new Node("table");
    table.addNode(valueNode("name", "ITEM"));
    table.addNode(valueNode("catalog", "CACHE"));
    table.addNode(valueNode("schema", "PUBLIC"));
    table.addNode(valueNode("type", "BASE TABLE"));
    Node columns = new Node("columns");
    columns.addNode(column("ID", "CHARACTER VARYING", "true", "1"));
    columns.addNode(column("CAPTION", "CHARACTER VARYING", "true", "2"));
    table.addNode(columns);
    root.addNode(table);

    assertEquals(table(
        List.of(
            "table.name",
            "table.catalog",
            "table.schema",
            "table.type",
            "table.columns.column.name",
            "table.columns.column.type",
            "table.columns.column.nullable",
            "table.columns.column.position"),
        List.of("ITEM", "CACHE", "PUBLIC", "BASE TABLE", "ID", "CHARACTER VARYING", "true", "1"),
        List.of("ITEM", "CACHE", "PUBLIC", "BASE TABLE", "CAPTION", "CHARACTER VARYING", "true", "2")),
        MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void attributesAndNullsRenderAsOrdinaryCells() {
    Node root = new Node("person");
    Node id = valueNode("id", "7");
    id.setAttribute(true);
    root.addNode(id);
    Node middleName = new Node("middleName");
    middleName.setNullValue(true);
    root.addNode(middleName);

    assertEquals(table(
        List.of("id", "middleName"),
        List.of("7", "")), MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void rendersCellContentLiterallyWithoutEscapes() {
    Node root = new Node("message");
    root.addNode(valueNode(
        "display_name",
        "A_B | *x* <b> & [x] \\ ok"));

    assertEquals(table(
        List.of("display_name"),
        List.of("A_B | *x* <b> & [x] \\ ok")),
        MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void truncatesCellsToTheFixedWidth() {
    Node root = new Node("result");
    root.addNode(valueNode("value", "1234567890123456789012345678901234567890"));

    assertEquals(table(
        List.of("value"),
        List.of("1234567890123456789012345678901234567890")),
        MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void emptyHierarchyMapsAsEmptyString() {
    assertEquals("", MarkdownOutputWriter.map(null));
    assertEquals("", MarkdownOutputWriter.map(new Hierarchy()));
  }

  private Node person(String id, String name) {
    Node person = new Node("person");
    person.addNode(valueNode("id", id));
    person.addNode(valueNode("name", name));
    return person;
  }

  private Node phone(String number) {
    Node phone = new Node("phone");
    phone.addNode(valueNode("number", number));
    return phone;
  }

  private Node column(String name, String type, String nullable, String position) {
    Node column = new Node("column");
    column.addNode(valueNode("name", name));
    column.addNode(valueNode("type", type));
    column.addNode(valueNode("nullable", nullable));
    column.addNode(valueNode("position", position));
    return column;
  }

  private Node valueNode(String name, String value) {
    Node node = new Node(name);
    node.setValue(value);
    return node;
  }

  @SafeVarargs
  private final String table(List<String> columns, List<String>... rows) {
    List<String> lines = new ArrayList<>();
    lines.add(row(columns));
    lines.add(row(columns.stream().map(ignored -> "-".repeat(COLUMN_WIDTH)).toList()));
    for (List<String> values : rows) {
      lines.add(row(values));
    }
    return String.join("", lines);
  }

  private String row(List<String> cells) {
    StringBuilder result = new StringBuilder("|");
    for (String cell : cells) {
      String value = cell.substring(0, Math.min(cell.length(), COLUMN_WIDTH));
      result.append(" ").append(value);
      result.append(" ".repeat(COLUMN_WIDTH - value.length())).append(" |");
    }
    return result.append("\n").toString();
  }
}
