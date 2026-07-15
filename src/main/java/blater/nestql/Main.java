package blater.nestql;

import blater.nestql.outputwriter.OutputType;
import blater.nestql.outputwriter.OutputWriter;
import blater.nestql.parser.ScriptLoader;
import blater.nestql.parser.ScriptParser;
import blater.nestql.parser.script.NestScript;
import blater.nestql.runner.ScriptRunner;
import blater.nestql.runner.sql.cache.PersistentCache;

import static blater.nestql.ParameterParser.*;

// Responsibility: orchestrates running a nestQL script.
public class Main {
  public static void main(String... args) throws Exception {
    var params = ParameterParser.parse(args);
    if (params.containsKey(HELP_PARAM)) {
      Help help = new Help();
      String topic = params.get(HELP_PARAM);
      if (topic.isBlank()) {
        help.printManPage();
      } else {
        help.printCommandInfo(topic);
      }
    } else if (params.containsKey(CACHE_LIST_PARAM)) {
      PersistentCache.list(params);
    } else  if (params.containsKey(CACHE_CLEAR_TARGET_PARAM)
               || params.containsKey(CACHE_CLEAR_ALL_PARAM)
               || params.containsKey(CACHE_CLEAR_OLDER_THAN_PARAM)
    ) {
      PersistentCache.clear(params);
    } else {
      String inputScript = params.containsKey(SCRIPT_TEXT_PARAM)
          ? ScriptLoader.loadText(params.get(SCRIPT_TEXT_PARAM))
          : ScriptLoader.load(params.get(SCRIPT_FILE_PARAM));
      NestScript script = ScriptParser.parse(inputScript);
      OutputWriter.of(OutputType.outputTypeFor(script, params)).write(ScriptRunner.run(script, params));
    }
  }
}
