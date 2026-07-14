package blater.nestql.runner.sql.cache;

import java.nio.file.Path;

public record CacheHandle(Path cacheDir, String jdbcUrl, boolean needsLoad, CacheSource source) {
}
