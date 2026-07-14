package blater.nestql.runner.sql.domain;

/*
 * Responsibility: For mapping a xpath defined field from an input data file into insert/update statements
 * Holds the source xpath and what sql table/column it maps to for one XML-to-SQL column mapping.
 * TODO: confirm how does it know what table??
 */
public record InputToColumnMap(
    ColumnDefinition columnDefinition,
    String xpathMapping,
    String defaultValue,
    boolean literal) {
}
