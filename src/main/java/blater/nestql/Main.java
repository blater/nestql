package blater.nestql;

import blater.nestql.outputwriter.OutputType;
import blater.nestql.outputwriter.OutputWriter;
import blater.nestql.parser.ScriptLoader;
import blater.nestql.parser.ScriptParser;
import blater.nestql.parser.script.NestScript;
import blater.nestql.parser.script.NestStatement;
import blater.nestql.runner.ScriptRunner;
import blater.nestql.runner.sql.cache.CacheExecution;
import blater.nestql.runner.sql.cache.CacheSource;
import blater.nestql.runner.sql.cache.PersistentCache;

import java.util.List;
import java.util.Map;

import static blater.nestql.ParameterParser.*;

// Responsibility: orchestrates running a nestQL script.
public class Main {
  public static void main(String... args) throws Exception {
    var params = ParameterParser.parse(args);

    if (params.containsKey(HELP_PARAM)) {
      String topic = params.get(HELP_PARAM);
      if (BRIEF_HELP.equals(topic)) {
        Help.printBriefHelp();
      } else if (topic.isBlank()) {
        Help.printManPage();
      } else {
        Help.printCommandInfo(topic);
      }
    } else if (params.containsKey(CACHE_LIST_PARAM)) {
      PersistentCache.list(params);
    } else  if (params.containsKey(CACHE_CLEAR_TARGET_PARAM)
               || params.containsKey(CACHE_CLEAR_ALL_PARAM)
               || params.containsKey(CACHE_CLEAR_OLDER_THAN_PARAM)
    ) {
      PersistentCache.clear(params);
    } else if (params.containsKey(CATALOG_PATTERN_PARAM)) {
      String pattern = params.get(CATALOG_PATTERN_PARAM);
      NestScript script = new NestScript(List.of(NestStatement.catalog(pattern.isEmpty() ? null : pattern)));
      execute(script, params);
    } else if (Boolean.parseBoolean(params.get(CACHE_MODE_PARAM))
        && !params.containsKey(SCRIPT_FILE_PARAM)
        && !params.containsKey(SCRIPT_TEXT_PARAM)) {
      boolean loaded = CacheExecution.loadAndActivate(params);
      String source = CacheSource.normalizedSourcePath(params.get(INPUT_FILENAME)).toString();
      System.out.println((loaded ? "Loaded cache for " : "Using existing cache for ") + source);
    } else {
      String inputScript = params.containsKey(SCRIPT_TEXT_PARAM)
          ? ScriptLoader.loadText(params.get(SCRIPT_TEXT_PARAM))
          : ScriptLoader.load(params.get(SCRIPT_FILE_PARAM));
      NestScript script = ScriptParser.parse(inputScript);
      execute(script, params);
    }
  }

  private static void execute(NestScript script, Map<String, String> params) {
    OutputWriter.of(OutputType.outputTypeFor(script, params)).write(ScriptRunner.run(script, params));
  }
}
