package blater.nestql.inference;

import blater.nestql.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HexFormat;

/** Stable cache identity which deliberately excludes JDBC credentials. */
public record DatabaseTargetIdentity(String identityText, String displayName, String directoryName) {
  public static DatabaseTargetIdentity from(Connection connection) throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    String catalog = connection.getCatalog();
    String schema;
    try {
      schema = connection.getSchema();
    } catch (SQLException | AbstractMethodError ignored) {
      schema = null;
    }
    String url = sanitizeUrl(metadata.getURL());
    String identity = "url=" + url + "\n"
        + "product=" + safe(metadata.getDatabaseProductName()) + "\n"
        + "user=" + safe(metadata.getUserName()) + "\n"
        + "catalog=" + safe(catalog) + "\n"
        + "schema=" + safe(schema) + "\n";
    String display = safe(metadata.getDatabaseProductName()) + " " + url;
    return new DatabaseTargetIdentity(identity, display.strip(), "database-" + sha256(identity).substring(0, 24));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      return Log.fatal(IllegalStateException.class, "SHA-256 is not available", ex);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  static String sanitizeUrl(String value) {
    String sanitized = safe(value)
        .replaceAll("(?i)(password|passwd|pwd)=([^;&?]*)", "$1=<redacted>");
    return sanitized.replaceAll(
        "(?i)(jdbc:oracle:[^:]+:)[^/@:]+/[^@]+@",
        "$1<credentials>@");
  }
}
