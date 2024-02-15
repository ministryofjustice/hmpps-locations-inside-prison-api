# 1. Architecture Overview
Locations inside prison service

[Next >>](0003-nomis-sync-and-migration.md)


Date: 2024-01-23

## Status

Accepted

## Context

This document illustrate where the **locations inside prison service** fits into the wider architecture.

### Components
High level set of components that would make up the internal location management service.

#### Proposed Architecture
The proposed architecture for location management follows a similar pattern to existing services:

- Locations Inside Prison API

- UI Application to allow creation of to locations, viewing and managing existing locations and configuring cells, etc

- A database to store locations, reference data and configuration

- Domain events to indicate new and changes to location information

Below illustrate these components and how they interact with each other.

```mermaid
flowchart TB

subgraph prisonStaff[Prison Staff]
    h1[-Person-]:::type
    d1[Prison Staff \n with NOMIS account]:::description
end
prisonStaff:::person

prisonStaff--Views / Maintains locations -->uiApplication
prisonStaff--Views / Maintains locations in NOMIS -->oracleForms

subgraph locationManagementService[Location Management Service]
    subgraph uiApplication[UI Application]
        direction LR
        h2[Container: Node / Typescript]:::type
        d2[Delivers the content for maintaining locations]:::description
    end
    uiApplication:::internalContainer

    subgraph locationManagementApi[Location Management API]
        direction LR
        h5[Container: Kotlin / Spring Boot]:::type
        d5[Provides location management \n functionality via a JSON API]:::description
    end
    locationManagementApi:::internalContainer

    subgraph database[Location Management Database]
        direction LR
        h6[Container: Postgres Database Schema]:::type
        d6[Stores locations, \n historical changes and reference data]:::description
    end
    database:::internalContainer

    uiApplication--Makes API calls to -->locationManagementApi
    locationManagementApi--Reads from and \n writes to -->database
end
locationManagementService:::newSystem

locationManagementApi--Publishes location \n created/updated events -->domainEvents
locationManagementApi--Audits changes -->auditService

domainEvents<--Listens to location change \n events from NOMIS-->locationManagementApi
    
subgraph otherServices[Other HMPPS Services]
    subgraph prisonApi[Prison API]
        direction LR
        h31[Container: Java/Kotlin / Spring Boot]:::type
        d31[Exposes NOMIS data]:::description
    end
    prisonApi:::internalContainer

end
otherServices:::internalSystem


domainEvents<--Listens to location change events from DPS -->sysconApis

subgraph eventsSystem[Event Pipelines Pub/Sub System]
    subgraph domainEvents[Domain Events]
        direction LR
        h61[Container: SNS / SQS]:::type
        d61[Pub / Sub System]:::description
    end
    domainEvents:::internalContainer
end
eventsSystem:::internalSystem

subgraph auditSystem[Audit Services]
    subgraph auditService[Audit Service]
        direction LR
        h62[Container: Kotlin / Spring Boot]:::type
        d62[Receives and records audit events]:::description
    end
    auditService:::internalContainer
end
auditSystem:::internalSystem

prisonApi--Reads from and \n writes to -->nomisDb

subgraph NOMIS[NOMIS & Related Services]
    subgraph sysconApis[Syscon Services]
        direction LR
        h82[Container: Kotlin / Spring Boot]:::type
        d82[Migration and Sync Management Services]:::description
    end
    sysconApis:::sysconContainer
    subgraph oracleForms[NOMIS front end]
        direction LR
        h91[Container: Weblogic / Oracle Forms]:::type
        d91[Java applet screens surfacing NOMIS data]:::description
    end
    oracleForms:::legacyContainer
    
    subgraph nomisDb[NOMIS Database]
        direction LR
        h92[Container: Oracle 11g Database]:::type
        d92[Stores core \n information about prisoners, \n prisons, finance, etc]:::description
    end
    nomisDb:::legacyContainer

    oracleForms-- read/write data to -->nomisDb
end
NOMIS:::legacySystem


%% Element type definitions

classDef person fill:#90BD90, color:#000
classDef internalContainer fill:#1168bd, color:#fff
classDef legacyContainer fill:purple, color:#fff
classDef sysconContainer fill:#1168bd, color:#fff
classDef internalSystem fill:#A8B5BD
classDef newSystem fill:#D5EAF6, color:#000
classDef legacySystem fill:#A890BD, color:#fff


classDef type stroke-width:0px, color:#fff, fill:transparent, font-size:12px
classDef description stroke-width:0px, color:#fff, fill:transparent, font-size:13px

```

[Next >>](0003-nomis-sync-and-migration.md)