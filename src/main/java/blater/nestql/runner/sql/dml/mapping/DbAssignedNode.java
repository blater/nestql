package blater.nestql.runner.sql.dml.mapping;

import blater.nestql.domain.Node;

/*
 * Responsibility: Identifies one input node that should receive a
 * database-assigned value for a named SQL column.
 */
public record DbAssignedNode(
  Node node,
  String columnName
) {}
