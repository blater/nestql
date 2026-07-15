package blater.nestql.inputreader;

import blater.nestql.util.Log;

import java.util.Locale;

public enum InputType {
  XML,
  JSON,
  YAML,
  CSV,
  PARQUET;

  public static InputType fromFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return XML;
    }
    String normalized = filename.toLowerCase(Locale.ROOT);
    if (normalized.endsWith(".xml")) {
      return XML;
    }
    if (normalized.endsWith(".json")) {
      return JSON;
    }
    if (normalized.endsWith(".yaml") || normalized.endsWith(".yml")) {
      return YAML;
    }
    if (normalized.endsWith(".csv")) {
      return CSV;
    }
    if (normalized.endsWith(".parquet")) {
      return PARQUET;
    }
    return Log.fatal(IllegalArgumentException.class, "Unsupported input file type: " + filename);
  }
}
