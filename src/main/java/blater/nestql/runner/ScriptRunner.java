package blater.nestql.runner;

import blater.nestql.domain.Hierarchy;
import blater.nestql.inputreader.InputReader;
import blater.nestql.inputreader.InputType;
import blater.nestql.parser.script.NestScript;
import blater.nestql.parser.script.NestStatement;
import blater.nestql.runner.sql.Capture;
import blater.nestql.runner.sql.cache.CacheHandle;
import blater.nestql.runner.sql.cache.CacheSource;
import blater.nestql.runner.sql.dml.*;
import blater.nestql.runner.sql.dml.mapping.InputFileRowMapper;
import blater.nestql.runner.sql.dml.mapping.MappingResult;
import blater.nestql.runner.sql.domain.DmlExecutionResult;
import blater.nestql.runner.sql.domain.SqlRow;
import blater.nestql.runner.sql.query.RunQuery;
import blater.nestql.runner.sql.SqlExecutor;
import blater.nestql.runner.sql.cache.HierarchyCacheLoader;
import blater.nestql.runner.sql.cache.PersistentCache;
import blater.nestql.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static blater.nestql.ParameterParser.CACHE_MODE_PARAM;
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

    CacheHandle cacheHandle = cacheMode(params) ? cacheHandle(params) : null;
    Map<String, String> sqlParams = cacheHandle != null ? cacheJdbcParams(params, cacheHandle.jdbcUrl()) : params;
    SqlExecutor sqlExecutor = new SqlExecutor(sqlParams);
    try {
      if (cacheHandle != null && cacheHandle.needsLoad()) {
        Hierarchy cacheInput = loadCacheInput(params.get(INPUT_FILENAME), params);
        new HierarchyCacheLoader(sqlExecutor).load(cacheInput);
        PersistentCache.markLoaded(cacheHandle);
      }

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
                inputHierarchy = InputReader.of(inputTypeFor(inputFilename)).load(inputFilename, params);
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

  private static boolean cacheMode(Map<String, String> params) {
    return params != null && Boolean.parseBoolean(params.get(CACHE_MODE_PARAM));
  }

  private static CacheHandle cacheHandle(Map<String, String> params) {
    String inputFilename = requireCacheInput(params);
    InputType inputType = inputTypeFor(inputFilename);
    return PersistentCache.prepare(
        CacheSource.from(inputFilename, inputType, params),
        params);
  }

  private static Map<String, String> cacheJdbcParams(Map<String, String> params, String jdbcUrl) {
    Map<String, String> cacheParams = new HashMap<>(params);
    cacheParams.put("jdbc.class.name", "org.h2.Driver");
    cacheParams.put("jdbc.database", jdbcUrl);
    cacheParams.put("jdbc.username", "sa");
    cacheParams.put("jdbc.password", "");
    return cacheParams;
  }

  private static String requireCacheInput(Map<String, String> params) {
    String inputFilename = params.get(INPUT_FILENAME);
    if (inputFilename == null || inputFilename.isBlank()) {
      return Log.fatal(IllegalArgumentException.class, "--cache requires an input file.");
    }
    return inputFilename;
  }

  private static Hierarchy loadCacheInput(String inputFilename, Map<String, String> params) {
    return InputReader.of(inputTypeFor(inputFilename)).load(inputFilename, params);
  }

  static InputType inputTypeFor(String filename) {
    if (filename == null || filename.isBlank()) {
      return InputType.XML;
    }
    String normalized = filename.toLowerCase(Locale.ROOT);
    if (normalized.endsWith(".xml")) {
      return InputType.XML;
    }
    if (normalized.endsWith(".json")) {
      return InputType.JSON;
    }
    if (normalized.endsWith(".yaml") || normalized.endsWith(".yml")) {
      return InputType.YAML;
    }
    if (normalized.endsWith(".csv")) {
      return InputType.CSV;
    }
    if (normalized.endsWith(".parquet")) {
      return InputType.PARQUET;
    }
    return Log.fatal(IllegalArgumentException.class, "Unsupported input file type: " + filename);
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
