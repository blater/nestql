package blater.nestql.domain;

import java.util.List;

/**
 * Declares the internal result columns that identify one object at an output path.
 */
public record KeyedPath(HierarchyPath path, List<String> sourceColumns) {
  public KeyedPath {
    sourceColumns = List.copyOf(sourceColumns);
  }
}
