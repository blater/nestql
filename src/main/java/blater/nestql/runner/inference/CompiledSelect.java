package blater.nestql.runner.inference;

import blater.nestql.domain.MappingPlan;

/** SQL and hierarchy plan prepared before the query is executed. */
public record CompiledSelect(String sql, MappingPlan plan) {
}
