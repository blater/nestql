package blater.nestql.outputwriter;

import blater.nestql.domain.Hierarchy;
import blater.nestql.util.Log;

/*
 * Responsibility: Renders a mapped hierarchy through a concrete output format.
 */
public interface OutputWriter {

  void write(Hierarchy result);
}
