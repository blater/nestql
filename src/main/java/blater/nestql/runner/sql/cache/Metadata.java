package blater.nestql.runner.sql.cache;

record Metadata(
    String sourcePath,
    String inputType,
    String identityText,
    long createdMillis) {
}
