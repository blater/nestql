package blater.nestql.runner.sql.dml;

import blater.nestql.parser.script.NestStatement;
import blater.nestql.runner.sql.domain.SqlRow;
import blater.nestql.runner.sql.domain.SqlStatement;
import blater.nestql.runner.sql.dml.statementbuilder.DeleteStatementBuilder;
import blater.nestql.runner.sql.SqlExecutor;
import blater.nestql.util.Log;

/*
 * Responsibility: Builds and executes one DELETE statement for one
 * mapped input row.
 */
public final class RunDelete {
  public RunDelete() {}

  public static void execute(NestStatement stmt, SqlRow row, SqlExecutor sqlExecutor) {
    row = row.withColumnTypes(sqlExecutor.columnTypes(stmt.getTargetName()));
    SqlStatement deleteStmt = DeleteStatementBuilder.build(stmt, row);

    if (!sqlExecutor.checkStatementError(deleteStmt.getStatus(), stmt.getErrorHandling())) {
      return;
    }

    Log.debug("DELETE [{}]", deleteStmt.getSql());
    int rowcount = sqlExecutor.run(deleteStmt, stmt.getErrorHandling());
    Log.debug("Rows affected = {}", rowcount);
    if (rowcount < 0) {
      Log.error("Problem running a delete statement [{}]", deleteStmt.getSql());
    }
  }
}
