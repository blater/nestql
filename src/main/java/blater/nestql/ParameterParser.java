package blater.nestql;

import blater.nestql.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/*
 * Responsibility: Parses CLI arguments and properties files into the
 * runtime parameter map.
 */
public final class ParameterParser {
  public static final String INPUT_FILENAME = "NSQL_INPUTFILE";

  public static final String SCRIPT_FILE_PARAM = "NSQL_SCRIPTFILE";
  public static final String SCRIPT_TEXT_PARAM = "NSQL_SCRIPT";

  public static final String CACHE_CLEAR_ALL_PARAM = "NSQL_CACHE_CLEAR_ALL";
  public static final String CACHE_CLEAR_TARGET_PARAM = "NSQL_CACHE_CLEAR_TARGET";
  public static final String CACHE_CLEAR_OLDER_THAN_PARAM = "NSQL_CACHE_CLEAR_OLDER_THAN";
  public static final String CACHE_LIST_PARAM = "NSQL_CACHE_LIST";
  public static final String CACHE_DIR_PARAM = "NSQL_CACHE_DIR";

  public static final String JDBC_PROPS_FILE_PARAM = "NSQL_JDBC_PROPS_FILE";
  public static final String OUTPUT_TYPE_PARAM = "NSQL_OUTPUT_TYPE";
  public static final String CACHE_MODE_PARAM = "NSQL_CACHE";
  public static final String PARQUET_ROOT_PARAM = "NSQL_PARQUET_ROOT";
  public static final String PARQUET_RECORD_PARAM = "NSQL_PARQUET_RECORD";
  public static final String JDBC_DRIVER_PARAM = "jdbc.driver";
  public static final String JDBC_CLASS_NAME_PARAM = "jdbc.class.name";
  public static final String JDBC_DATABASE_PARAM = "jdbc.database";
  public static final String JDBC_USERNAME_PARAM = "jdbc.username";
  public static final String JDBC_PASSWORD_PARAM = "jdbc.password";

  // returns parameters
  public static Map<String, String> parse(String... args) {
    List<String> positionals = new ArrayList<>();
    Map<String, String> propertyParameters = new LinkedHashMap<>();
    Map<String, String> commandParameters = new LinkedHashMap<>();
    String databaseType = null;
    String databaseName = null;
    String host = null;
    String port = null;

    for (int i = 0; i < args.length; i++) {
      var arg = args[i];
      String connectionOption = connectionOptionName(arg);
      if (connectionOption != null) {
        String value = connectionOptionValue(args, i, connectionOption);
        if (arg.indexOf('=') < 0) {
          i++;
        }
        switch (connectionOption) {
          case "--db" -> databaseType = value;
          case "--database" -> databaseName = value;
          case "--host" -> host = value;
          case "--port" -> port = value;
          case "--user", "--jdbc-username" -> commandParameters.put(JDBC_USERNAME_PARAM, value);
          case "--password", "--jdbc-password" -> commandParameters.put(JDBC_PASSWORD_PARAM, value);
          case "--jdbc-driver" -> commandParameters.put(JDBC_DRIVER_PARAM, value);
          case "--jdbc-class-name" -> commandParameters.put(JDBC_CLASS_NAME_PARAM, value);
          case "--jdbc-database" -> commandParameters.put(JDBC_DATABASE_PARAM, value);
        }
        continue;
      }

      // parameters file
      if ("-p".equals(arg)) {
        if (i + 1 >= args.length) {
          Log.fatal(IllegalArgumentException.class, "no properties filename supplied");
        }
        String filename = args[++i];
        if (filename == null)
          Log.fatal(IllegalArgumentException.class, "no properties filename supplied");

        if (filename.endsWith(".properties")) {
          addParametersFromPropFile(propertyParameters, filename);
        } else if (filename.endsWith(".xml"))
          addParametersFromXmlFile(propertyParameters, filename);
        else if (filename.endsWith(".json"))
          addParametersFromJsonFile(propertyParameters, filename);
        else if (filename.endsWith(".yaml"))
          addParametersFromYamlFile(propertyParameters, filename);

        // cache the query
      } else if ("--cache".equals(arg)) {
        addSystemParameter(commandParameters, CACHE_MODE_PARAM, "true");

        // override default cache dir
      } else if ("--cache-dir".equals(arg)) {
        requireValueAfter(args, i, "no cache directory supplied");
        addSystemParameter(commandParameters, CACHE_DIR_PARAM, args[++i]);

        // override default cache dir
      } else if (arg.startsWith("--cache-dir=")) {
        addSystemParameter(commandParameters, CACHE_DIR_PARAM, arg.substring("--cache-dir=".length()));

        // show available cache databases
      } else if ("--list-caches".equals(arg)) {
        addSystemParameter(commandParameters, CACHE_LIST_PARAM, "true");

        // clear out cache
      } else if ("--clear-cache".equals(arg)) {
        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
          addSystemParameter(commandParameters, CACHE_CLEAR_TARGET_PARAM, args[++i]);
        } else {
          addSystemParameter(commandParameters, CACHE_CLEAR_ALL_PARAM, "true");
        }
        // clear out cache
      } else if (arg.startsWith("--clear-cache=")) {
        addSystemParameter(commandParameters, CACHE_CLEAR_TARGET_PARAM, arg.substring("--clear-cache=".length()));

        // clear out cache
      } else if ("--clear-cache-older-than".equals(arg)) {
        requireValueAfter(args, i, "no cache age supplied");
        addSystemParameter(commandParameters, CACHE_CLEAR_OLDER_THAN_PARAM, args[++i]);
        // clear out cache
      } else if (arg.startsWith("--clear-cache-older-than=")) {
        addSystemParameter(commandParameters, CACHE_CLEAR_OLDER_THAN_PARAM, arg.substring("--clear-cache-older-than=".length()));

        // parquet specific
      } else if ("--parquet-root".equals(arg)) {
        requireValueAfter(args, i, "no parquet root supplied");
        addSystemParameter(commandParameters, PARQUET_ROOT_PARAM, args[++i]);
        // parquet specific
      } else if (arg.startsWith("--parquet-root=")) {
        addSystemParameter(commandParameters, PARQUET_ROOT_PARAM, arg.substring("--parquet-root=".length()));
        // parquet specific
      } else if ("--parquet-record".equals(arg)) {
        requireValueAfter(args, i, "no parquet record supplied");
        addSystemParameter(commandParameters, PARQUET_RECORD_PARAM, args[++i]);
        // parquet specific
      } else if (arg.startsWith("--parquet-record=")) {
        addSystemParameter(commandParameters, PARQUET_RECORD_PARAM, arg.substring("--parquet-record=".length()));

        // output type e.g. json/xml/yaml
      } else if ("--output".equals(arg) || "-o".equals(arg)) {
        if (i + 1 >= args.length) {
          Log.fatal(IllegalArgumentException.class, "no output type supplied");
        }
        addSystemParameter(commandParameters, OUTPUT_TYPE_PARAM, args[++i]);

      } else if (arg.startsWith("--output=")) {
        addSystemParameter(commandParameters, OUTPUT_TYPE_PARAM, arg.substring("--output=".length()));

      } else if (arg.startsWith("-")) {
        Log.fatal(IllegalArgumentException.class, "Unknown option: " + arg);

      } else if (isParameterAssignment(arg) && !fileExists(arg)) {
        addParameterFromMainPropsFile(commandParameters, arg);

      } else {
        positionals.add(arg);
      }
    }
    Map<String, String> parameters = new LinkedHashMap<>(propertyParameters);
    resolveSimpleConnection(parameters, databaseType, databaseName, host, port);
    parameters.putAll(commandParameters);
    if (commandParameters.containsKey(JDBC_CLASS_NAME_PARAM)
        && !commandParameters.containsKey(JDBC_DRIVER_PARAM)) {
      parameters.remove(JDBC_DRIVER_PARAM);
    }
    validateCacheConnectionConflict(parameters);
    normalizeJdbcDriver(parameters);
    resolvePositionals(parameters, positionals);
    return parameters;
  }

  private static String connectionOptionName(String argument) {
    int equals = argument.indexOf('=');
    String option = equals < 0 ? argument : argument.substring(0, equals);
    return switch (option) {
      case "--db", "--database", "--host", "--port", "--user", "--password",
           "--jdbc-driver", "--jdbc-class-name", "--jdbc-database",
           "--jdbc-username", "--jdbc-password" -> option;
      default -> null;
    };
  }

  private static String connectionOptionValue(String[] args, int index, String option) {
    int equals = args[index].indexOf('=');
    if (equals >= 0) {
      return args[index].substring(equals + 1);
    }
    requireValueAfter(args, index, "no value supplied for " + option);
    return args[index + 1];
  }

  private static void validateCacheConnectionConflict(Map<String, String> parameters) {
    boolean cacheCommand = Boolean.parseBoolean(parameters.get(CACHE_MODE_PARAM))
        || parameters.containsKey(CACHE_CLEAR_ALL_PARAM)
        || parameters.containsKey(CACHE_CLEAR_TARGET_PARAM)
        || parameters.containsKey(CACHE_CLEAR_OLDER_THAN_PARAM)
        || parameters.containsKey(CACHE_LIST_PARAM);
    if (!cacheCommand) {
      return;
    }

    boolean connectionConfigured = parameters.containsKey(JDBC_DRIVER_PARAM)
        || parameters.containsKey(JDBC_CLASS_NAME_PARAM)
        || parameters.containsKey(JDBC_DATABASE_PARAM)
        || parameters.containsKey(JDBC_USERNAME_PARAM)
        || parameters.containsKey(JDBC_PASSWORD_PARAM);
    if (connectionConfigured) {
      Log.fatal(
          IllegalArgumentException.class,
          "JDBC connection options cannot be used with cache mode or cache maintenance commands.");
    }
  }

  private static void resolveSimpleConnection(
      Map<String, String> parameters,
      String databaseType,
      String databaseName,
      String host,
      String port) {
    if ((host != null || port != null) && databaseType == null) {
      Log.fatal(IllegalArgumentException.class, "--host and --port require --db.");
    }
    if ((databaseType == null) != (databaseName == null)) {
      Log.fatal(IllegalArgumentException.class, "--db and --database must be supplied together.");
    }
    if (databaseType == null) {
      return;
    }

    String driver = normalizeDatabaseType(databaseType);
    if (driver.equals("h2") && (host != null || port != null)) {
      Log.fatal(IllegalArgumentException.class, "--host and --port are not valid for H2.");
    }
    if (requiresExplicitPort(driver) && port == null) {
      Log.fatal(IllegalArgumentException.class, "--port is required for " + driver + ".");
    }

    String username = defaultUsername(driver);
    if (username != null) {
      parameters.putIfAbsent(JDBC_USERNAME_PARAM, username);
    }
    parameters.put(JDBC_DRIVER_PARAM, driver);
    parameters.put(JDBC_DATABASE_PARAM, jdbcUrl(driver, databaseName, host, port));
  }

  private static boolean requiresExplicitPort(String driver) {
    return driver.equals("hana") || driver.equals("informix");
  }

  private static void normalizeJdbcDriver(Map<String, String> parameters) {
    String driver = parameters.get(JDBC_DRIVER_PARAM);
    if (driver != null && !driver.isBlank()) {
      parameters.put(JDBC_DRIVER_PARAM, normalizeDatabaseType(driver));
    }
  }

  private static String normalizeDatabaseType(String value) {
    String normalized = value.trim().toLowerCase().replace('_', '-');
    return switch (normalized) {
      case "h2", "h2db" -> "h2";
      case "oracle", "ojdbc", "ojdbc11" -> "oracle";
      case "sqlserver", "mssql", "mssql-jdbc", "sql-server" -> "sqlserver";
      case "db2", "jcc" -> "db2";
      case "hana", "sap", "sap-hana", "ngdbc" -> "hana";
      case "informix" -> "informix";
      case "mysql", "mysql-connector-j" -> "mysql";
      case "mariadb" -> "mariadb";
      case "postgresql", "postgres", "postgres-jdbc" -> "postgresql";
      default -> Log.fatal(
          IllegalArgumentException.class,
          "Unsupported JDBC driver [" + value + "]. Expected one of: "
              + "h2, oracle, sqlserver, db2, hana, informix, mysql, mariadb, postgresql");
    };
  }

  private static String defaultUsername(String driver) {
    return switch (driver) {
      case "h2" -> "sa";
      case "postgresql", "mysql", "mariadb" -> System.getProperty("user.name");
      default -> null;
    };
  }

  private static String defaultPort(String driver) {
    return switch (driver) {
      case "postgresql" -> "5432";
      case "mysql", "mariadb" -> "3306";
      case "oracle" -> "1521";
      case "sqlserver" -> "1433";
      case "db2" -> "50000";
      default -> null;
    };
  }

  private static String jdbcUrl(String driver, String database, String host, String port) {
    String resolvedHost = host == null ? "localhost" : host;
    String resolvedPort = port == null ? defaultPort(driver) : port;
    return switch (driver) {
      case "h2" -> "jdbc:h2:" + database;
      case "postgresql" -> "jdbc:postgresql://" + resolvedHost + ":" + resolvedPort + "/" + database;
      case "mysql" -> "jdbc:mysql://" + resolvedHost + ":" + resolvedPort + "/" + database;
      case "mariadb" -> "jdbc:mariadb://" + resolvedHost + ":" + resolvedPort + "/" + database;
      case "oracle" -> "jdbc:oracle:thin:@//" + resolvedHost + ":" + resolvedPort + "/" + database;
      case "sqlserver" -> "jdbc:sqlserver://" + resolvedHost + ":" + resolvedPort + ";databaseName=" + database;
      case "db2" -> "jdbc:db2://" + resolvedHost + ":" + resolvedPort + "/" + database;
      case "hana" -> "jdbc:sap://" + resolvedHost + ":" + resolvedPort + "/?databaseName=" + database;
      case "informix" -> "jdbc:informix-sqli://" + resolvedHost + ":" + resolvedPort + "/" + database;
      default -> throw new IllegalArgumentException("Unsupported JDBC driver [" + driver + "].");
    };
  }


  static void addParameterFromMainPropsFile(Map<String, String> parameters, String propertiesString) {
    if (propertiesString == null) return;
    String s = propertiesString.trim();
    if (s.isEmpty()) return;                      // skip blank
    char first = s.charAt(0);
    if (first == '#' || first == '!') return;     // skip comment
    int eq = s.indexOf('=');
    int col = s.indexOf(':');
    int sep;
    if (eq >= 0 && col >= 0) sep = Math.min(eq, col);
    else if (eq >= 0) sep = eq;
    else if (col >= 0) sep = col;
    else sep = -1;
    if (sep < 0) {                                 // no separator -> empty value
      parameters.put(s, "");
      return;
    }
    String key = s.substring(0, sep).trim();
    if (isNotSystemParam(key)) {
      String val = s.substring(sep + 1).trim();
      parameters.put(key, val);
    }
  }

  static boolean isNotSystemParam(String key) {
    return !(key.equals(SCRIPT_FILE_PARAM)
        || key.equals(SCRIPT_TEXT_PARAM)
        || key.equals(INPUT_FILENAME)
        || key.equals(JDBC_PROPS_FILE_PARAM)
        || key.equals(OUTPUT_TYPE_PARAM)
        || key.equals(CACHE_MODE_PARAM)
        || key.equals(CACHE_DIR_PARAM)
        || key.equals(CACHE_CLEAR_ALL_PARAM)
        || key.equals(CACHE_CLEAR_TARGET_PARAM)
        || key.equals(CACHE_CLEAR_OLDER_THAN_PARAM)
        || key.equals(CACHE_LIST_PARAM)
        || key.equals(PARQUET_ROOT_PARAM)
        || key.equals(PARQUET_RECORD_PARAM));
  }

  static void addSystemParameter(Map<String, String> parameters, String key, String value) {
    parameters.put(key, value);
  }

  static void addParametersFromPropFile(Map<String, String> parameters, String filename) {
    Path propFile = Path.of(filename);
    try (Stream<String> lines = Files.lines(propFile)) {
      lines.forEach(line -> addParameterFromMainPropsFile(parameters, line));
    } catch (IOException e) {
      Log.fatal(IllegalStateException.class, "Could not read properties file: " + filename, e);
    }
  }

  static void addParametersFromXmlFile(Map<String, String> parameters, String filename) {
    // todo - adds the distinct unique set of params from an xml file into parameters array.
    // if an element of the same name appears more than once, the first one wins - its value is stored, others ignored
    // this will call new method addParameterCannotOverride(parameters, key, value));  << note must never be able to overwrite existing parameter entries or system key entry.
  }

  static void addParametersFromJsonFile(Map<String, String> parameters, String filename) {
    // todo - adds the distinct unique set of params from an json file into parameters array.
    // if an element of the same name appears more than once, the first one wins - its value is stored, others ignored
    // this will call new method addParameterCannotOverride(parameters, key, value));  << note must never be able to overwrite existing parameter entries or system key entry.
  }

  static void addParametersFromYamlFile(Map<String, String> parameters, String filename) {
    // todo - adds the distinct unique set of params from an yaml file into parameters array.
    // if an element of the same name appears more than once, the first one wins - its value is stored, others ignored
    // this will call new method addParameterCannotOverride(parameters, key, value));  << note must never be able to overwrite existing parameter entries or system key entry.
  }

  private static void requireValueAfter(String[] args, int index, String missingMessage) {
    if (index + 1 >= args.length || args[index + 1].startsWith("-")) {
      Log.fatal(IllegalArgumentException.class, missingMessage);
    }
  }

  private static void resolvePositionals(Map<String, String> params, List<String> positionArguments) {
    if ( params.containsKey(CACHE_LIST_PARAM)
      || params.containsKey(CACHE_CLEAR_TARGET_PARAM)
      || params.containsKey(CACHE_CLEAR_ALL_PARAM)
      || params.containsKey(CACHE_CLEAR_OLDER_THAN_PARAM))
    {
      if (!positionArguments.isEmpty()) {
        Log.fatal(IllegalArgumentException.class, "Unexpected argument: " + positionArguments.getFirst());
      }
      return;
    }
    if (positionArguments.isEmpty()) {
      Log.fatal(IllegalArgumentException.class, "No script filename supplied.");
    }
    if (positionArguments.size() > 2) {
      Log.fatal(IllegalArgumentException.class, "Unexpected argument: " + positionArguments.get(2));
    }

    if (positionArguments.size() == 1) {
      resolveSinglePositional(params, positionArguments.getFirst());
    } else {
      resolveTwoPositionals(params, positionArguments.get(0), positionArguments.get(1));
    }
  }


  private static void resolveSinglePositional(Map<String, String> parameters, String arg) {
    if (!fileExists(arg)) {
      addSystemParameter(parameters, SCRIPT_TEXT_PARAM, arg);
    } else if (isInputFile(arg)) {
      Log.fatal(IllegalArgumentException.class, "No script filename supplied.");
    } else {
      addSystemParameter(parameters, SCRIPT_FILE_PARAM, arg);
    }
  }

  private static void resolveTwoPositionals(Map<String, String> parameters, String first, String second) {
    boolean firstExists = fileExists(first);
    boolean secondExists = fileExists(second);

    if (!firstExists && !secondExists) {
      Log.fatal(IllegalArgumentException.class, "Neither script nor input file exists: " + first + ", " + second);
    } else if (firstExists && secondExists) {
      resolveTwoFiles(parameters, first, second);
    } else {
      resolveExistingFileAndMissingArgument(parameters, firstExists ? first : second, firstExists ? second : first);
    }
  }

  private static void resolveTwoFiles(Map<String, String> parameters, String first, String second) {
    boolean firstInput = isInputFile(first);
    boolean secondInput = isInputFile(second);

    if (firstInput == secondInput) {
      Log.fatal(IllegalArgumentException.class, "Could not identify script and input file from: " + first + ", " + second);
    }
    addSystemParameter(parameters, SCRIPT_FILE_PARAM, firstInput ? second : first);
    addSystemParameter(parameters, INPUT_FILENAME, firstInput ? first : second);
  }

  private static void resolveExistingFileAndMissingArgument(Map<String, String> parameters, String existingFile, String missingArgument) {
    if (isInputFile(existingFile)) {
      addSystemParameter(parameters, INPUT_FILENAME, existingFile);
      addSystemParameter(parameters, SCRIPT_TEXT_PARAM, missingArgument);
    } else if (isInputFile(missingArgument)) {
      addSystemParameter(parameters, SCRIPT_FILE_PARAM, existingFile);
      addSystemParameter(parameters, INPUT_FILENAME, missingArgument);
    } else {
      Log.fatal(IllegalArgumentException.class, "Input file does not exist: " + missingArgument);
    }
  }

  private static boolean fileExists(String value) {
    return value != null && Files.exists(Path.of(value));
  }

  private static boolean isInputFile(String value) {
    String lower = value == null ? "" : value.toLowerCase();
    return lower.endsWith(".xml")
        || lower.endsWith(".json")
        || lower.endsWith(".yaml")
        || lower.endsWith(".yml")
        || lower.endsWith(".csv")
        || lower.endsWith(".parquet");
  }

  private static boolean isParameterAssignment(String value) {
    if (value == null) {
      return false;
    }
    int eq = value.indexOf('=');
    if (eq <= 0) {
      return false;
    }
    String key = value.substring(0, eq);
    for (int index = 0; index < key.length(); index++) {
      char ch = key.charAt(index);
      if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '.' && ch != '-') {
        return false;
      }
    }
    return true;
  }

}
