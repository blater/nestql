package blater.nestql.outputwriter;

import blater.nestql.ParameterParser;
import blater.nestql.parser.script.NestScript;

import java.util.Map;

/*
 * Responsibility: Names the supported output formats and their outputWriter implementation.
 */
public enum OutputType {
  XML(new XmlOutputWriter()),
  JSON(new JsonOutputWriter()),
  YAML(new YamlOutputWriter()),
  CSV(new CsvOutputWriter()),
  MARKDOWN(new MarkdownOutputWriter())
  ;

  final static OutputType DEFAULT_OUTPUT_TYPE = JSON;
  final OutputWriter outputWriter;

  OutputType(OutputWriter outputWriter) {
    this.outputWriter = outputWriter;
  }

  OutputWriter writer() {
    return outputWriter;
  }

  public static OutputType outputTypeFor(NestScript script, Map<String, String> params) {
    String cliOutputType = params.get(ParameterParser.OUTPUT_TYPE_PARAM);
    if (cliOutputType != null) {
      return OutputType.fromName(cliOutputType);
    } else if (script != null && script.outputType() != null) {
      return script.outputType();
    } else if (params.containsKey(ParameterParser.CATALOG_PATTERN_PARAM)) {
      return MARKDOWN;
    } else
      return DEFAULT_OUTPUT_TYPE;
  }

  public static OutputType fromName(String name) {
    if (name == null || name.isBlank()) {
      return DEFAULT_OUTPUT_TYPE;
    }
    return switch (name.trim().toLowerCase()) {
      case "xml" -> XML;
      case "json" -> JSON;
      case "csv" -> CSV;
      case "yaml" -> YAML;
      case "markdown" -> MARKDOWN;
      default -> DEFAULT_OUTPUT_TYPE;
    };
  }
}
