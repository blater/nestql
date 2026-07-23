package blater.nestql.runner.sql.domain;

import blater.nestql.domain.SqlType;

import static blater.nestql.runner.sql.domain.ColumnDataSourceType.*;

/*
 * Responsibility: Describes one target SQL column and its execution
 * metadata for DML statement building.
 */
public record ColumnDefinition(
  String sqlName,
  SqlType sqlType,
  String sqlFunction,
  boolean key,
  int keyNumber,
  ColumnDataSourceType columnDataSourceType
) {
  public static final int NOT_A_KEY = -99;

  public ColumnDefinition {
    columnDataSourceType = columnDataSourceType == null ? NORMAL : columnDataSourceType;
    keyNumber = key ? keyNumber : NOT_A_KEY;
    if (sqlType == null) {
      sqlType = SqlType.STRING;
    }
  }

  public boolean isUid() {
    return columnDataSourceType == GENERATED_UID;
  }

  public boolean isDbAssigned() {
    return columnDataSourceType == DB_ASSIGNED;
  }
}
