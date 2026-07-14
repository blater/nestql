package blater.nestql.runner.sql.dml;

import blater.nestql.parser.script.NestStatement;
import blater.nestql.parser.script.ErrorStrategy;
import blater.nestql.runner.sql.domain.DmlExecutionResult;
import blater.nestql.runner.sql.domain.SqlRow;
import blater.nestql.runner.sql.domain.SqlStatement;
import blater.nestql.runner.sql.dml.statementbuilder.InsertStatementBuilder;
import blater.nestql.runner.sql.SqlExecutor;
import blater.nestql.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Responsibility: Builds and executes one INSERT statement for one
 * mapped input row.
 */
public class RunInsert {
  private RunInsert() {}

  public static DmlExecutionResult execute(NestStatement scriptStmt, SqlRow row, SqlExecutor sqlExecutor) {
    if (row.hasPositionColumns()) {
      row = row.withPositionColumnNames(sqlExecutor.columnNames(scriptStmt.getTargetName()));
    }
    row = row.withColumnTypes(sqlExecutor.columnTypes(scriptStmt.getTargetName()));
    SqlStatement insert = InsertStatementBuilder.build(scriptStmt, row);
    if (!sqlExecutor.checkStatementError(insert.getStatus(), scriptStmt.getErrorHandling())) {
      return DmlExecutionResult.EMPTY;
    }
    Log.debug("INSERT [{}]", insert.getSql());

    ErrorStrategy errorHandling = scriptStmt.getErrorHandling();
    Map<String, String> dbAssignedValues = new LinkedHashMap<>();

    String generatedKey = null;
    if (insert.hasIdentityColumn() || !scriptStmt.getReturnMappings().isEmpty()) {
      generatedKey = sqlExecutor.insertWithIdentity(insert, errorHandling);
      if (insert.hasIdentityColumn()) {
        dbAssignedValues.put(insert.getDbAssignedIdentityColumn(), generatedKey);
      }
    } else {
      int rowcount = sqlExecutor.run(insert, errorHandling);
      if (rowcount < 0) {
        Log.error("Problem running an insert statement [{}]", insert.getSql());
        return DmlExecutionResult.EMPTY;
      }
      Log.debug("Rows affected = {}", rowcount);
    }

    if (insert.hasDbAssignedColumns()) {
      dbAssignedValues.putAll(
          RunSelectDbAssignedValues.runSelect(
            insert.getDbAssignedValuesSql(generatedKey),
            insert.getDbAssignedValuesParameters(generatedKey),
            insert.getDbAssignedColumns(),
            sqlExecutor));
    }
    dbAssignedValues.putAll(RunSelectDmlReturnValues.selectForInsert(scriptStmt, row, generatedKey, sqlExecutor));
    return DmlExecutionResult.of(dbAssignedValues);
  }
}
