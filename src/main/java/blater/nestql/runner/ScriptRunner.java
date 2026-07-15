package blater.nestql.runner;

import blater.nestql.domain.Hierarchy;
import blater.nestql.inputreader.InputReader;
import blater.nestql.inputreader.InputType;
import blater.nestql.parser.script.NestScript;
import blater.nestql.parser.script.NestStatement;
import blater.nestql.runner.sql.Capture;
import blater.nestql.runner.sql.cache.CacheExecution;
import blater.nestql.runner.sql.dml.*;
import blater.nestql.runner.sql.dml.mapping.InputFileRowMapper;
import blater.nestql.runner.sql.dml.mapping.MappingResult;
import blater.nestql.runner.sql.domain.DmlExecutionResult;
import blater.nestql.runner.sql.domain.SqlRow;
import blater.nestql.runner.sql.query.RunQuery;
import blater.nestql.runner.sql.SqlExecutor;
import blater.nestql.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static blater.nestql.ParameterParser.INPUT_FILENAME;
import static blater.nestql.util.ValueUtil.has;

// `AGENTS MUST  NOT REMOVE *ANY* COMMENTS
/*
 * Responsibility: Dispatches an already parsed script against the
 * active SQL connection and optional file input.
 */
public final class ScriptRunner {
  private ScriptRunner() { }

  public static Hierarchy run(NestScript script, Map<String, String> params) {
    if (script == null || script.statements().isEmpty())
      return null;

    SqlExecutor sqlExecutor = CacheExecution.openForQuery(params)
        .orElseGet(() -> new SqlExecutor(params));
    try {
      final InputFileRowMapper inputFileRowMapper = new InputFileRowMapper();
      Hierarchy inputHierarchy = null;
      Map<String, List<Map<String, Object>>> captureRowSets = new HashMap<>();
      Hierarchy hierarchy =  null;

      for (NestStatement stmt : script.statements()) {
        switch (stmt.getType()) {
          case AUTOCOMMIT ->  sqlExecutor.setAutoCommit(has(stmt.getTargetName()) && stmt.getTargetName().equals("true"));

          case CAPTURE -> captureRowSets.putAll(Capture.captureTempRowset(stmt, params, sqlExecutor));

          case CATALOG -> hierarchy = sqlExecutor.catalog();

          case SELECT -> hierarchy = RunQuery.runQuery(stmt, params, hierarchy, sqlExecutor);

          case LITERAL -> RunLiteralSql.execute(stmt, params, sqlExecutor);

          case INSERT, UPDATE, DELETE, PROC -> {
            if (inputDataIsFromFile(stmt)) {
              if (inputHierarchy == null)  {
                String inputFilename = params.get(INPUT_FILENAME);
                inputHierarchy = InputReader.of(InputType.fromFilename(inputFilename)).load(inputFilename, params);
              }
              runDmlForInputFile(stmt, inputHierarchy, params, inputFileRowMapper, sqlExecutor);

            } else {
              // use rows captured from a preceding 'capture' statement; each is mapped to a SqlRow & DML run with it
              List<Map<String, Object>> rows = captureRowSets.get(stmt.getSourceRowsetName());
              if (rows == null)
                Log.fatal(IllegalArgumentException.class, "No temp rowset named: " + stmt.getSourceRowsetName());

              // run the statement against each captured row one by one
              // annoying for more than a couple of dozen rows, bad for > 1k rows, unusable for >10k
              //  todo:
              //   add captures at time of capture into in-memory temp table & reformulate the dml
              //   statement dynamically to reference the temp table.
              //   for small row sets, similar or less efficent, for >1K rows, 100s of times more efficent, for >100k rows thousands of times more efficient
              for (Map<String, Object> capturedRow : rows)
                runDml(stmt, Capture.toSqlRow(stmt.getMappings(), capturedRow, params), sqlExecutor);
            }
          }
        }
      }
      if (inputHierarchy != null) {
        return inputHierarchy;
      }
      // Refactor note: callers expect DML-only scripts to return an empty hierarchy, not null.
      return hierarchy == null ? new Hierarchy() : hierarchy;
    } finally {
      sqlExecutor.close();
    }
  }

  private static boolean inputDataIsFromFile(NestStatement stmt) {
    return stmt.getSourceRowsetName() == null;
  }

  private static void runDmlForInputFile(NestStatement stmt, Hierarchy inputDataFile, Map<String, String> parameters, InputFileRowMapper inputFileRowMapper, SqlExecutor sqlExecutor) {
    MappingResult mapping = inputFileRowMapper.map(inputDataFile, stmt.getMappings(), stmt.getReturnMappings(), parameters);
    if (mapping.hasProblem()) {
      sqlExecutor.checkStatementError(mapping.problemStatus(), stmt.getErrorHandling());
      return;
    }

    for (SqlRow row : mapping.rows()) {
      DmlExecutionResult result = runDml(stmt, row, sqlExecutor);
      inputFileRowMapper.applyWriteBack(row, result);
    }
  }




  private static DmlExecutionResult runDml(NestStatement stmt, SqlRow row, SqlExecutor sqlExecutor) {
    return switch (stmt.getType()) {
      case INSERT -> RunInsert.execute(stmt, row, sqlExecutor);
      case UPDATE -> RunUpdate.execute(stmt, row, sqlExecutor);
      case DELETE -> {
        RunDelete.execute(stmt, row, sqlExecutor);
        yield DmlExecutionResult.EMPTY;
      }
      case PROC -> RunProcedure.execute(stmt, row, sqlExecutor);
      default -> Log.fatal(IllegalStateException.class, "executeDml called with non-DML type: " + stmt.getType());
    };
  }
}
