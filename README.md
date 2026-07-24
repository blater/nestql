# nestQL

nestQL is a command-line SQL tool for querying JSON, YAML, XML, CSV and Parquet files alongside relational databases reached through JDBC. It can return tabular results, assemble query results into nested documents, and apply database changes using values read from structured files.

NestQL

## Getting Started

### Querying and Filtering a JSON file

This example finds every record containing the highest value, using SQL instead of a format-specific query language.

Given [`elements.json`](docs/examples/jq/elements.json):

```json
[
  {"a": 1, "id": 1},
  {"a": 2, "id": 2},
  {"a": 2, "id": 3},
  {"a": 1, "id": 4}
]
```

Run an inline query against the file's local cache:

```bash
nestql 'select id from item where a in (select max(a) from item);' docs/examples/jq/elements.json --cache
```

The result contains both records tied for the maximum:

```json
[{"id":"2"},{"id":"3"}]
```

The cache makes the file queryable as SQL tables. It is persistent, so the same file can be loaded once and queried repeatedly.

### Extract data from a database

This example retrieves selected customer records from an existing database and returns them as JSON.

Suppose the database contains a __customer__ table:

| id | name  | status     |
|---:|-------|------------|
| 1  | Alice | active     |
| 2  | Bob   | inactive   |
| 3  | Eva   | active     |

Run an inline SQL query:

```bash
nestql \
  "select id, name from customer where status = 'active' order by id;" \
  -p database.properties --output json
```

Result:

```json
[{"id":"1","name":"Alice"},{"id":"3","name":"Eva"}]
```

The selected build must contain the JDBC driver for the target database.

### Create a nested document for export

This example produces a customer document with each customer's orders nested underneath, ready to save as JSON, XML or YAML or pass to another application.

A relational join returns one row for every customer/order combination:

| customer_id | customer_name | order_id | total |
|------------:|---------------|---------:|------:|
| 1           | Alice         | 10       | 24.50 |
| 1           | Alice         | 11       | 13.00 |
| 2           | Bob           | 12       | 40.00 |
| 2           | Bob           | 13       | 5.00  |

#### Inference and `structure`

nestQL must decide when multiple SQL rows describe the same customer or child object. By default it infers object identity and parent-child relationships from JDBC metadata and naming conventions, using primary, unique and composite keys where available; it does not sample result rows to guess.

Most mapped queries therefore need no `structure` clause. Add one when suitable key metadata is missing, inference is ambiguous, or—as in this export—the intended identity of each output object should be stated explicitly.

`into` paths describe the desired document, while `structure` declares the identity of the repeated objects:

```sql
output json;

select
  c.id    into {customers.customer.id},
  c.name  into {customers.customer.name},
  o.id    into {customers.customer.order.id},
  o.total into {customers.customer.order.total}
from customer c
left join customer_order o on o.customer_id = c.id
order by c.id, o.id
structure
  {customers.customer} key (c.id),
  {customers.customer.order} key (c.id, o.id);
```

Run the script against the database:

```bash
nestql customer-orders.nql -p database.properties
```

Result:

```json
{
  "customers": {
    "customer": [
      {
        "id": "1",
        "name": "Alice",
        "order": [
          {"id": "10", "total": "24.50"},
          {"id": "11", "total": "13.00"}
        ]
      },
      {
        "id": "2",
        "name": "Bob",
        "order": [
          {"id": "12", "total": "40.00"},
          {"id": "13", "total": "5.00"}
        ]
      }
    ]
  }
}
```

The declarations state that rows with the same customer ID contribute to one customer, while rows with the same customer and order IDs contribute to one order. An explicit key overrides inference for that output path; if no key is declared or inferred, nestQL preserves row-first output for the path.

To write the result to a file, use the required output format and ordinary shell redirection:

```bash
nestql customer-orders.nql -p database.properties --output yaml > customer-orders.yaml
```

### Apply data from a file to a database

This example uses values from an incoming JSON document to update the corresponding database record.

Given `person.json`:

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

The script `update-person.nql` reads values through mapping paths:

```sql
update person
set first_name = {message.person.first_name}
where person_id = {message.person.id};
```

Apply it as follows (see the usage section for more about how to configure a db properties file with your connection information)

```bash
nestql update-person.nql person.json -p database.properties
```

The row whose `person_id` is `7` is updated to use the name `Fred`. Equivalent YAML and XML structures can use the same mapping paths; CSV and Parquet inputs are also supported.

### Produce a summary from nested data

This example joins related customer, address and verification data from a nested file, then produces a country-level summary for reporting or downstream processing.

The complete [`identity-customers.json`](docs/examples/identity-customers.json) contains customers with nested addresses and KYC records. A reduced excerpt shows the relevant shape:

```json
{
  "identity_data": {
    "customer": [
      {
        "id": "C2001",
        "address": [
          {
            "id": "A4001",
            "kind": "residential",
            "country_code": "GB"
          }
        ],
        "kyc": {
          "id": "K5001",
          "status": "approved"
        }
      }
    ]
  }
}
```

[`identity-country-counts.nql`](docs/examples/identity-country-counts.nql) joins the generated cache tables, filters the source rows, aggregates them and maps the result:

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
order by country_key
structure {result.region} key (country_key);
```

Run it against the complete example:

```bash
nestql \
  docs/examples/identity-country-counts.nql \
  docs/examples/identity-customers.json \
  --cache
```

Result:

```json
{"result":{"region":[{"country":"GB","customerCount":"2"},{"country":"US","customerCount":"4"}]}}
```

See the [nestQL user manual](docs/user-manual.md) for the complete language and command-line reference, and [`docs/examples`](docs/examples/) for further runnable examples.

## Usage

The principal command forms are:

```text
nestql <script-file-or-text> [input-file] [name=value ...] [options]
nestql <input-file> [cache-options]
nestql catalog [table-pattern] [options]
nestql --use-cache <input-file-or-cache-filename> [cache-options]
nestql --list-caches [cache-options]
nestql --clear-cache [input-file-or-cache-filename] [cache-options]
```

A script can be a `.nql` file or one quoted inline argument. Options and positional arguments can appear in any unambiguous order.

### Files and caches

Input formats are selected by extension:

- `.json`
- `.yaml` or `.yml`
- `.xml`
- `.csv`
- `.parquet`

Supplying an input file on its own loads it into a persistent local H2 cache and makes that cache active:

```bash
nestql customers.json
nestql first-query.nql
nestql second-query.nql
```

The default cache directory is `~/.nestql/cache`. Common cache operations are:

```bash
nestql --list-caches
nestql --use-cache customers.json
nestql --clear-cache customers.json
nestql --clear-cache-older-than 7d
```

Use `--cache-dir <path>` to select another cache directory. `--use-cache` and targeted `--clear-cache` also accept a bare `cache-*.mv.db` filename, resolved under the selected cache directory.

Inspect the active cache or configured database with:

```bash
nestql catalog
nestql catalog customer
nestql catalog 'audit*'
```

### Database connections

Connection details can be kept in a database properties file:

```properties
jdbc.driver=postgresql
jdbc.database=jdbc:postgresql://localhost:5432/customer_data
jdbc.username=report_user
jdbc.password=change-me
```
The supported logical driver names are `h2`, `mysql`, `mariadb`, `postgresql`, `oracle`, `sqlserver`, `db2`, `hana` and `informix`. Exact JDBC settings are available through `--jdbc-driver`, `--jdbc-class-name`, `--jdbc-database`, `--jdbc-username` and `--jdbc-password`.

You can specify the database properties in the nestql command using the "-p filename" flag. e.g.:

```bash
nestql myscript.nql -p mydbprops.properties
```

You can also provide connection details directly (though it's not recommended to use --password outside of a dev environment):

```bash
nestql report.nql \
  --db mysql \
  --database customer_data \
  --host localhost \
  --user report_user \
  --password=change-me
```

### Output and help

Output defaults to JSON. Select another format in the script like this:

```sql
output xml;
```

You can also specify the output format on the command line with the "--output" flag
```bash
nestql report.nql -p database.properties --output yaml
```
Supported output formats are JSON, YAML, XML, CSV and Markdown.

Use the built-in help for the current command-line reference:

```bash
nestql -h
nestql --help
nestql --help cache
nestql --help connection
```

## How to build

nestQL requires JDK 25 and Maven. A GraalVM JDK with Native Image is required only for native executables.

### JVM build

Run the test suite and create the executable fat JAR:

```bash
mvn test
mvn package
```

Run the packaged application with:

```bash
java -jar target/nestql-*.jar -h
```

The default `jdbc-common` profile includes H2, MySQL, MariaDB and PostgreSQL. Alternative driver sets are:

```bash
mvn -Pjdbc-common package
mvn -Pjdbc-enterprise package
mvn -Pjdbc-all package
```

- `jdbc-common`: H2, MySQL, MariaDB and PostgreSQL.
- `jdbc-enterprise`: Oracle, SQL Server, Db2, SAP HANA and Informix, plus H2 for cache support.
- `jdbc-all`: all common and enterprise drivers.

### Native build

With a GraalVM JDK and `native-image` available:

```bash
mvn -Pjdbc-common,native -DskipTests package
mvn -Pjdbc-enterprise,native -DskipTests package
mvn -Pjdbc-all,native -DskipTests package
```

The resulting executable names are:

- `nestql` for `jdbc-common`
- `nestql-enterprise` for `jdbc-enterprise`
- `nestql-all` for `jdbc-all`

