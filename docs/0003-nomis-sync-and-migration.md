# 1. Mastering and synchronisation of Internal location data with NOMIS

[Next >>](0004-location-db-design.md)


Date: 2024-01-23

## Status

Accepted

## Context

This document will cover the approach for the locations inside prison service to "own" the internal location data and synchronisation this information back into NOMIS

### Migration plan for "Locations Inside Prison Service" - Moving data off NOMIS approach
A two-way sync and migration pattern is to be adopted.

Therefore, the approach will be to :-
- Keep NOMIS up to date with changes made in Locations Inside Prison Service
- Provide a two-way sync and keep both systems in synchronisation so that turning off access to old NOMIS screens can be done prison by prison
- Migrate all the data from NOMIS
- Once all prisons had been switched over - turn off NOMIS -> DPS sync (now 1 way only)
- All access to screen removed

Steps taken:
The Move a Prisoner Team.
- Model the Locations Inside Prison data in a new database 
- Build API functionality for managing the data 
- Provide Locations Inside Prison endpoint in API that mirrors data in NOMIS
- Raise events on creation or amendments of locations
- Build a "sync" and "migrate" endpoints to allow NOMIS to send location data to Locations Inside Prison API

On NOMIS (syscon)
- Call Locations Inside Prison API "sync" endpoints when any updates where made to internal location data in NOMIS
- Listen to events when locations where created in DPS and store them in NOMIS
- Migrate all the data held on internal locations in NOMIS by calling API "migrate" endpoint
- Reconcile mismatches with weekly checks
- Remove all location endpoints in prison-api once all services are using new API

Data is still held in NOMIS and will be maintained for existing processes to continue to function.

### NOMIS synchronisation sequence
When a change is made to a location either a creation or update, events are be fired. The sequence of events for syncing back to NOMIS is shown below:

```mermaid
sequenceDiagram

    actor Prison Staff
    participant Locations Inside Prison UI
    participant Locations Inside Prison API
    participant Locations Inside Prison Database
    participant Domain Events
    participant HMPPS Prisoner to NOMIS update
    participant HMPPS NOMIS Prisoner API
    participant NOMIS DB

    Prison Staff ->> Locations Inside Prison UI: Maintain Locations Inside Prison

    Locations Inside Prison UI ->> Locations Inside Prison API: Call API with changes
    activate Locations Inside Prison API
    Locations Inside Prison API->>Locations Inside Prison Database: update DB
    Locations Inside Prison API->>Domain Events: domain event raised
    Note over Locations Inside Prison API, Domain Events: location.inside.prison.* event raised
    Locations Inside Prison API-->>Locations Inside Prison UI: Saved location returned
    deactivate Locations Inside Prison API
    
    Domain Events-->>HMPPS Prisoner to NOMIS update: Receives location.inside.prison.* domain event
    activate HMPPS Prisoner to NOMIS update
    HMPPS Prisoner to NOMIS update->>HMPPS NOMIS Prisoner API: Update NOMIS with internal location data
    HMPPS NOMIS Prisoner API ->> NOMIS DB: Persist data into the internal location tables
    deactivate HMPPS Prisoner to NOMIS update

```

## Key components and their flow for internal locations sync
```mermaid
    
graph TB
    X((Prison Staff)) --> A
    A[Locations Inside Prison UI] -- update locations --> B
    B[Locations Inside Prison API] -- Store locations --> D[[Locations Inside Prison DB]]
    B -- Location Updated Event --> C[[Domain Events]]
    C -- Listen to events --> E[HMPPS Prisoner to NOMIS update]
    E -- Update NOMIS via API --> F[HMPPS NOMIS Prisoner API]
    F -- persist --> G[[NOMIS DB]]
    R[HMPPS Prisoner from NOMIS Migration] -- perform migration --> B
    R -- record history --> H[[History Record DB]]
    K[HMPPS NOMIS Mapping Service] --> Q[[Mapping DB]]
    R -- check for existing mapping --> K
    R -- 1. find out how many to migrate, 2 get locations details --> F
```


#### Domain Event Types:
In all instances the domain event will contain the unique reference to a location.
- location.inside.prison.created 
- location.inside.prison.amended
- location.inside.prison.deactivated
- location.inside.prison.reactivated
- location.inside.prison.capacity.changed
- location.inside.prison.certification.changed
- location.inside.prison.deleted

**Example:**
```json
{
  "eventType": "location.inside.prison.amended",
  "occurredAt": "2023-03-14T10:00:00",
  "version": "1.0",
  "description": "Location LEI-A-1-003 amended",
  "additionalInformation": {
    "locationId": "38c862ee-dbd8-4774-bdd2-499092a4a01e",
    "locationKey": "LEI-A-1-003"
  }
}
```


## API endpoints for sync

### Sync endpoints for locations inside prison
This endpoint will contain all the information need to populate the locations database with a location updated in NOMIS
- `GET /locations/sync/upsert`
- `GET /locations/sync/delete`
- 
### Migration endpoint for locations inside prison
This endpoint will contain all the information need to populate the locations database with a specific location
- `POST /locations/migrate`


## Decision
- Migration process will be trialed in pre-prod and UAT testing will be needed to check mappings have accurately represented location data



[Next >>](0004-location-db-design.md)
