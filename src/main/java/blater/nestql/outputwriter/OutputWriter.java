package blater.nestql.outputwriter;

import blater.nestql.domain.Hierarchy;
import blater.nestql.util.Log;

/*
 * Responsibility: Renders a mapped hierarchy through a concrete output format.
 */
public interface OutputWriter {
  static OutputWriter of(OutputType type) {
    if (type == OutputType.XML)
      return new XmlOutputWriter();
    else if (type == OutputType.JSON)
      return new JsonOutputWriter();
    else if (type == OutputType.YAML)
      return new YamlOutputWriter();
    else if (type == OutputType.CSV)
      return new CsvOutputWriter();
    else
      return Log.fatal(IllegalArgumentException.class, "unknown outputWriter type");
  }

  void write(Hierarchy result);
}
