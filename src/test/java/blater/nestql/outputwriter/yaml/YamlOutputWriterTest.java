package blater.nestql.outputwriter.yaml;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;
import blater.nestql.outputwriter.OutputType;
import blater.nestql.outputwriter.OutputWriter;
import blater.nestql.outputwriter.YamlOutputWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class YamlOutputWriterTest {
  @Test
  void factoryReturnsYamlOutputWriter() {
    assertInstanceOf(YamlOutputWriter.class, OutputWriter.of(OutputType.YAML));
  }

  @Test
  void mapsSimpleHierarchyAsYamlMapping() {
    Node root = new Node("people");
    Node person = new Node("person");
    person.addNode(valueNode("firstname", "Alice"));
    root.addNode(person);

    assertEquals("""
        people:
          person:
            firstname: "Alice"
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void repeatedSiblingNodesRenderAsSequences() {
    Node root = new Node("people");
    root.addNode(person("1", "Alice"));
    root.addNode(person("2", "Bob"));

    assertEquals("""
        people:
          person:
            -
              id: "1"
              firstname: "Alice"
            -
              id: "2"
              firstname: "Bob"
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void nullValuesRenderAsYamlNull() {
    Node root = new Node("person");
    Node middleName = new Node("middleName");
    middleName.setNullValue(true);
    root.addNode(middleName);

    assertEquals("""
        person:
          middleName: null
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void attributesRenderAsOrdinaryYamlProperties() {
    Node root = new Node("person");
    Node id = valueNode("id", "7");
    id.setAttribute(true);
    root.addNode(id);

    assertEquals("""
        person:
          id: "7"
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void attributeAndElementWithSameNamePreserveBothValues() {
    Node root = new Node("person");
    Node attributeId = valueNode("id", "7");
    attributeId.setAttribute(true);
    root.addNode(attributeId);
    root.addNode(valueNode("id", "internal"));

    assertEquals("""
        person:
          id:
            - "7"
            - "internal"
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  @Test
  void emptyHierarchyMapsAsEmptyYamlObject() {
    assertEquals("{}", YamlOutputWriter.map(new Hierarchy()));
  }

  @Test
  void escapesYamlStrings() {
    Node root = new Node("message");
    root.addNode(valueNode("text", "quote \" slash \\ newline\n"));

    assertEquals("""
        message:
          text: "quote \\" slash \\\\ newline\\n"
        """, YamlOutputWriter.map(new Hierarchy(root)));
  }

  private Node person(String id, String firstname) {
    Node person = new Node("person");
    person.addNode(valueNode("id", id));
    person.addNode(valueNode("firstname", firstname));
    return person;
  }

  private Node valueNode(String name, String value) {
    Node node = new Node(name);
    node.setValue(value);
    return node;
  }
}
