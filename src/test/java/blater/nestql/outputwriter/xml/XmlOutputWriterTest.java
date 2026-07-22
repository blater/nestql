package blater.nestql.outputwriter.xml;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.HierarchyPath;
import blater.nestql.domain.Node;
import blater.nestql.outputwriter.XmlOutputWriter;
import blater.nestql.parser.script.NestStatement;
import blater.nestql.runner.sql.domain.QueryResultRow;
import blater.nestql.domain.CorrelationRule;
import blater.nestql.domain.MappingCondition;
import blater.nestql.domain.MappingPlan;
import blater.nestql.domain.OutputField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static blater.nestql.runner.correlation.TestCurrentQueryRows.row;
import static blater.nestql.runner.correlation.TestCurrentQueryRows.values;
import static blater.nestql.testsupport.XmlTestHelpers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlOutputWriterTest {
  @Test
  void writesMappedHierarchyAsXmlDocument() throws Exception {
    MappingPlan plan = new MappingPlan(
      List.of(
        new OutputField(HierarchyPath.fromDottedPath("people.person.@id"), "personid", null, List.of(), false),
        new OutputField(HierarchyPath.fromDottedPath("people.person.name"), "firstname", " ", List.of(), false),
        new OutputField(HierarchyPath.fromDottedPath("people.person.name"), "surname", " ", List.of(), false)
      ),
      List.of(new CorrelationRule(HierarchyPath.fromDottedPath("people.person"), List.of(MappingCondition.newValue("personid"))))
    );

    NestStatement statement = NestStatement.select("", plan, null);
    Hierarchy hierarchy = hierarchy(statement, List.of(
      personRow("1", "Alice", "Adams"),
      personRow("2", "Bob", "Baker")));

    var document = XmlOutputWriter.map(hierarchy);

    var people = document.getRootElement();
    assertEquals("people", people.getName());
    assertEquals(2, children(people, "person").size());
    assertEquals("1", children(people, "person").get(0).getAttributeValue("id"));
    assertChildText(children(people, "person").get(0), "name", "Alice Adams");
    assertEquals("2", children(people, "person").get(1).getAttributeValue("id"));
    assertChildText(child(people, "person"), "name", "Alice Adams");
  }

  @Test
  void appliesDefaultNamespaceToRootElement() throws Exception {
    MappingPlan plan = new MappingPlan(
      List.of(new OutputField(HierarchyPath.fromDottedPath("people.person.firstname"), "firstname", null, List.of(), false)),
      List.of());
    NestStatement statement = NestStatement.select("", plan, "urn:hiql:test");
    Hierarchy hierarchy = hierarchy(statement, List.of(row(values("firstname", "Alice"), "firstname")));

    var document = XmlOutputWriter.map(hierarchy);
    assertEquals("urn:hiql:test", document.getRootElement().getNamespaceURI());
  }

  @Test
  void anonymousRootRendersWithResultWrapper() {
    Node root = new Node("");
    root.addNode(festival("First"));
    root.addNode(festival("Second"));

    var document = XmlOutputWriter.map(new Hierarchy(root));

    assertEquals("result", document.getRootElement().getName());
    assertEquals(2, children(document.getRootElement(), "festival").size());
    assertChildText(children(document.getRootElement(), "festival").getFirst(), "name", "First");
  }

  private Node festival(String value) {
    Node festival = new Node("festival");
    Node name = new Node("name");
    name.setValue(value);
    festival.addNode(name);
    return festival;
  }

  private QueryResultRow personRow(String personId, String firstname, String surname) {
    return row(values("personid", personId, "firstname", firstname, "surname", surname), "personid");
  }

  private Hierarchy hierarchy(NestStatement statement, List<QueryResultRow> rows) {
    Hierarchy accumulator = new Hierarchy();
    accumulator.register(statement);
    for (QueryResultRow row : rows) {
      accumulator.readRow(row);
    }
    return accumulator;
  }

}
