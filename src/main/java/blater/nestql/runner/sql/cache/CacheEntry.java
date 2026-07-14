package blater.nestql.runner.sql.cache;

public record CacheEntry(
    String sourcePath,
    String inputType,
    long createdMillis) {
}
