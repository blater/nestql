package blater.nestql.outputwriter;

import blater.nestql.domain.Hierarchy;
import blater.nestql.util.Log;

/*
 * Responsibility: Renders a mapped hierarchy through a concrete output format.
 */
public interface OutputWriter {
  static OutputWriter of(OutputType type) {
    if (type == null) {
      return Log.fatal(IllegalArgumentException.class, "output type is required");
    }
    return type.writer();
  }

  void write(Hierarchy result);
}
