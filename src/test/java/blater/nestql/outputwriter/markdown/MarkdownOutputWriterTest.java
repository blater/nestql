package blater.nestql.outputwriter.markdown;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;
import blater.nestql.outputwriter.MarkdownOutputWriter;
import blater.nestql.outputwriter.OutputType;
import blater.nestql.outputwriter.OutputWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MarkdownOutputWriterTest {
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

    assertEquals("""
        | id | name | address.city |
        | --- | --- | --- |
        | 1 | Alice | London |
        | 2 | Bob |  |
        """, MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void repeatedValuesStayWithinTheirRecordCell() {
    Node root = new Node("person");
    root.addNode(valueNode("id", "1"));
    root.addNode(valueNode("role", "admin"));
    root.addNode(valueNode("role", "editor"));
    root.addNode(phone("111"));
    root.addNode(phone("222"));

    assertEquals("""
        | id | role | phone |
        | --- | --- | --- |
        | 1 | admin<br>editor | \\[{"number":"111"},{"number":"222"}\\] |
        """, MarkdownOutputWriter.map(new Hierarchy(root)));
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

    assertEquals("""
        | id | middleName |
        | --- | --- |
        | 7 |  |
        """, MarkdownOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void escapesMarkdownHtmlPipesAndLineBreaksInsideCells() {
    Node root = new Node("message");
    root.addNode(valueNode(
        "display_name",
        "A | *bold* <tag> & [link](url)\r\nnext \\ path"));

    assertEquals("""
        | display\\_name |
        | --- |
        | A \\| \\*bold\\* &lt;tag&gt; &amp; \\[link\\](url)<br>next \\\\ path |
        """, MarkdownOutputWriter.map(new Hierarchy(root)));
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

  private Node valueNode(String name, String value) {
    Node node = new Node(name);
    node.setValue(value);
    return node;
  }
}
