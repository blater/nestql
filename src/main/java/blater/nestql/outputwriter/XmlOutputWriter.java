package blater.nestql.outputwriter;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;
import blater.nestql.util.Log;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/*
 * Responsibility: Renders a tree as a JDOM XML document and applies the
 * XML-only namespace hint. Nodes flagged as attributes render as attributes
 * on their parent element.
 */
public class XmlOutputWriter implements OutputWriter {
  public void write(Hierarchy doc) {
    if (doc == null || doc.isEmpty()) {
      return;
    }
    Document xml = map(doc);
    XMLOutputter xmlout = new XMLOutputter();
    xmlout.setFormat(Format.getPrettyFormat());
    try {
      xmlout.output(xml, System.out);
    } catch (Exception ex) {
      Log.error("Exception writing the result XML", ex);
    }
  }

  public static Document map(Hierarchy hierarchy) {
    Node rootNode = hierarchy.getRoot();
    if (rootNode == null || rootNode.getName() == null || rootNode.getName().isEmpty())
      return new Document(); // empty doc

    var rootElement = new Element(rootNode.getName());
    if (hierarchy.hasNamespace())
      rootElement.setNamespace(Namespace.getNamespace(hierarchy.getNamespace()));

    writeChildren(rootElement, rootNode);
    return new Document(rootElement);
  }

  private static void writeChildren(Element parent, Node node) {
    for (var child : node.getChildren()) {
      if (child.isAttribute()) {
        writeAttribute(parent, child);
      } else {
        writeNode(parent, child);
      }
    }
  }

  private static String nodeName(Node node) {
    return node.getName();
  }

  private static void writeAttribute(Element parent, Node node) {
    if (!node.hasValue()) {
      Log.fatal(IllegalArgumentException.class, "XML attribute path cannot point to a container node: " + node.getName());
    }
    parent.setAttribute(node.getName(), node.getValue());
  }

  private static void writeNode(Element parent, Node node) {
    var child = new Element(nodeName(node));
    if (node.hasValue()) {
      if (node.isNull()) {
        child.setAttribute("nil", "true");
      } else {
        child.setText(node.getValue());
      }
    } else {
      writeChildren(child, node);
    }
    parent.addContent(child);
  }
}
