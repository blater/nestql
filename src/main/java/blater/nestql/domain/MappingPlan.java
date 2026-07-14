package blater.nestql.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import static java.util.Collections.emptyList;

/*
 * Responsibility: Holds the fields and node-opening rules used to map
 * flat query rows into one result hierarchy.
 */
@NoArgsConstructor
public class MappingPlan{
  @Getter
  private List<OutputField> fields = emptyList();
  @Getter
  private List<CorrelationRule> correlationRules = emptyList();
  @Getter
  private List<KeyedPath> keyedPaths = emptyList();

  public MappingPlan(List<OutputField> fields, List<CorrelationRule> correlationRules) {
    this(fields, correlationRules, emptyList());
  }

  public MappingPlan(List<OutputField> fields, List<CorrelationRule> correlationRules, List<KeyedPath> keyedPaths) {
    this.fields = fields;
    this.correlationRules = correlationRules;
    this.keyedPaths = keyedPaths;
  }

  public String rootName() {
    if (!fields.isEmpty()) {
      return fields.getFirst().getPath().getRootName();
    }
    if (!correlationRules.isEmpty()) {
      return correlationRules.getFirst().getPath().getRootName();
    }
    return null;
  }
}
