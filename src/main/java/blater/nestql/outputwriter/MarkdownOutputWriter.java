package blater.nestql.outputwriter;

import blater.nestql.domain.Hierarchy;
import blater.nestql.domain.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Responsibility: Renders a hierarchy as one Markdown table by flattening
 * record nodes into dotted columns. Repeated hierarchical values that cannot
 * occupy their own rows are represented within a single table cell.
 */
public final class MarkdownOutputWriter implements OutputWriter {
  @Override
  public void write(Hierarchy result) {
    String markdown = map(result);
    if (!markdown.isEmpty()) {
      System.out.print(markdown); //NOPMD - suppressed SystemPrintln - legitimate CLI output
    }
  }

  public static String map(Hierarchy hierarchy) {
    Node root = hierarchy == null ? null : hierarchy.getRoot();
    if (root == null || root.getName() == null || root.getName().isEmpty()) {
      return "";
    }

    List<Map<String, String>> rows = new ArrayList<>();
    Set<String> columns = new LinkedHashSet<>();
    for (Node record : recordNodes(root)) {
      Map<String, String> row = flattenRecord(record);
      rows.add(row);
      columns.addAll(row.keySet());
    }
    if (columns.isEmpty()) {
      return "";
    }
    return writeTable(new ArrayList<>(columns), rows);
  }

  private static List<Node> recordNodes(Node root) {
    Map<String, List<Node>> children = groupedChildren(root);
    if (children.size() == 1) {
      List<Node> onlyChildGroup = children.values().iterator().next();
      if (onlyChildGroup.size() > 1) {
        return onlyChildGroup;
      }
    }
    return List.of(root);
  }

  private static Map<String, String> flattenRecord(Node record) {
    Map<String, String> row = new LinkedHashMap<>();
    flattenNode(record, "", row);
    return row;
  }

  private static void flattenNode(Node node, String path, Map<String, String> row) {
    if (node.isNull()) {
      row.put(pathOrNodeName(path, node), "");
      return;
    }
    if (node.hasValue()) {
      row.put(pathOrNodeName(path, node), node.getValue());
      return;
    }

    for (Map.Entry<String, List<Node>> entry : groupedChildren(node).entrySet()) {
      String childPath = childPath(path, entry.getKey());
      List<Node> children = entry.getValue();
      if (children.size() == 1) {
        flattenNode(children.getFirst(), childPath, row);
      } else if (children.stream().allMatch(MarkdownOutputWriter::isScalarNode)) {
        row.put(childPath, joinedScalarValues(children));
      } else {
        row.put(childPath, jsonArray(children));
      }
    }
  }

  private static String pathOrNodeName(String path, Node node) {
    return path == null || path.isEmpty() ? node.getName() : path;
  }

  private static String childPath(String parentPath, String childName) {
    return parentPath == null || parentPath.isEmpty()
        ? childName
        : parentPath + "." + childName;
  }

  private static boolean isScalarNode(Node node) {
    return node.isNull() || node.hasValue();
  }

  private static String joinedScalarValues(List<Node> nodes) {
    return nodes.stream()
        .map(node -> node.isNull() ? "" : node.getValue())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private static String writeTable(List<String> columns, List<Map<String, String>> rows) {
    StringBuilder markdown = new StringBuilder();
    writeRow(markdown, columns);
    writeRow(markdown, columns.stream().map(ignored -> "---").toList());
    for (Map<String, String> row : rows) {
      writeRow(markdown, columns.stream()
          .map(column -> row.getOrDefault(column, ""))
          .toList());
    }
    return markdown.toString();
  }

  private static void writeRow(StringBuilder markdown, List<String> cells) {
    markdown.append("|");
    for (String cell : cells) {
      markdown.append(" ").append(escapeCell(cell)).append(" |");
    }
    markdown.append("\n");
  }

  private static String escapeCell(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
    StringBuilder escaped = new StringBuilder(normalized.length());
    for (int index = 0; index < normalized.length(); index++) {
      char ch = normalized.charAt(index);
      switch (ch) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '\n' -> escaped.append("<br>");
        case '\\', '`', '*', '_', '[', ']', '~', '|' -> escaped.append('\\').append(ch);
        default -> escaped.append(ch);
      }
    }
    return escaped.toString();
  }

  private static Map<String, List<Node>> groupedChildren(Node node) {
    Map<String, List<Node>> grouped = new LinkedHashMap<>();
    for (Node child : node.getChildren()) {
      grouped.computeIfAbsent(child.getName(), ignored -> new ArrayList<>()).add(child);
    }
    return grouped;
  }

  private static String jsonArray(List<Node> nodes) {
    StringBuilder json = new StringBuilder("[");
    for (int index = 0; index < nodes.size(); index++) {
      if (index > 0) {
        json.append(",");
      }
      writeJsonValue(json, nodes.get(index));
    }
    return json.append("]").toString();
  }

  private static void writeJsonValue(StringBuilder json, Node node) {
    if (node.isNull()) {
      json.append("null");
    } else if (node.hasValue()) {
      json.append(jsonQuote(node.getValue()));
    } else {
      writeJsonObject(json, node);
    }
  }

  private static void writeJsonObject(StringBuilder json, Node node) {
    json.append("{");
    boolean first = true;
    for (Map.Entry<String, List<Node>> entry : groupedChildren(node).entrySet()) {
      if (!first) {
        json.append(",");
      }
      first = false;
      json.append(jsonQuote(entry.getKey())).append(":");
      List<Node> children = entry.getValue();
      if (children.size() == 1) {
        writeJsonValue(json, children.getFirst());
      } else {
        json.append(jsonArray(children));
      }
    }
    json.append("}");
  }

  private static String jsonQuote(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      switch (ch) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (ch < 0x20) {
            escaped.append(String.format("\\u%04x", (int) ch));
          } else {
            escaped.append(ch);
          }
        }
      }
    }
    return escaped.append('"').toString();
  }
}
