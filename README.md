##  NestQL

**NestQL** is a **SQL**-like language for dealing with **JSON** / **YAML** / **XML** / **CSV** / **Parquet** files.
It allows you to query them, update databases from these files, and pull data out of databases in these formats.

## Full Documentation

See the [nestQL user manual](docs/user-manual.md) for the complete language and command-line reference.

### Getting started

You use SQL and specify paths such as "{customers.person.address}" to reference fields in your json / yaml / xml file.

For example if you have a json file of customers you want to update a DB from:
``` sql 
update address
set street = {customers.person.addressline1},
    city = {customers.person.addressline4}
where personid = {customers.person.id};
```

nestQL normally infers keys and relationships from database metadata. The SQL extension
`structure {path.to.object} key (...)` explicitly tells nestQL which rows contribute to the same output object:
```sql
output json;                                    -- create json output
select personid into {people.person.id},     -- the persons id goes into people.person.id in the JSON
       name into {people.person.firstname}   -- the name goes into people.person.name
from person
structure {people.person} key (personid);
```
Output:
```json
{
  "people": {
    "person": [
      { "id": 1, "name": "Alice" },
      { "id": 2, "name": "Bob" },
      { "id": 3, "name": "Eva" }
    ] }
}
```

### More complex examples
Complexity in most tools ramps up quickly when you need heirachical output or need to join tables.
`structure` key declarations specify the relationships. This allows NestQL to take care of wrangling the
output into the structure you want.

Here's an example of retrieving a hierarchical relationship, in a database where people have one name but can have 
zero or more nicknames & we want to extract this as structured XML:

```sql
output xml;
select
  p.personid  into {people.person.@id},                      -- we can put data into attributes in XML
  n.nicknameid,
  p.firstname into {people.person.name},
  n.nickname  into {people.person.nicknames.nickname}
from person p, nickname n
where n.personid = p.personid
structure
  {people.person} key (p.personid),                          -- one person object per person ID
  {people.person.nicknames.nickname} key (n.nicknameid);     -- a person can have more than one nickname in the DB
```

Output:
```xml
<people>
  <person id=1>
    <name>Alice</name>
    <nicknames>
      <nickname>Ali</nickname>
    </nicknames>
  </person>
  ... etc
  <person id=5>
    <name>Eva</name>
    <nicknames>
      <nickname>Evie</surname>
      <nickname>Hawkeye</surname>
    </nicknames>
  </person>
</people>
```


## Loading Files into a Database

nestQL can also use XML, JSON, YAML, CSV, or Parquet files as input for database changes. For example, this JSON:

```json
{
  "message": {
    "person": {
      "id": 7,
      "first_name": "Fred"
    }
  }
}
```

can be inserted with:

```sql
insert into person (person_id, first_name)
values ({message.person.id}, {message.person.first_name});
```

or used to update an existing row:

```sql
update person
set first_name = {message.person.first_name}
where person_id = {message.person.id};
```

To apply the file to the database rather than query it with `--cache`, supply the input file after the script and use
`-p` for the database connection. Equivalent YAML and XML structures use the same mapping paths:

```bash
nestql insert-person.nql person.json -p database.properties
nestql update-person.nql person.yaml -p database.properties
```


## Usage

nestQL can be launched in three ways. Invoke the main class directly when the compiled classes and dependencies are
already on `CLASSPATH`:

```bash
java blater.nestql.Main report.nql -p database.properties
```

`mvn package` creates an executable fat JAR containing its runtime dependencies:

```bash
java -jar target/nestql-*.jar report.nql -p database.properties
```

Native builds produce one of three executables, according to the selected JDBC driver profile:

```text
nestql             Common drivers: H2, MySQL, MariaDB, and PostgreSQL
nestql-enterprise  Enterprise drivers: Oracle, SQL Server, DB2, SAP HANA, and Informix
nestql-all         All common and enterprise drivers
```

Their command-line syntax is identical. For simplicity, the remaining examples use the default `nestql` executable.

Run a script against an external database:

```bash
nestql report.nql -p database.properties
```

Load an input file for mapped DML:

```bash
nestql update-customers.nql customers.json -p database.properties region=EMEA
```

Query an input file through nestQL's local SQL cache, without an external database:

```bash
nestql query.nql customers.json --cache --output json
```

Load a cache once and make it active, then run repeated queries without repeating the input path or `--cache`:

```bash
nestql --cache customers.json
nestql first-query.nql
nestql second-query.nql
```

Select another cache explicitly with `nestql query.nql --cache other.json`. The selected cache becomes active.

The general execution form is:

```text
nestql <script-file-or-text> [input-file] [name=value ...] [options]
```

Options and positional arguments can appear in any unambiguous order. When two files are supplied, nestQL identifies
the input file by its extension, so `query.nql customers.json` and `customers.json query.nql` are equivalent. A
nonexistent positional argument is treated as inline script text when the other positional argument, if any, is a
recognised input file.

### Positional Arguments

| Argument | Description |
|---|---|
| `script-file-or-text` | A `.nql` script filename, or the script itself as one quoted command-line argument. Required except for standalone cache loading and cache-maintenance commands. |
| `input-file` | Optional XML, JSON, YAML, CSV, or Parquet input. With standalone `--cache`, it is loaded and made active; otherwise it is available to cached queries or mapped DML statements. |
| `name=value` | A runtime parameter used by `${name}` placeholders. Quote the complete argument when its value contains shell-sensitive characters or spaces. |

Input type is selected case-insensitively from `.xml`, `.json`, `.yaml`, `.yml`, `.csv`, or `.parquet`.

### Options

| Option | Description |
|---|---|
| `-p FILE` | Load JDBC settings and runtime parameters from a `.properties` file. |
| `--db TYPE` | Build a JDBC connection for `h2`, `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver`, `db2`, `hana`, or `informix`. Existing driver aliases are also accepted. |
| `--database NAME` | Database name, Oracle service name, or H2 URL suffix. Used with `--db`. |
| `--host HOST` | Database host. Defaults to `localhost`; not valid for H2. |
| `--port PORT` | Database port. Conventional ports are inferred where available; HANA and Informix require this option. |
| `--user USER` | Database username. |
| `--password PASSWORD` | Database password. Optional; `--password=` supplies an explicit empty password. |
| `--jdbc-driver NAME` | Set the exact `jdbc.driver` logical driver name. |
| `--jdbc-class-name CLASS` | Set the exact `jdbc.class.name` driver class. |
| `--jdbc-database URL` | Set the complete `jdbc.database` JDBC URL. |
| `--jdbc-username USER` | Set the exact `jdbc.username` value. |
| `--jdbc-password PASSWORD` | Set the exact `jdbc.password` value. |
| `--output TYPE`, `-o TYPE` | Select `xml`, `json`, `csv`, `yaml`, or `markdown` output. |
| `--output=TYPE` | Equals-form of `--output TYPE`. |
| `--debug` | Log each query's inferred output-path, relation, key, and parent relationship decisions to stderr. |
| `--no-key-inference` | Disable automatic DQL keys and preserve row-first output for paths without explicit `structure` keys. |
| `--metadata-refresh` | Rebuild cached key and relationship metadata for the selected target, then exit. |
| `--metadata-expiry-hours HOURS` | Persist metadata expiry for the selected target; zero refreshes every use. |
| `--cache` | Load or select an input file's persistent local H2 cache. With no input, query the active cache. Explicit cache mode overrides JDBC settings. |
| `--cache-dir PATH` | Store query caches under `PATH` instead of `~/.nestql/cache`. |
| `--cache-dir=PATH` | Equals-form of `--cache-dir PATH`. |
| `--list-caches` | List caches and their source files. This is a standalone maintenance command and does not take a script. |
| `--clear-cache` | Clear every cache in the selected cache directory. |
| `--clear-cache INPUT` | Clear every cache variant belonging to one input file. |
| `--clear-cache=INPUT` | Equals-form for clearing one input file's caches. |
| `--clear-cache-older-than AGE` | Clear caches older than an age such as `30m`, `6h`, or `7d`. Long unit forms such as `minutes`, `hours`, and `days` are also accepted. |
| `--clear-cache-older-than=AGE` | Equals-form of the cache-age option. |
| `--parquet-root NAME` | Override the hierarchy root inferred from a Parquet filename. |
| `--parquet-root=NAME` | Equals-form of `--parquet-root NAME`. |
| `--parquet-record NAME` | Override the repeated record name inferred from the Parquet message type. |
| `--parquet-record=NAME` | Equals-form of `--parquet-record NAME`. |

Every long JDBC option supports both `--option VALUE` and `--option=VALUE`.
An equals form with no value supplies an explicit empty string. A separated
option requires a following value, so use `--password=-secret` when a value
begins with `-`.

The command-line output option takes precedence over an `output xml|json|csv|yaml|markdown;` directive in the script. Without
either, output defaults to JSON. The command-line `catalog` command defaults to Markdown.

Cache maintenance examples:

```bash
nestql --cache customers.json
nestql --list-caches
nestql --clear-cache
nestql --clear-cache customers.json
nestql --clear-cache-older-than 7d
nestql --cache-dir /tmp/nestql-cache --list-caches
```

Catalog the active cache or a configured database without creating a script:

```bash
nestql catalog
nestql catalog customer
nestql catalog 'audit*' --output json
nestql catalog '*' --cache customers.json
```

With no pattern, `catalog` lists table names only. A table name or `*` pattern returns full table and column details.

### Command-Line Database Connections

The simple form infers the driver class, JDBC URL, host, and conventional port:

```bash
nestql report.nql --db postgresql --database customer_data --user report_user
```

Override the network defaults when required:

```bash
nestql report.nql \
  --db postgresql \
  --database customer_data \
  --host db.internal.example \
  --port 5544 \
  --user report_user
```

For an existing or vendor-specific JDBC URL, use the exact form:

```bash
nestql report.nql \
  --jdbc-driver postgresql \
  --jdbc-database 'jdbc:postgresql://db.internal.example:5432/customer_data' \
  --jdbc-username report_user
```

The simple form uses `sa` as the H2 username and the current OS username for
PostgreSQL, MySQL, and MariaDB. Other drivers leave the username absent. Host
defaults to `localhost`; default ports are PostgreSQL `5432`, MySQL/MariaDB
`3306`, Oracle `1521`, SQL Server `1433`, and Db2 `50000`. HANA and Informix
require `--port` because they have no single safe default.

Supplied connection values are used as written. nestQL does not validate port
ranges, encode URL components, or reconcile inconsistent credentials. Use the
exact form for advanced vendor syntax. Explicit `--cache` takes precedence over
JDBC connection options; cache maintenance commands ignore them.

### Properties File

External database connections normally use a Java-style `.properties` file:

```properties
jdbc.driver=postgresql
jdbc.database=jdbc:postgresql://localhost:5432/customer_data
jdbc.username=nestql
jdbc.password=change-me

region=EMEA
fromDate=2026-01-01
```

The JDBC properties are:

| Property | Description |
|---|---|
| `jdbc.driver` | Preferred logical driver name: `h2`, `mysql`, `mariadb`, `postgresql`, `oracle`, `sqlserver`, `db2`, `hana`, or `informix`. The selected build must include that driver's dependency or connection-time class loading fails. |
| `jdbc.class.name` | Legacy alternative containing a JDBC driver class name, for example `org.h2.Driver`. `jdbc.driver` wins when both are present. |
| `jdbc.database` | JDBC connection URL passed to `DriverManager`, such as `jdbc:postgresql://localhost:5432/customer_data`. |
| `jdbc.username` | Database username. |
| `jdbc.password` | Database password; an empty value is allowed when the database permits it. |

Other properties become ordinary runtime parameters. For example, `${region}` resolves to `EMEA` from the file above,
and `${missing:default}` uses `default` when no parameter or Java system property supplies `missing`.

Properties may use `key=value` or `key: value`. Blank lines and lines beginning with `#` or `!` are ignored. When
multiple properties files define the same value, the later file wins. Values supplied directly on the command line
always override properties-file values, regardless of where `-p` appears:

```bash
nestql report.nql -p database.properties region=APAC
nestql report.nql region=APAC -p database.properties
```

Both commands use `region=APAC`. This also permits partial connection settings,
for example credentials in `database.properties` with `--db` and `--database`
on the command line. Exact command-line `--jdbc-*` values override values
inferred from the simple form, regardless of their argument position. Among
command-line values for the same runtime property, the later value wins. An
explicit command-line `jdbc.class.name` replaces a `jdbc.driver` inherited from
properties; when both are supplied directly on the command line,
`jdbc.driver` takes precedence.

Only `.properties` parameter files currently load values; XML, JSON, and YAML parameter-file loading is not yet
implemented. A properties file is not required in `--cache` mode because nestQL supplies the local H2 connection.
Keep property files containing credentials out of source control and restrict their filesystem permissions. Prefer a
properties file for reusable passwords because command-line values can be visible in shell history and process listings.


### Building with Maven

NestQL is built with Maven. The default build uses the active `jdbc-common`
profile, which includes the common open-source JDBC drivers.

```bash
mvn test
mvn package
```

Use `-DskipTests` when you only need a package after tests have already passed:

```bash
mvn -DskipTests package
```

JDBC driver profile variations:

```bash
mvn -Pjdbc-common package
mvn -Pjdbc-enterprise package
mvn -Pjdbc-all package
```

- `jdbc-common`: H2, MySQL, MariaDB, and PostgreSQL.
- `jdbc-enterprise`: Oracle, SQL Server, DB2, SAP HANA, and Informix.
- `jdbc-all`: common and enterprise driver sets together.

Native images use the `native` profile. A GraalVM JDK with `native-image`
support must be on `PATH`.

```bash
mvn -Pnative -DskipTests package
mvn -Pjdbc-enterprise,native -DskipTests package
mvn -Pjdbc-all,native -DskipTests package
```

The native executable name follows the selected JDBC driver profile:

- default / `jdbc-common`: `nestql`
- `jdbc-enterprise`: `nestql-enterprise`
- `jdbc-all`: `nestql-all`

Input formats are application features, not JDBC-profile features. XML, JSON,
YAML, CSV, and Parquet support should be present in every JVM and native build.

The grammar lives in: `src/main/antlr4/blater/nestql/core/parser/NestQL.g4`
It is processed by ANTLR4 during the Maven build (see `pom.xml`). The generated parser/lexer sources are in `target/generated-sources/antlr4/` and should not be edited directly.
