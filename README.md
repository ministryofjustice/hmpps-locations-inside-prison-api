# Locations Inside Prison API
[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.result&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-locations-inside-prison-api)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-github-repositories.html#hmpps-locations-inside-prison-api "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-locations-inside-prison-api/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-locations-inside-prison-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-locations-inside-prison-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-locations-inside-prison-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs)

## Purpose
Provides and API to manage the locations inside a prison.

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
docker compose -f docker-compose-local.yml up
```

Then run the API.

## Architecture

Architecture decision records start [here](docs/0001-use-adr.md)
