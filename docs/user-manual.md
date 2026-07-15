# nestQL User Manual

## Introduction

nestQL is a SQL-like scripting language for moving data between relational databases and hierarchical documents. It lets you write ordinary SQL and add small mapping clauses that describe where values should be read from or written to in a neutral `Hierarchy` tree.

Typical uses are:

- Query a database and render hierarchical output.
- Query arbitrary XML, JSON, YAML, CSV, or Parquet files with SQL by using `--cache`.
- Load XML, JSON, YAML, CSV, or Parquet input and apply `insert`, `update`, `delete`, or stored-procedure calls.
- Capture database rows into an in-memory temp rowset and use those rows in later DML.
- Write returned database values, such as generated keys, back into the input hierarchy.

The core mapping model is format-neutral. XML, JSON, YAML, CSV, and Parquet are boundary adapters around the same `Hierarchy` structure.

## Quick Start

### Minimal Script

Every script contains one or more statements. Prefer `;` as the statement delimiter. `\g` and `\G` are still supported for existing scripts.

```sql
select 1 into {result.value};
```

Run it with JDBC properties:

```bash
nestql script.nql -p database.properties
```

Example `database.properties`:

```properties
jdbc.class.name=org.h2.Driver
jdbc.database=jdbc:h2:mem:demo;MODE=MySQL;DB_CLOSE_DELAY=-1
jdbc.username=sa
jdbc.password=
```

### Query An Input File With `--cache`

`--cache` loads the input file into a persistent local H2 cache, then runs the script against generated SQL tables. No external JDBC properties are required.

Input file:

```json
{
  "data": {
    "customer": [
      { "id": "C1", "country": "GB" },
      { "id": "C2", "country": "US" }
    ]
  }
}
```

Script:

```sql
output json;

select
  c.id into {result.customer.id},
  c.country into {result.customer.country}
from customer c
where c.country = 'GB'
order by c.id asc
structure {result.customer} key (c.id);
```

Run:

```bash
nestql customers.nql customers.json --cache
```

Output:

```json
{"result":{"customer":{"id":"C1","country":"GB"}}}
```

### Query SQL To Hierarchical Output

```sql
select
  personid,
  firstname into {people.person.firstname},
  surname into {people.person.surname}
from person
order by personid asc
structure {people.person} key (personid);
```

`into {people.person.firstname}` maps the selected value into the output hierarchy. `structure {people.person} key (personid)` says that rows with the same person ID contribute to one `person` object.

Default CLI output is XML:

```xml
<people>
  <person>
    <firstname>Alice</firstname>
    <surname>Adams</surname>
  </person>
  <person>
    <firstname>Bob</firstname>
    <surname>Baker</surname>
  </person>
</people>
```

### Use XML Input To Update A Database

Input file:

```xml
<message>
  <person id="7">
    <firstname>Fred</firstname>
  </person>
</message>
```

Script:

```sql
update person
set firstname = {message.person.firstname}
where personid = {message.person.@id};
```

Run:

```bash
nestql update-person.nql input.xml -p database.properties
```

### Use JSON, YAML, CSV, Or Parquet Input

The input file extension selects the input reader when a mapped DML statement needs file input.

JSON:

```json
{
  "message": {
    "person": {
      "id": "7",
      "firstname": "Fred"
    }
  }
}
```

YAML:

```yaml
message:
  person:
    id: "7"
    firstname: Fred
```

Both use the same DML paths:

```sql
insert into person (personid, firstname)
values ({message.person.id}, {message.person.firstname});
```

CSV uses a synthetic root and repeated row nodes:

```csv
person.id,person.firstname
7,Fred
8,Wilma
```

Paths:

```sql
insert into person (personid, firstname)
values ({csv.row.person.id}, {csv.row.person.firstname});
```

Parquet uses domain names from the file stem and schema message name by
default. For `customers.parquet` with `message customer`, DML paths look like:

```sql
insert into customer_snapshot (id, risk)
values ({customers.customer.id}, {customers.customer.risk});
```

Use `--parquet-root` and `--parquet-record` when the physical file or message
name is generic.

### Use Parameters

Parameters can appear in SQL text, DML expressions, and supported string input values.

```sql
select firstname into {people.person.firstname}
from person
where region = '${region:EMEA}';
```

Run:

```bash
nestql people.nql -p database.properties region=APAC
```

`${name}` is replaced by a runtime parameter or Java system property. `${name:default}` uses a default when no value is found.

### Capture Rows And Reuse Them

```sql
capture 'people'
select personid, firstname from person order by personid;

insert into audit_log from temp 'people' (personid, firstname)
values ({personid}, {firstname});
```

`capture` materializes query rows in memory. Later `from temp 'people'` DML reads fields from that temp rowset instead of an input file.

## Command Line Reference

### Usage

nestQL can be launched in three ways.

Invoke the main class directly when the compiled classes and dependencies are already on `CLASSPATH`:

```bash
java blater.nestql.Main script.nql -p database.properties
```

The Maven JAR is a thin JAR. After running `mvn package dependency:copy-dependencies`, invoke it with the dependency
directory on the classpath:

```bash
java -cp 'target/nestql-1.0-SNAPSHOT.jar:target/dependency/*' blater.nestql.Main script.nql -p database.properties
```

Native builds produce `nestql` for common JDBC drivers, `nestql-enterprise` for enterprise drivers, or `nestql-all`
for both sets. All three executables accept the same arguments:

```bash
nestql script.nql -p database.properties
nestql-enterprise script.nql -p database.properties
nestql-all script.nql -p database.properties
```

For simplicity, command examples from this point onward use the default `nestql` executable.

```bash
nestql script-file [load-file] [param=value ...] [-p properties-file] [--output type] [--cache]
```

Arguments can appear in any unambiguous order.

Cache maintenance commands do not require a script:

```bash
nestql --clear-cache
nestql --clear-cache customers.json
nestql --clear-cache-older-than 6h
nestql --list-caches
```

Command-line help does not require a script:

```bash
nestql -h
nestql --help
nestql --help help
nestql --help query
```

`-h` prints brief usage help, while `--help` prints the complete `nestql(1)`
manual page. `--help help` lists the available focused help topics, and
`--help <topic>` prints help for one command or option group.

### Positional Arguments

| Argument      | Meaning                                                  |
|---------------|----------------------------------------------------------|
| `script-file` | Required. The nestQL script to load and run.             |
| `load-file`   | Optional input data file. Required by `--cache`; otherwise used by mapped DML statements. |

If a script only queries the database and produces output, the load file is not read. If a mapped DML statement needs input and the file is missing, execution fails when that statement is reached.

In `--cache` mode, the load file is read before the script runs and is required.

### Options

| Option                     | Meaning                                          |
|----------------------------|--------------------------------------------------|
| `-h`                       | Print brief usage help.                          |
| `--help`                   | Print the complete `nestql(1)` manual page.      |
| `--help help`              | List focused help topics.                        |
| `--help topic`             | Print focused help for a command or option group. |
| `-p properties-file`       | Load parameters from a `.properties` file.       |
| `--db type`                | Select a supported logical database type and infer its driver class and JDBC URL. |
| `--database name`          | Set the simple-form database name, Oracle service name, or H2 URL suffix. |
| `--host host`              | Set the simple-form database host. Defaults to `localhost`; not valid for H2. |
| `--port port`              | Set the simple-form database port. Stable conventional ports are inferred when omitted. |
| `--user username`          | Set `jdbc.username`. |
| `--password password`      | Set `jdbc.password`; use `--password=` for an explicit empty value. |
| `--jdbc-driver name`       | Set the exact `jdbc.driver` logical driver name. |
| `--jdbc-class-name class`  | Set the exact `jdbc.class.name` driver class. |
| `--jdbc-database url`      | Set the complete `jdbc.database` JDBC URL. |
| `--jdbc-username username` | Set the exact `jdbc.username` value. |
| `--jdbc-password password` | Set the exact `jdbc.password` value. |
| `--output type`, `-o type` | Write output as `xml`, `json`, `csv`, or `yaml`. |
| `--cache`                  | Load or reuse the input file's local query cache. |
| `--cache-dir path`         | Use a non-default cache directory.                |
| `--clear-cache`            | Clear all caches, or one cache if followed by an input file. |
| `--clear-cache-older-than duration` | Clear caches not used within a duration such as `30m`, `6h`, or `7d`. |
| `--list-caches`            | List known local query caches.                    |
| `--parquet-root name`      | Override the Parquet hierarchy root name. Also supports `--parquet-root=name`. |
| `--parquet-record name`    | Override the repeated Parquet record node name. Also supports `--parquet-record=name`. |
| `param=value`              | Add or override a runtime parameter.             |

All long JDBC options accept `--option value` and `--option=value`. An equals form with nothing after the equals sign supplies an explicit empty value. A separated option must have a following token; use the equals form for values beginning with `-`.

Only `.properties` parameter files currently load values. Hooks exist for XML, JSON, and YAML parameter files, but they are not implemented.

### JDBC Parameters

The simple command-line form builds a JDBC URL and infers the driver class:

```bash
nestql report.nql \
  --db postgresql \
  --database customer_data \
  --host db.internal.example \
  --port 5544 \
  --user report_user
```

The supported URL and default rules are:

| Database type | Generated JDBC URL | Default port | Username default |
|---------------|--------------------|--------------|------------------|
| `h2` | `jdbc:h2:<database>` | none | `sa` |
| `postgresql` | `jdbc:postgresql://<host>:<port>/<database>` | `5432` | current OS user |
| `mysql` | `jdbc:mysql://<host>:<port>/<database>` | `3306` | current OS user |
| `mariadb` | `jdbc:mariadb://<host>:<port>/<database>` | `3306` | current OS user |
| `oracle` | `jdbc:oracle:thin:@//<host>:<port>/<service>` | `1521` | absent |
| `sqlserver` | `jdbc:sqlserver://<host>:<port>;databaseName=<database>` | `1433` | absent |
| `db2` | `jdbc:db2://<host>:<port>/<database>` | `50000` | absent |
| `hana` | `jdbc:sap://<host>:<port>/?databaseName=<database>` | required | absent |
| `informix` | `jdbc:informix-sqli://<host>:<port>/<database>` | required | absent for trusted-user handling |

The host defaults to `localhost`. HANA and Informix require an explicit port because neither has one safe conventional default. The password is never inferred.

For a complete JDBC URL or custom JVM driver, use the exact form:

```bash
nestql report.nql \
  --jdbc-driver postgresql \
  --jdbc-database 'jdbc:postgresql://db.internal.example:5432/customer_data' \
  --jdbc-username report_user
```

The named exact options map directly to the existing properties:

| Command-line option | Runtime property |
|---------------------|------------------|
| `--jdbc-driver` | `jdbc.driver` |
| `--jdbc-class-name` | `jdbc.class.name` |
| `--jdbc-database` | `jdbc.database` |
| `--jdbc-username` | `jdbc.username` |
| `--jdbc-password` | `jdbc.password` |

The same values can still be supplied in a properties file:

```properties
jdbc.driver=postgresql
jdbc.database=jdbc:postgresql://localhost:5432/customer_data
jdbc.username=report_user
jdbc.password=change-me
```

Properties files provide defaults and command-line values override them regardless of argument position. This makes partial configuration predictable:

```properties
# database.properties
jdbc.username=report_user
jdbc.password=change-me
```

```bash
nestql report.nql -p database.properties --db postgresql --database customer_data
nestql report.nql --db postgresql --database customer_data -p database.properties
```

Both commands use the generated PostgreSQL URL and the credentials from the properties file. A command-line `--user`, `--password`, `--jdbc-*`, or `jdbc.*=value` replaces the corresponding property. Exact command-line `--jdbc-*` values override values inferred from the simple form regardless of argument position. If a command-line property is supplied more than once, its last value wins. An explicit command-line `jdbc.class.name` replaces a `jdbc.driver` inherited from properties; when both are supplied directly on the command line, `jdbc.driver` takes precedence.

The selected executable must contain the requested JDBC dependency. If it does not, driver class loading fails when the connection is opened; nestQL does not maintain a separate build-profile validation registry.

Supplied values are used as written. nestQL does not validate port ranges, encode URL components, or reconcile inconsistent credentials; the JDBC driver or database reports those errors. For example, `--host=` and `--port=` supply explicit empty values rather than selecting defaults, while omitting those options selects documented defaults. The simple form requires `--db` and `--database` together and does not decompose an existing `jdbc.database` URL from a properties file.

JDBC options cannot be combined with `--cache` or cache-maintenance commands because those commands own their local H2 connection. Command-line passwords may be visible in shell history and process listings; prefer a protected properties file for reusable credentials.

### Input File Type Selection

Input file type is selected by file extension:

| Extension       | Input reader |
|-----------------|--------------|
| `.xml`          | XML          |
| `.json`         | JSON         |
| `.yaml`, `.yml` | YAML         |
| `.csv`          | CSV          |
| `.parquet`      | Parquet      |

Extension matching is case-insensitive. Blank or missing input filenames preserve the historical empty XML-input behavior.

### Cache Query Mode

`--cache` loads the input file into a persistent local H2 database before running the script. This mode is for querying arbitrary XML, JSON, YAML, CSV, or Parquet files without supplying external JDBC properties.

Example:

```bash
nestql totals.nql customers.json --cache --output json
```

By default, cache files are stored under:

```text
~/.nestql/cache
```

Use `--cache-dir path` to choose another directory.

Cache reuse is automatic. Once a cache exists for an input path, nestQL reuses it until it is explicitly cleared. The cache is not synchronized with changes to the input file. An explicit Parquet `--parquet-record` name is part of the cache identity because it changes the generated table name; `--parquet-root` is not, because the hierarchy root is not materialized as a cache table. Clearing a Parquet input path clears all record-name variants for that source file.

When a cache is built, nestQL creates input-structure tables. Object names become table names, direct scalar children become columns, and nested objects become related tables.

Input-structure table rules:

- A hierarchy object becomes a materialized table when it carries scalar data or contains child objects.
- The object name becomes the table name, such as `customer`, `country`, or `wallet`.
- Nodes with the same object name share one table.
- Direct scalar children and XML attributes become columns.
- Every structure table has an `id` column.
- If the input object has a scalar `id`, nestQL uses it as the row `id`.
- If the input object has no scalar `id`, nestQL generates one.
- Nested objects are separate tables, not embedded columns.
- Child rows get a containment reference named `<parent>_id`, such as `wallet.customer_id`.
- If the input child already has that `<parent>_id` field, nestQL preserves the input value.
- Repeated direct scalar children become child tables named `<parent>_<field>` with `id`, `<parent>_id`, and `value` columns.
- nestQL does not infer primary key constraints, foreign key constraints, uniqueness, or indexes from the input file.

Example input shape:

```json
{
  "data": {
    "customer": [
      {
        "id": "C1",
        "ccode": "90",
        "wallet": [
          { "symbol": "GBP", "balance": "1.93" },
          { "symbol": "AUD", "balance": "998.33" }
        ]
      },
      {
        "id": "C2",
        "ccode": "90",
        "wallet": [
          { "symbol": "GBP", "balance": "89933.00" }
        ]
      }
    ],
    "country": [
      { "ccode": "89", "name": "vietnam" },
      { "ccode": "90", "name": "vatican city" }
    ]
  }
}
```

Example query:

```sql
output json;

select cn.name into {result.countryName}
from customer cu
inner join country cn on cn.ccode = cu.ccode
where cu.id = 'C1';
```

Example containment query:

```sql
output json;

select w.balance into {result.balance}
from customer cu
inner join wallet w on w.customer_id = cu.id
where cu.id = 'C1'
  and w.symbol = 'AUD';
```

Example grouped query over the sample identity/customer files in `docs/examples`:

```sql
output json;

select
  a.country_code as country_key,
  a.country_code into {result.region.country},
  count(distinct c.id) into {result.region.customerCount}
from customer c
inner join address a on a.customer_id = c.id
inner join kyc k on k.customer_id = c.id
where a.kind = 'residential'
  and k.status <> 'not_started'
group by a.country_code
order by country_key asc
structure {result.region} key (country_key);
```

Run it against any of the equivalent example inputs:

```bash
nestql docs/examples/identity-country-counts.nql docs/examples/identity-customers.json --cache --output json
nestql docs/examples/identity-country-counts.nql docs/examples/identity-customers.yaml --cache --output json
nestql docs/examples/identity-country-counts.nql docs/examples/identity-customers.xml --cache --output json
```

Output:

```json
{"result":{"region":[{"country":"GB","customerCount":"2"},{"country":"US","customerCount":"4"}]}}
```

Repeated scalar values are queryable through child tables rather than packed strings:

```json
{
  "data": {
    "customer": [
      { "id": "C1", "tag": ["vip", "active"] }
    ]
  }
}
```

This creates `customer` and `customer_tag`. Query the tags with:

```sql
select tag.value
from customer_tag tag
where tag.customer_id = 'C1';
```

Input-structure tables are materialized into the persistent cache. Secondary path/value indexes are deferred to a later `--index` enhancement.

### Cache Maintenance

List caches:

```bash
nestql --list-caches
```

The listing shows input type, cache creation time, and source path. Cache metadata is stored inside each H2 cache database.

Clear all caches:

```bash
nestql --clear-cache
```

Clear one input file's cache:

```bash
nestql --clear-cache customers.json
```

Clear caches not used within a duration:

```bash
nestql --clear-cache-older-than 30m
nestql --clear-cache-older-than 6h
nestql --clear-cache-older-than 7d
```

Supported duration units are minutes, hours, and days. Short forms such as `m`, `h`, and `d` are accepted.

Cache maintenance commands accept `--cache-dir`:

```bash
nestql --cache-dir /tmp/nestql-cache --clear-cache
```

### Output Format

Output format is selected in this order:

1. Command-line `--output type` or `-o type`.
2. The first `output type;` directive in the script.
3. XML by default.

Accepted output types are `xml`, `json`, `csv`, and `yaml`, case-insensitively.

Examples:

```bash
nestql people.nql -p database.properties --output json
```

```sql
output yaml;

select firstname into {people.person.firstname}
from person;
```

Only the first script `output` directive is significant. Later `output` directives are parsed but ignored.

## Script Language Reference

### Statement Terminators

Statements normally end with `;`.

```sql
select 1 into {result.value};
```

`\g` and `\G` are also accepted for compatibility with older scripts:

```sql
select 1 into {result.value}
\g
```

A semicolon before `\g` or `\G` is also accepted, but semicolon-only scripts are preferred. Handlers require their own delimiter.

### Output Directive

Use `output` to set the script's preferred output format:

```sql
output json;
```

Accepted formats are `xml`, `json`, `csv`, and `yaml`. The keyword and format are case-insensitive. If multiple `output` directives appear, only the first one is used. A command-line `--output` or `-o` flag overrides the script directive.

### Comments

Line comments:

```sql
-- comment
```

Block comments:

```sql
/* comment */
```

### Keywords And Identifiers

Keywords are case-insensitive.

Identifiers can be quoted with double quotes, square brackets, or backticks where the grammar accepts a name:

```sql
select "first name" into {people.person.firstname} from person;
```

### String Literals

String literals use single quotes. Escape a single quote by doubling it:

```sql
literal insert into audit(message) values ('it''s done');
```

### Templates

Template placeholders are expanded from runtime parameters, then Java system properties, then defaults:

| Syntax            | Meaning                                                               |
|-------------------|-----------------------------------------------------------------------|
| `${name}`         | Replace with parameter/system property named `name`; omit if missing. |
| `${name:default}` | Use `default` when no value is found.                                 |
| `\${name}`        | Render literal `${name}`.                                             |

Examples:

```sql
select personid into {people.person.id}
from person
where region = '${region:EMEA}';
```

```bash
nestql people.nql -p database.properties region=APAC
```

### Paths

Script mapping paths are dot-separated:

```sql
into {people.person.firstname}
```

XML attributes use `@` on the terminal segment:

```sql
into {people.person.@id}
```

Only terminal attributes are supported. JSON, YAML, and CSV do not have attributes, so use ordinary property paths for those formats.

## Query Output Reference

### Plain SELECT

A plain `select` runs as SQL. If it has no `into {path}` mappings, it does not add hierarchy output.

```sql
select count(*) as total from person;
```

### SELECT With Output Mappings

Add `into {path}` to a select item:

```sql
select
  personid,
  firstname into {people.person.firstname}
from person
order by personid asc
structure {people.person} key (personid);
```

The mapping clause is removed from the SQL sent to the database. nestQL creates generated column aliases internally. Key expressions are also projected internally when they are needed to resolve a `structure` path.

SQL aliases are allowed:

```sql
select name as personName into {person.name} from person;
```

### `structure`

Without a `structure` key, mapped object paths below the document wrapper are materialized once per SQL result row. This preserves row multiplicity without guessing how rows should be grouped.

```sql
select
  id into {companies.company.id},
  name into {companies.company.name}
from company
order by id;
```

This produces one `company` object per row. Add a key where joined rows should contribute to the same output object:

```sql
structure {companies.company} key (id)
```

Keys are typed tuples. Use a comma-separated list for a composite identity:

```sql
structure {customers.customer.order} key (o.customer_id, o.order_id)
```

Nested keys coalesce each level independently:

```sql
structure
  {people.person} key (p.personid),
  {people.person.nickname} key (n.nicknameid)
```

Key declarations do not change SQL ordering. Ordinary `order by` controls the first-encounter order of output objects. Add normal SQL tie-breakers when deterministic order matters:

```sql
order by p.surname, p.personid, n.nicknameid
```

Independent repeated siblings can be mapped from one joined result without duplicate output nodes when both paths have keys:

```sql
structure
  {customers.customer} key (c.id),
  {customers.customer.phone} key (p.id),
  {customers.customer.email} key (e.id)
```

### Attributes

For XML output, map terminal values to attributes with `@`:

```sql
select personid into {people.person.@id}
from person
structure {people.person} key (personid);
```

JSON, YAML, and CSV output adapters approximate attributes as ordinary fields.

### Appending Values

`append(space)` combines multiple mapped values into the same output node with a space separator:

```sql
select
  firstname into {people.person.name:append(space)},
  surname into {people.person.name:append(space)}
from person
structure {people.person} key (personid);
```

Output:

```xml
<name>Alice Adams</name>
```

### Null Output

By default, SQL `NULL` is preserved in the hierarchy. XML renders it as:

```xml
<field nil="true" />
```

Use `absent on null` to omit a mapped output field when the SQL value is null:

```sql
case when discount > 0 then discount end
  into {order.line.discount} absent on null
```

### Complete Hierarchical Query Example

This query joins customers, orders, order lines, and products into a three-level output hierarchy. It combines
runtime parameters, mapped XML attributes, appended values, a conditional expression, null omission, and a
`structure` key for every repeated object path.

```sql
output xml;

select
  c.customer_id into {customers.customer.@id},
  c.first_name into {customers.customer.name:append(space)},
  c.last_name into {customers.customer.name:append(space)},
  o.order_id into {customers.customer.order.@id},
  p.sku into {customers.customer.order.line.@sku},
  case when l.discount_amount > 0 then l.discount_amount end
    into {customers.customer.order.line.discount} absent on null
from customer c
join orders o on o.customer_id = c.customer_id
join order_line l on l.order_id = o.order_id
join product p on p.product_id = l.product_id
where c.region = '${region:EMEA}'
  and c.account_status = 'ACTIVE'
  and o.order_date >= date '${fromDate:2026-01-01}'
structure
  {customers.customer} key (c.customer_id),
  {customers.customer.order} key (o.order_id),
  {customers.customer.order.line} key (l.line_id);
```

The three keys allow rows for the same customer, order, and line to contribute to existing objects even when those
rows are not consecutive in the SQL result. The first time each key is encountered determines that object's position
in the output.

Example output:

```xml
<customers>
  <customer id="100">
    <name>Alice Adams</name>
    <order id="9001">
      <line sku="SKU-1" />
      <line sku="SKU-2">
        <discount>5.00</discount>
      </line>
    </order>
    <order id="9002">
      <line sku="SKU-3" />
    </order>
  </customer>
  <customer id="201">
    <name>Brian Baker</name>
    <order id="9110">
      <line sku="SKU-9" />
    </order>
  </customer>
</customers>
```

### `hierarchy union`

`hierarchy union` combines multiple mapped select branches into one hierarchy. It is useful when separate branch queries are clearer or avoid a large joined row expansion. It is not required merely to map repeated siblings: keyed paths also work with one joined select.

```sql
select
  c.id into {customers.customer.id},
  p.id into {customers.customer.phone.id},
  p.number into {customers.customer.phone.number}
from customer c
join phone p on p.customer_id = c.id

hierarchy union

select
  c.id into {customers.customer.id},
  e.id into {customers.customer.email.id},
  e.address into {customers.customer.email.address}
from customer c
join email e on e.customer_id = c.id
structure
  {customers.customer} key (c.id),
  {customers.customer.phone} key (p.id),
  {customers.customer.email} key (e.id);
```

Branches must map under the same document wrapper. nestQL combines them with SQL `union all`, so duplicate source rows are preserved and keys decide whether output objects coalesce.

`using` metadata is only valid on the first `hierarchy union` branch.

### `using` Metadata

Syntax:

```sql
select using schema 'people.xsd' xmlroot = people namespace = 'urn:people'
  firstname into {people.person.firstname}
from person p
structure {people.person} key (p.personid);
```

Current behavior:

- `namespace = '...'` is applied to XML root output.
- `schema` and `xmlroot` are parsed as metadata markers but do not currently validate or reshape output.
- `using` requires at least one hierarchy mapping.
- `using` is not allowed on non-first `hierarchy union` branches.

### SQL Constructs

SQL expressions are mostly passed through to the database. Examples known to parse include:

```sql
case when category = 'A' then firstname end into {people.person.staffName}
```

```sql
group by department, upper(surname)
having count(*) > 1
order by upper(surname) asc, 1 desc
```

The parser recognizes nestQL mapping markers at top level and otherwise leaves SQL text to the database.

## DML Input Reference

Mapped DML reads values from a loaded input hierarchy or a captured temp rowset and binds them as SQL parameters.

### Input File Formats

XML:

```xml
<message>
  <person id="7">
    <firstname>Fred</firstname>
  </person>
</message>
```

Paths:

```sql
{message.person.@id}
{message.person.firstname}
```

JSON and YAML:

- A single top-level object key becomes the root.
- Multiple top-level keys use synthetic roots `json` or `yaml`.
- Named arrays become repeated child nodes.
- Null values become hierarchy null values.

CSV:

- Root is `csv`.
- Each record is a repeated `row`.
- Headers become child paths under each row.
- Dotted headers create nested nodes.

Example:

```csv
person.id,person.firstname
7,Fred
```

Paths:

```sql
{csv.row.person.id}
{csv.row.person.firstname}
```

Parquet:

- The root defaults to the projected input file stem, such as `customers` from `customers.parquet`.
- The repeated record node defaults to the projected Parquet schema message name, such as `customer`.
- Use `--parquet-root` and `--parquet-record` when physical names are generic or unstable.
- Unsafe Parquet names are projected to path-safe names. For example, `event.time` becomes `event_time` and `1st_seen` becomes `_1st_seen`.
- Projection collisions fail clearly. For example, `user-id` and `user_id` both project to `user_id`.
- Single-valued scalar structs flatten into owner fields, such as `profile_score`.
- Repeated structs remain child records.
- Parquet map keys become fields or child nodes on the owning record. The map wrapper and technical `key` / `value` rows are not exposed.

Example DML paths for `customers.parquet` with `message customer`:

```sql
{customers.customer.id}
{customers.customer.risk}
{customers.customer.profile_score}
```

### INSERT

With an input file:

```sql
insert into person (personid, firstname)
values ({message.person.id}, {message.person.firstname});
```

Without a column list, table column order is used:

```sql
insert into audit_log
values ({message.person.id}, {message.person.firstname});
```

With literals and expressions:

```sql
insert into audit_log (personid, status, nickname)
values ({person.id}, 'NEW', coalesce({person.nickname}, 'none'));
```

A DML expression can contain one mapped source path. If it contains no mapped source, it is treated as a literal expression.

### UPDATE

```sql
update person
set firstname = upper({person.firstname}),
    lastupdated = coalesce({person.lastUpdated}, 'fallback')
where personid = {person.@id};
```

Values in the `where` clause are key fields for row matching.

### DELETE

```sql
delete from audit_log
where personid = {person.id};
```

`delete` does not support `returns`.

### RETURNS

`returns` writes database-assigned values back into the input hierarchy after an `insert` or `update`.

```sql
insert into person (firstname)
values ({person.firstname})
returns personid into {person.id};
```

Multiple returned values are allowed:

```sql
update person
set firstname = {person.firstname}
where personid = {person.id}
returns lastupdated into {person.lastUpdated},
        version into {person.version};
```

`returns` requires a mapped input source and is not supported on `delete` or `insert ... select`.

### Stored Procedures

```sql
execute procedure update_person(firstname = {person.firstname});
```

Procedure arguments are mapped similarly to DML columns.

### Repeated Input Rows

When input contains repeated nodes, nestQL infers row context from mapped paths.

XML:

```xml
<message>
  <person id="7">
    <nickname id="10">Ace</nickname>
    <nickname id="11">Al</nickname>
  </person>
</message>
```

Script:

```sql
insert into nickname (nicknameid, personid, nickname)
values ({message.person.nickname.@id}, {message.person.@id}, {message.person.nickname});
```

This inserts two rows.

Ambiguous independent repeated branches are reported as statement problems. Use `onWarning` to choose whether to fail or continue.

### Temp Rowsets

Capture query results:

```sql
capture 'people'
select personid, firstname from person order by personid;
```

Insert from captured rows:

```sql
insert into audit_log from temp 'people' (personid, firstname)
values ({personid}, {firstname});
```

Update from captured rows:

```sql
update audit_log from temp 'people'
set firstname = {firstname}
where personid = {personid};
```

Delete from captured rows:

```sql
delete from audit_log from temp 'people'
where personid = {personid};
```

Temp row field names are matched case-insensitively by lowercased column label.

## Statement Reference

### `autocommit`

```sql
autocommit on;

autocommit off;
```

Calls `Connection.setAutoCommit(...)`.

### `literal`

Use `literal` to run SQL exactly as written, with template expansion at execution time.

```sql
literal create table audit_log (message varchar(80));
```

Some SQL-like DML without mapped sources is also treated as literal SQL:

```sql
insert into audit_log (message) values (${MESSAGE});
```

### `capture`

```sql
capture 'rowset_name'
select id, name from source_table;
```

The SQL after `capture 'name'` is stored as the capture query.

### `include` And `submapping`

`include` and `submapping` are textual inclusion directives handled before parsing.

```sql
include 'setup.sql'
submapping 'person-summary.nql'
```

Included paths are resolved relative to the including file. Circular includes fail. Both directive names behave the same.

## Error Handling Reference

Each statement can be followed by handler statements.

```sql
update menu
set dishname = {message.dish.dishname}
where dishid = {message.dish.@id};
onError('bad update', abort, rollback);
onWarning('ambiguous input', rollback);
```

Handlers:

| Handler          | Applies to                                          |
|------------------|-----------------------------------------------------|
| `onError(...)`   | SQL execution errors.                               |
| `onWarning(...)` | Statement problems such as ambiguous input mapping. |

Flags:

| Flag       | Meaning                                                                    |
|------------|----------------------------------------------------------------------------|
| `abort`    | Fail the script. This is the default behavior if no handler is present.    |
| `rollback` | Roll back the active JDBC connection before applying the handler behavior. |

If a handler has no `abort`, behavior is best effort: log and continue.

## Format Reference

### XML Input

- Root element becomes the hierarchy root.
- Elements become nodes.
- Attributes become child nodes marked as attributes.
- Leaf element text and attribute values are template-expanded.
- Empty filename or empty file returns an empty hierarchy.
- Malformed XML fails.

### JSON Input

- One top-level property becomes the hierarchy root.
- Multiple top-level properties use synthetic root `json`.
- Object properties become child nodes.
- Arrays under named properties become repeated child nodes with that property name.
- Anonymous arrays use `item` nodes.
- String values are template-expanded.
- Numbers and booleans become string values.
- JSON null becomes a null node.

### YAML Input

YAML input follows the same structure rules as JSON:

- One top-level mapping key becomes the hierarchy root.
- Multiple top-level keys use synthetic root `yaml`.
- Mappings become child nodes.
- Sequences under named keys become repeated child nodes.
- Anonymous sequences use `item` nodes.
- String scalars are template-expanded.
- Numbers and booleans become string values.
- YAML null becomes a null node.

### CSV Input

- Root is `csv`.
- Rows are repeated `row` nodes.
- Headers become child names.
- Dotted headers become nested paths.
- Quoted commas, quotes, and newlines are parsed as normal CSV.
- Missing trailing cells become empty strings.
- Empty filename or empty file returns an empty hierarchy.

Example:

```csv
person.id,person.firstname
7,Fred
```

Paths:

```text
/csv/row/person/id
/csv/row/person/firstname
```

### Parquet Input

Parquet input is domain-shaped rather than storage-shaped:

- The root node defaults to the file stem after `.parquet` is removed.
- Each Parquet row becomes a repeated node named from the schema message name.
- Primitive fields become scalar child fields.
- Single-valued structs whose children are scalar fields flatten into owner fields using `<struct>_<field>`.
- Repeated structs and non-scalar nested groups become child nodes.
- List and repeated scalar values become repeated child nodes.
- String and enum values are template-expanded; other scalar values become strings.
- Null optional scalar fields become null nodes.
- Binary or fixed values without a supported logical type become Base64 strings.

Naming:

- `--parquet-root customers` overrides the root.
- `--parquet-record customer` overrides the repeated record node.
- Equals forms are accepted: `--parquet-root=customers` and `--parquet-record=customer`.
- Unsafe names are projected to path-safe names without changing case. Examples: `event.time` to `event_time`, `user-id` to `user_id`, `1st_seen` to `_1st_seen`.
- Projected-name collisions fail instead of being silently renamed.

Map fields:

- Map keys become public domain fields on the owning record.
- Scalar map values become direct fields.
- Simple nested-record map values flatten using `<key>_<child>`.
- List map values become repeated nodes under the key.
- The map wrapper name and technical `key` / `value` rows are not public hierarchy or cache names.

For a Parquet `customer` record with `attributes = {risk: low, tier: gold}`,
the cache table is queried as:

```sql
select risk, tier
from customer
where id = 'C1';
```

### XML Output

- Root hierarchy node becomes the XML root element.
- Attribute nodes render as XML attributes.
- Null nodes render with `nil="true"`.
- Namespace metadata from `using namespace = ...` is applied to the root.

### JSON Output Adapter

Writer: `JsonOutputWriter`.

- Root node becomes a single top-level JSON property.
- Child containers become nested objects.
- Repeated sibling names become arrays.
- Value nodes become JSON strings.
- Null nodes become JSON `null`.
- Attributes become ordinary properties.
- Attribute/element name conflicts preserve both as an array.

### YAML Output Adapter

Writer: `YamlOutputWriter`.

- Root node becomes a top-level mapping key.
- Child containers become nested mappings.
- Repeated sibling names become sequences.
- Value nodes become quoted string scalars.
- Null nodes become YAML null.
- Attributes become ordinary properties.
- Attribute/element name conflicts preserve both as a sequence.

### CSV Output Adapter

Writer: `CsvOutputWriter`.

CSV is a flat format, so hierarchy is flattened:

- If the root contains only one repeated child group, those children become CSV records.
- Otherwise, the root itself is written as one record.
- Nested scalar nodes become dotted column names.
- Repeated scalar nodes join in one cell with `|`.
- Repeated nested object nodes serialize as compact JSON in one cell.
- Attributes become ordinary columns.
- Null nodes become empty cells.

## Known Limitations

- XML, JSON, and YAML parameter files passed through `-p` are recognized by extension but currently do not load parameters.
- `schema` and `xmlroot` metadata are parsed but do not currently perform validation or reshape output.
- DML expressions currently support one mapped source path per SQL expression.
- Core path support is simple path traversal, not full XPath, JSONPath, or YAMLPath.
- CSV output approximates nested repeated objects because CSV has no native hierarchy.
- `--cache` input-structure tables do not infer parent-child joins. Use scalar fields present in the input for natural joins.
