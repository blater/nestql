package blater.nestql;

/** Prints top-level and topic-specific command-line help. */
public final class Help {
  static final String USAGE = """
      Usage: nestql <script-file-or-text> [input-file] [name=value ...] [options]
             nestql catalog [table-pattern] [connection/cache options]
             nestql --cache <input-file> [--cache-dir path]
             nestql --list-caches [--cache-dir path]
             nestql --clear-cache [input-file] [--cache-dir path]
             nestql --clear-cache-older-than age [--cache-dir path]
             nestql -h | --help | --help <topic>
      """;

  static final String HELP_ON_HELP = """
      HELP
          nestql -h
              Print brief usage help.

          nestql --help
              Print the complete nestql(1) manual page.

          nestql --help help
              Print this list of available help topics.

          nestql --help <topic>
              Print focused help for one topic.

      AVAILABLE HELP TOPICS
          query          Run a script against a database or input-file cache.
          catalog        List tables or show details for matching tables.
          cache          Query an XML, JSON, YAML, CSV, or Parquet input file.
          clear-cache    Remove all caches, one input's caches, or old caches.
          list-caches    List persistent input-file caches.
          connection     Configure JDBC connections from options or properties.
          output         Select XML, JSON, YAML, CSV, or Markdown output.
          parameters     Supply runtime template parameters.
          parquet        Override Parquet hierarchy and record names.

      EXAMPLES
          nestql --help query
          nestql --help connection
          nestql --help clear-cache
      """;

  static final String CATALOG_CMD = """
      CATALOG
          List database tables or show full details for matching tables.

      SYNOPSIS
          nestql catalog [table-pattern] [connection/cache options]

      DESCRIPTION
          With no pattern, catalog lists user table names only. Supplying a
          table name or a pattern containing * includes table metadata and
          column details for every match. Matching is case-insensitive.

          Catalog uses an explicitly selected cache, otherwise a configured
          JDBC connection, otherwise the active cache. Quote patterns containing
          * so the shell does not expand them before nestQL receives them.
          Command-line catalog output defaults to Markdown; --output overrides it.

      EXAMPLES
          nestql catalog
          nestql catalog customer
          nestql catalog 'audit*' --output json
          nestql catalog '*' --cache customers.json
          nestql catalog -p database.properties

      SEE ALSO
          nestql --help cache
          nestql --help connection
          nestql --help output
      """;

  static final String QUERY_CMD = """
      QUERY
          Run a nestQL script against an external database or a cached input file.

      SYNOPSIS
          nestql <script-file-or-text> [input-file] [name=value ...] [options]

      DESCRIPTION
          A script may be a filename or inline script text. Supply an input file
          for mapped DML, or combine it with --cache to query the file through a
          persistent local H2 database. With no input or JDBC connection, the
          script queries the active cache. DQL structure keys are inferred from
          database metadata unless --no-key-inference is supplied. Use --debug
          to log the inferred relationships selected for each query to stderr.

      EXAMPLES
          nestql report.nql -p database.properties
          nestql update.nql customers.json -p database.properties
          nestql query.nql customers.json --cache --output json
          nestql 'output json; select 1 into {result.value};' -p database.properties

      SEE ALSO
          nestql --help connection
          nestql --help cache
          nestql --help output
          nestql --help parameters
      """;

  static final String CACHE_CMD = """
      CACHE
          Query a structured input file through a persistent local H2 cache.

      SYNOPSIS
          nestql --cache <input-file> [--cache-dir path]
          nestql <script> <input-file> --cache [--cache-dir path] [--output type]
          nestql <script> [--output type]

      DESCRIPTION
          Supported input types are XML, JSON, YAML, CSV, and Parquet. Caches are
          stored under ~/.nestql/cache by default and reused until explicitly
          cleared. Loading or explicitly selecting a cache makes it active. A
          script with no input or JDBC connection queries the active cache.
          Explicit --cache takes precedence over supplied JDBC settings.

      EXAMPLES
          nestql --cache customers.json
          nestql totals.nql
          nestql totals.nql customers.json --cache --output json

      SEE ALSO
          nestql --help clear-cache
          nestql --help list-caches
          nestql --help parquet
      """;

  static final String CLEAR_CACHE_CMD = """
      CLEAR-CACHE
          Remove persistent input-file caches.

      SYNOPSIS
          nestql --clear-cache [--cache-dir path]
          nestql --clear-cache <input-file> [--cache-dir path]
          nestql --clear-cache=<input-file> [--cache-dir path]
          nestql --clear-cache-older-than <age> [--cache-dir path]

      DESCRIPTION
          With no input file, --clear-cache removes every cache. With an input
          file, it removes every cache variant belonging to that source. Ages
          accept minutes, hours, or days, including forms such as 30m, 6h, and 7d.

      EXAMPLES
          nestql --clear-cache
          nestql --clear-cache customers.json
          nestql --clear-cache-older-than 7d
      """;

  static final String LIST_CACHES_CMD = """
      LIST-CACHES
          List persistent input-file caches.

      SYNOPSIS
          nestql --list-caches [--cache-dir path]

      DESCRIPTION
          Displays each cache's input type, creation time, and source path. The
          active cache is marked with an asterisk.

      EXAMPLE
          nestql --list-caches --cache-dir /tmp/nestql-cache
      """;

  static final String CONNECTION_CMD = """
      CONNECTION
          Configure the JDBC connection used to execute a script.

      SYNOPSIS
          nestql <script> -p <properties-file>
          nestql <script> --db <type> --database <name> [connection-options]
          nestql <script> --jdbc-database <url> [exact-jdbc-options]

      SIMPLE OPTIONS
          --db type             h2, postgresql, mysql, mariadb, oracle,
                                sqlserver, db2, hana, or informix
          --database name       Database, Oracle service, or H2 URL suffix
          --host host           Database host; defaults to localhost
          --port port           Database port
          --user username       JDBC username
          --password password   JDBC password; --password= supplies empty

      EXACT OPTIONS
          --jdbc-driver name
          --jdbc-class-name class
          --jdbc-database url
          --jdbc-username username
          --jdbc-password password

      EXAMPLES
          nestql report.nql -p database.properties
          nestql report.nql --db postgresql --database customers --user report
      """;

  static final String OUTPUT_CMD = """
      OUTPUT
          Select the document format written by nestQL.

      SYNOPSIS
          nestql <script> [other arguments] --output <type>
          nestql <script> [other arguments] -o <type>

      DESCRIPTION
          Accepted types are xml, json, yaml, csv, and markdown,
          case-insensitively. The
          command-line option overrides the script's first 'output type;'
          directive. JSON is the default when neither is supplied. The
          command-line catalog command defaults to Markdown.

      EXAMPLES
          nestql report.nql -p database.properties --output json
          nestql query.nql customers.csv --cache -o yaml
      """;

  static final String PARAMETERS_CMD = """
      PARAMETERS
          Supply values for ${name} and ${name:default} script templates.

      SYNOPSIS
          nestql <script> [input-file] [name=value ...] [options]

      DESCRIPTION
          Runtime parameters may appear in any unambiguous argument position.
          Quote the complete name=value argument when its value contains spaces
          or shell-sensitive characters. Command-line values override properties.

      EXAMPLE
          nestql report.nql -p database.properties region=EMEA 'name=Alice Smith'
      """;

  static final String PARQUET_CMD = """
      PARQUET
          Override names inferred from a Parquet filename and message schema.

      SYNOPSIS
          nestql <script> <file.parquet> [--cache] [parquet-options]

      OPTIONS
          --parquet-root name     Override the hierarchy root inferred from the
                                  Parquet filename.
          --parquet-record name   Override the repeated record name inferred
                                  from the Parquet message type.

          Both options also accept --option=value.

      EXAMPLE
          nestql query.nql data.parquet --cache \
            --parquet-root customers --parquet-record customer
      """;

  static final String MAN_PAGE = """
      NESTQL(1)

      NAME
          nestql - query and transform relational and hierarchical data

      SYNOPSIS
          nestql <script-file-or-text> [input-file] [name=value ...] [options]
          nestql catalog [table-pattern] [connection/cache options]
          nestql --cache <input-file> [--cache-dir path]
          nestql --list-caches [--cache-dir path]
          nestql --clear-cache [input-file] [--cache-dir path]
          nestql --clear-cache-older-than age [--cache-dir path]
          nestql -h | --help | --help <topic>

      DESCRIPTION
          nestQL is a SQL-like language for moving data between relational
          databases and XML, JSON, YAML, CSV, or Parquet documents. It can run
          scripts against an external JDBC database, apply mapped DML from an
          input document, or query an input file through a persistent H2 cache.

          Arguments may appear in any unambiguous order. A script may be a file
          or inline text. The input file type is selected by its extension.

      HELP
          -h
              Print brief usage help.

          --help
              Print this manual page.

          --help help
              List available focused help topics.

          --help <topic>
              Print focused help for a command or option group.

      COMMANDS
          catalog [table-pattern]
              List user table names. With a name or * pattern, include full
              table and column details for every matching table.

      OPTIONS
          -p file
              Load JDBC settings and runtime parameters from a properties file.

          --db type, --database name, --host host, --port port
              Construct a JDBC URL for a supported logical database type.

          --user username, --password password
              Set credentials for the simple JDBC connection form.

          --jdbc-driver name, --jdbc-class-name class, --jdbc-database url,
          --jdbc-username username, --jdbc-password password
              Set exact JDBC connection properties.

          --output type, -o type
              Write xml, json, yaml, csv, or markdown output.

          --debug
              Log inference decisions and other diagnostic details to stderr.

          --no-key-inference
              Disable automatic DQL structure-key inference and preserve
              row-first output for paths without explicit structure keys.

          --metadata-refresh
              Rebuild cached database key and relationship metadata for the
              selected JDBC or input-cache target, then exit.

          --metadata-expiry-hours hours
              Persist the selected target's metadata expiry. Zero refreshes
              metadata on every use; the default is 24 hours.

          --cache
              Load, select, or query a persistent local H2 cache. An explicit
              cache takes precedence over JDBC settings.

          --cache-dir path
              Store caches somewhere other than ~/.nestql/cache.

          --list-caches
              List persistent caches and their source files.

          --clear-cache [input-file]
              Clear all caches or every cache variant for one input file.

          --clear-cache-older-than age
              Clear caches older than an age such as 30m, 6h, or 7d.

          --parquet-root name, --parquet-record name
              Override names inferred from a Parquet file and message schema.

          name=value
              Supply a runtime template parameter.

      FILES
          ~/.nestql/config.properties
              Stores the active cache selection.

          ~/.nestql/cache
              Default directory for persistent input-file caches.

      EXAMPLES
          nestql report.nql -p database.properties
          nestql update.nql customers.json -p database.properties region=EMEA
          nestql --cache customers.json
          nestql catalog
          nestql catalog 'customer*' --output json
          nestql query.nql
          nestql query.nql customers.json --cache --output json
          nestql --list-caches
          nestql --clear-cache-older-than 7d

      SEE ALSO
          nestql --help help
          https://github.com/blater/nestql
      """;

  public static void printBriefHelp() {
    System.out.print(USAGE);
    System.out.println("Run 'nestql --help' for the complete manual or 'nestql --help help' for topics.");
  }

  public static void printCommandInfo(String command) {
    String normalized = command == null ? "" : command.strip().toLowerCase();
    String info = switch (normalized) {
      case "help" -> HELP_ON_HELP;
      case "query", "run" -> QUERY_CMD;
      case "catalog" -> CATALOG_CMD;
      case "cache" -> CACHE_CMD;
      case "clear-cache", "clear" -> CLEAR_CACHE_CMD;
      case "list-caches", "list" -> LIST_CACHES_CMD;
      case "connection", "database", "db", "jdbc" -> CONNECTION_CMD;
      case "output" -> OUTPUT_CMD;
      case "parameters", "parameter", "params" -> PARAMETERS_CMD;
      case "parquet" -> PARQUET_CMD;
      default -> """
          Unknown help topic: %s

          Run 'nestql --help help' to list available topics.
          """.formatted(command);
    };
    System.out.print(info);
  }

  public static void printManPage() {
    System.out.print(MAN_PAGE);
  }
}
