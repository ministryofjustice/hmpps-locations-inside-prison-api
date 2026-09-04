# Locations Inside Prison API
[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-locations-inside-prison-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-locations-inside-prison-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-locations-inside-prison-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-locations-inside-prison-api/main/async-api.yml)
[![Data dictionary](https://img.shields.io/badge/Data_dictionary-view-85EA2D.svg)](https://ministryofjustice.github.io/hmpps-locations-inside-prison-api/schema-spy-report/)


## Purpose
Provides and API to manage the locations inside a prison.

- [Guidelines](./guidelines.md)
- [High Level Design](./docs/high-level-design.md) — the current architecture across the API and both UIs
- [Data Flow Diagram](./docs/data-flow-diagram.md) — data flows and trust boundaries, maintained for threat modelling
- [Architecture Decision Records](./docs/0001-use-adr.md)
- [Data dictionary](https://ministryofjustice.github.io/hmpps-locations-inside-prison-api/schema-spy-report/) — the published database schema, descriptions and sensitivity classifications


## Getting Started

## Running locally against dev services

This is straight-forward as authentication is delegated down to the calling services in `dev` environment.
Environment variables to be set are as follows:

```
API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth
API_BASE_URL_PRISON=https://prison-api-dev.prison.service.justice.gov.uk
LOCATIONS_INSIDE_PRISON_API_CLIENT_ID=[choose a suitable hmpps-auth client]
LOCATIONS_INSIDE_PRISON_API_CLIENT_SECRET=
```

Start the database and other required services via docker-compose with:

```shell
docker compose up
```

Then run the API.

### Running the whole setup in docker

```shell
docker compose --profile include-api up --build
```

## Architecture

Architecture decision records start [here](docs/0001-use-adr.md)


## Data dictionary

A browsable schema report is published from `main` to
[ministryofjustice.github.io/hmpps-locations-inside-prison-api/schema-spy-report](https://ministryofjustice.github.io/hmpps-locations-inside-prison-api/schema-spy-report/),
along with two CSV exports for the MOJ Data Catalogue:

| File | Contents |
| --- | --- |
| `data-dictionary.csv` | One row per column: table, column, type, nullability, default, description, sensitivity classification, primary key flag and foreign key target. |
| `reference-data.csv` | The permitted values behind coded columns — the `constant_transaction_type` table, plus every Kotlin enum persisted as a string. Without this a consumer sees a `varchar(60)` with no idea which values are legal. |

The report is generated from a database built by Flyway, so it cannot drift from the migrations. It
supersedes the hand-maintained [location DB design note](./docs/0004-location-db-design.md) and
`docs/schema.png`.

To regenerate it locally:

```shell
docker compose -f docker-compose-schema-spy.yml up -d --wait
./gradlew -Pinit-db=true test --tests '*InitialiseDatabase' --tests '*ExportReferenceData'
docker run --rm --network host -v /tmp/schemaspy:/output schemaspy/schemaspy:6.2.4 \
  -t pgsql -host localhost -port 5432 -db locations_inside_prison -s public \
  -u locations_inside_prison -p locations_inside_prison -vizjs
scripts/generate-data-dictionary.sh
```

Tear the database down with `docker compose -f docker-compose-schema-spy.yml down -v` when you are
done. Editing the comments migration while the container is still up gives a Flyway checksum mismatch
on the next run.

The stored procedures in `db/routines` and the seed data in `db/seed` and `db/training` are applied
only under the `seed` and `train` profiles, so they are not on the default Flyway path and do not
appear in the report. That is intentional — the report shows what `dev`, `preprod` and `prod` have.

### Table and column descriptions

Descriptions are held in the database itself as `COMMENT ON` statements, applied by
[`V1_104__schema_comments.sql`](./src/main/resources/db/migration/V1_104__schema_comments.sql). Keeping
them in `pg_description` means the SchemaSpy report, the CSV export and any Glue crawl all share one
source of truth.

### Data sensitivity

Every column description ends with a classification tag:

| Tag | Meaning |
| --- | --- |
| `[Sensitivity: NONE]` | Not personal data in itself — keys, timestamps, process flags, published capacity figures |
| `[Sensitivity: PERSONAL]` | Personal data about a prisoner — identifies or locates them |
| `[Sensitivity: STAFF]` | Personal data about a member of staff, typically the username that acted |
| `[Sensitivity: SPECIAL-CATEGORY]` | UK GDPR Article 9 data, or offence data under Article 10 |
| `[Sensitivity: OFFICIAL-SENSITIVE]` | Not personal data, but damaging if disclosed |

`STAFF` is still personal data and still in scope for a staff member's own subject access request. It
is separated from `PERSONAL` so that an extract about prisoners can be reasoned about without staff
columns inflating the count, and so staff data can be dropped or pseudonymised independently.

This schema holds no prisoner identifiers, so most columns are `NONE`. The classifications that need
care here are the capacity figures: those MoJ already publishes are `NONE`, while proposed,
not-yet-approved figures on `certification_approval_request`,
`certification_approval_request_location` and `cell_certificate_upload_location` are
`OFFICIAL-SENSITIVE`.

**Any new table or column needs a `COMMENT ON`** in a migration — `SchemaCommentsTest` fails the build
otherwise. A later migration can add to or replace any comment at any time. Likewise a new enum value
needs a description in `ExportReferenceData`, which fails rather than exporting a blank row.

## Testing coverage report

Run:

```
./gradlew koverHtmlReport
```

Then view output file for coverage report.

### HMPPS-template-kotlin update 
The project is sync with https://github.com/ministryofjustice/hmpps-template-kotlin  
Sync date: `25/10/2024` sync commit: `7426643351775e175fc5e77101fd9226fe66ef86`

#### How to sync the project with HMPPS-template-kotlin
Check if remote repository is added by `git remote -v`.
if repository is not present run `git remote add hmpps-kotlin-tamplate git@github.com:ministryofjustice/hmpps-template-kotlin.git`.
Fetch the newest changes from hmpps-kotlin-template calling `git fetch hmpps-kotlin-template`.
Find out missing commits https://github.com/ministryofjustice/hmpps-template-kotlin/commits/main/ following `sync commit` above. 
Cherry Pick the changes by calling `git cherry-pick <<commit_id>>..<<commit_id>>` if you want to Cherry pick only one commit call `git cherry-pick <<commit_id>>`
Resolve the conflict and update **sync date** and **sync commit** in README.md to guide the next person on what was done.  