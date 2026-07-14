package blater.nestql.e2e;

import blater.nestql.ParameterParser;
import blater.nestql.outputwriter.JsonOutputWriter;
import blater.nestql.parser.ScriptLoader;
import blater.nestql.parser.ScriptParser;
import blater.nestql.parser.script.NestScript;
import blater.nestql.runner.ScriptRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheQueryExamplesE2ETest {
  @TempDir
  Path tempDir;

  @Test
  void aggregatesIdentityCustomersByResidentialAddressCountry() throws Exception {
    NestScript script = ScriptParser.parse(ScriptLoader.load(
        Path.of("docs", "examples", "identity-country-counts.nql").toString()));

    for (String filename : List.of(
        "identity-customers.json",
        "identity-customers.yaml",
        "identity-customers.xml")) {

      var hierarchy = ScriptRunner.run(script, Map.of(
          ParameterParser.CACHE_MODE_PARAM, "true",
          ParameterParser.CACHE_DIR_PARAM, tempDir.resolve("cache-" + filename).toString(),
          ParameterParser.INPUT_FILENAME, Path.of("docs", "examples", filename).toString()));

      assertEquals(
          "{\"result\":{\"region\":[{\"country\":\"GB\",\"customerCount\":\"2\"},{\"country\":\"US\",\"customerCount\":\"4\"}]}}",
          JsonOutputWriter.map(hierarchy),
          filename);
    }
  }
}
