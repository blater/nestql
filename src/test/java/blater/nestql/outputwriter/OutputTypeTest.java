package blater.nestql.outputwriter;

import blater.nestql.ParameterParser;
import blater.nestql.parser.script.NestScript;
import blater.nestql.parser.script.NestStatement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputTypeTest {
  @Test
  void commandLineCatalogDefaultsToMarkdownWhileOtherCommandsDefaultToJson() {
    NestScript catalogScript = new NestScript(List.of(NestStatement.catalog(null)));

    assertEquals(
        OutputType.MARKDOWN,
        OutputType.outputTypeFor(
            catalogScript,
            Map.of(ParameterParser.CATALOG_PATTERN_PARAM, "")));
    assertEquals(OutputType.JSON, OutputType.outputTypeFor(catalogScript, Map.of()));
  }

  @Test
  void explicitOutputSelectionsOverrideTheCatalogDefault() {
    NestScript yamlScript = new NestScript(OutputType.YAML, List.of());
    Map<String, String> catalog = Map.of(ParameterParser.CATALOG_PATTERN_PARAM, "");
    Map<String, String> commandLineJson = Map.of(
        ParameterParser.CATALOG_PATTERN_PARAM, "",
        ParameterParser.OUTPUT_TYPE_PARAM, "json");

    assertEquals(OutputType.YAML, OutputType.outputTypeFor(yamlScript, catalog));
    assertEquals(OutputType.JSON, OutputType.outputTypeFor(yamlScript, commandLineJson));
  }
}
