package blater.nestql.outputwriter;

import blater.nestql.ParameterParser;
import blater.nestql.parser.script.NestScript;
import blater.nestql.util.Log;

import java.util.Locale;
import java.util.Map;

/*
 * Responsibility: Names the supported output formats and their outputWriter implementation.
 */
public enum OutputType {
  XML(new XmlOutputWriter()),
  JSON(new JsonOutputWriter()),
  YAML(new YamlOutputWriter()),
  CSV(new CsvOutputWriter())
  ;

  final OutputWriter outputWriter;

  OutputType(OutputWriter outputWriter) {
    this.outputWriter = outputWriter;
  }

  public static OutputType outputTypeFor(NestScript script, Map<String, String> params) {
    String cliOutputType = params.get(ParameterParser.OUTPUT_TYPE_PARAM);
    if (cliOutputType != null) {
      return OutputType.fromName(cliOutputType);
    }
    if (script != null && script.outputType() != null) {
      return script.outputType();
    }
    return OutputType.XML;
  }

  public static OutputType fromName(String value) {
    if (value == null || value.isBlank()) {
      return Log.fatal(IllegalArgumentException.class, "output type must be one of xml, json, csv, yaml");
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "xml" -> XML;
      case "json" -> JSON;
      case "csv" -> CSV;
      case "yaml" -> YAML;
      default -> Log.fatal(IllegalArgumentException.class, "unknown output type: " + value);
    };
  }
}
