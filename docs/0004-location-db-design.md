# 1. Database proposed schema for holding locations inside prison

[Next >>](9999-end.md)


Date: 2024-01-24    

## Status

Accepted

## Context
This illustrates the entities that will be held in the database for locations inside prison service

```mermaid
---
title: Locations Inside Prison Database ER Diagram
---

classDiagram
    direction BT
    class capacity {
        integer capacity
        integer operational_capacity
        integer id
    }
    class certification {
        boolean certified
        integer capacity_of_certified_cell
        integer id
    }
    class location {
        varchar(3) prison_id
        varchar(150) path_hierarchy
        varchar(40) code
        varchar(30) location_type
        uuid parent_id
        varchar(80) description
        varchar(255) comments
        integer order_within_parent_location
        varchar(30) residential_housing_type
        bigint certification_id
        bigint capacity_id
        boolean active
        date deactivated_date
        varchar(30) deactivated_reason
        date reactivated_date
        timestamp when_created
        timestamp when_updated
        varchar(80) updated_by
        uuid id
    }
    class locations
    class non_residential_usage {
        uuid location_id
        varchar(60) usage_type
        integer capacity
        integer sequence
        integer id
    }
    class residential_attribute {
        uuid location_id
        varchar(60) attribute_type
        varchar(60) attribute_value
        integer id
    }

    location  -->  capacity : capacity_id
    location  -->  certification : certification_id
    location  -->  location : parent_id
    non_residential_usage  -->  location : location_id
    residential_attribute  -->  location : location_id
```

### NOMIS to DPS type translations

#### Location Types
| NOMIS | DPS                 |
|-------|---------------------|
| ADJU  | ADJUDICATION_ROOM   |
| ADMI  | ADMINISTRATION_AREA |
| APP   | APPOINTMENTS        |
| AREA  | AREA                |
| ASSO  | ASSOCIATION         |
| BOOT  | BOOTH               |
| BOX   | BOX                 |
| CELL  | CELL                |
| CLAS  | CLASSROOM           |
| EXER  | EXERCISE_AREA       |
| EXTE  | EXTERNAL_GROUNDS    |
| FAIT  | FAITH_AREA          |
| GROU  | GROUP               |
| HCEL  | HOLDING_CELL        |
| HOLD  | HOLDING_AREA        |
| IGRO  | INTERNAL_GROUNDS    |
| INSI  | INSIDE_PARTY        |
| INTE  | INTERVIEW           |
| LAND  | LANDING             |
| LOCA  | LOCATION            |
| MEDI  | MEDICAL             |
| MOVE  | MOVEMENT_AREA       |
| OFFI  | OFFICE              |
| OUTS  | OUTSIDE_PARTY       |
| POSI  | POSITION            |
| RESI  | RESIDENTIAL_UNIT    |
| ROOM  | ROOM                |
| RTU   | RETURN_TO_UNIT      |
| SHEL  | SHELF               |
| SPOR  | SPORTS              |
| SPUR  | SPUR                |
| STOR  | STORE               |
| TABL  | TABLE               |
| TRAI  | TRAINING_AREA       |
| TRRM  | TRAINING_ROOM       |
| VIDE  | VIDEO_LINK          |
| VISIT | VISIT               |
| WING  | WING                |
| WORK  | WORKSHOP            |
                          
        
### Residential Housing Type
| NOMIS | DPS                      |
|-------|--------------------------|
| HC    | HEALTHCARE               |           
| HOLC  | HOLDING_CELL             |          
| NA    | NORMAL_ACCOMMODATION     |     
| OU    | OTHER_USE                | 
| REC   | RECEPTION                |
| SEG   | SEGREGATION              | 
| SPLC  | SPECIALIST_CELL          | 

### LocationAttributeType

| NOMIS        | DPS                  |
|--------------|----------------------|
| NON_ASSO_TYP | NON_ASSOCIATIONS     |
| SUP_LVL_TYPE | SECURITY             |          
| HOU_USED_FOR | USED_FOR             | 
| HOU_UNIT_ATT | LOCATION_ATTRIBUTE   | 
| HOU_SANI_FIT | SANITATION_FITTINGS  | 

### LocationUsageType

| NOMIS    | DPS                    |   
|----------|------------------------|
| APP      | APPOINTMENT            |
| MOVEMENT | MOVEMENT               |
| OCCUR    | OCCURRENCE             |
| OIC      | ADJUDICATION_HEARING   |
| OTHER    | OTHER                  |
| PROG     | PROGRAMMES_ACTIVITIES  |
| PROP     | PROPERTY               |
| VISIT    | VISIT                  |

### DeactivatedReason
| NOMIS | DPS                     |
|-------|-------------------------|
| A     | NEW_BUILDING            |
| B     | CELL_RECLAIMS           |
| C     | CHANGE_OF_USE           |
| D     | REFURBISHMENT           |
| E     | CLOSURE                 |
| F     | OTHER                   |    
| G     | LOCAL_WORK              |
| H     | STAFF_SHORTAGE          |
| I     | MOTHBALLED              |
| J     | DAMAGED                 |
| K     | OUT_OF_USE              |
| L     | CELLS_RETURNING_TO_USE  |


### LocationAttributeValue
| NOMIS Type   | Value      | Meaning                                | DPS  |
|--------------|------------|----------------------------------------|------|
| HOU_SANI_FIT | ACB        | Auditable Cell Bell                    | ACB  |
| HOU_SANI_FIT | ABD        | Anti Barricade Door                    | ABD  |
| HOU_SANI_FIT | FIB	       | Fixed Bed                              | FIB  |
| HOU_SANI_FIT | MD	        | Metal Door                             | MD   |
| HOU_SANI_FIT | MOB	       | Movable Bed                            | MOB  |
| HOU_SANI_FIT | PC	        | Privacy Curtain                        | PC   |
| HOU_SANI_FIT | PS	        | Privacy Screen                         | PS   |
| HOU_SANI_FIT | SCB	       | Standard Cell Bell                     | SCB  |
| HOU_SANI_FIT | SETO       | 	Separate Toilet                       | SETO |
| HOU_SANI_FIT | WD	        | Wooden Door                            | XXX  |
| HOU_UNIT_ATT | A	         | Cat A Cell                             | XXX  |
| HOU_UNIT_ATT | DO	        | Double Occupancy                       | XXX  |
| HOU_UNIT_ATT | ELC	       | E List Cell                            | XXX  |
| HOU_UNIT_ATT | GC	        | Gated Cell                             | XXX  |
| HOU_UNIT_ATT | LC	        | Listener Cell                          | XXX  |
| HOU_UNIT_ATT | LF	        | Locate Flat                            | XXX  |
| HOU_UNIT_ATT | MO	        | Multiple Occupancy                     | XXX  |
| HOU_UNIT_ATT | NSMC       | 	Non Smoker Cell                       | XXX  |
| HOU_UNIT_ATT | OC	        | Observation Cell                       | XXX  |
| HOU_UNIT_ATT | SC	        | Safe Cell                              | XXX  |
| HOU_UNIT_ATT | SO	        | Single Occupancy                       | XXX  |
| HOU_UNIT_ATT | SPC	       | Special Cell                           | XXX  |
| HOU_UNIT_ATT | WA	        | Wheelchair Access                      | XXX  |
| HOU_USED_FOR | 1	         | Unconvicted Juveniles                  | XXX  |
| HOU_USED_FOR | 	10	       | Healthcare Centre                      | XXX  |
| HOU_USED_FOR | 	11	       | National Resource Hospital             | XXX  |
| HOU_USED_FOR | 	12	       | Other (Please Specify)                 | XXX  |
| HOU_USED_FOR | 	2	        | Sentenced Juveniles                    | XXX  |
| HOU_USED_FOR | 	3	        | Unconvicted 18 to 20 year olds         | XXX  |
| HOU_USED_FOR | 	4	        | Sentenced 18 to 20 year olds           | XXX  |
| HOU_USED_FOR | 	5	        | Unconvicted Adults                     | XXX  |
| HOU_USED_FOR | 	6	        | Sentenced Adults                       | XXX  |
| HOU_USED_FOR | 	7	        | Vulnerable Prisoner Unit               | XXX  |
| HOU_USED_FOR | 	8	        | Special Unit                           | XXX  |
| HOU_USED_FOR | 	9	        | Resettlement Hostel                    | XXX  |
| HOU_USED_FOR | 	A	        | Remand Centre                          | XXX  |
| HOU_USED_FOR | 	B	        | Local Prison                           | XXX  |
| HOU_USED_FOR | 	C	        | Closed Prison                          | XXX  |
| HOU_USED_FOR | 	D	        | Open Training                          | XXX  |
| HOU_USED_FOR | 	E	        | Hostel                                 | XXX  |
| HOU_USED_FOR | 	H	        | National Resource Hospital             | XXX  |
| HOU_USED_FOR | 	I	        | Closed Young Offender Institute        | XXX  |
| HOU_USED_FOR | 	J	        | Open Young Offender Institute          | XXX  |
| HOU_USED_FOR | 	K	        | Remand Institute for Under 18s         | XXX  |
| HOU_USED_FOR | 	L	        | Institution for Sentenced Under 18s    | XXX  |
| HOU_USED_FOR | 	R	        | ECL Component Code                     | XXX  |
| HOU_USED_FOR | 	S	        | Special Unit                           | XXX  |
| HOU_USED_FOR | 	T	        | Additional Special Unit                | XXX  |
| HOU_USED_FOR | 	V	        | Vulnerable Prisoner Unit               | XXX  |
| HOU_USED_FOR | 	Y	        | Second Closed Trainer/Single LIDS Site | XXX  |
| HOU_USED_FOR | 	Z	        | Immigration Detainees                  | XXX  |
| NON_ASSO_TYP | 	CELL      | 	Do Not Locate in Same Cell            | XXX  |
| NON_ASSO_TYP | 	LAND      | 	Do Not Locate on Same Landing         | XXX  |
| NON_ASSO_TYP | 	NONE X    | 	Do Not Exercise Together              | XXX  |
| NON_ASSO_TYP | 	TNA	      | Total Non Association                  | XXX  |
| NON_ASSO_TYP | 	WING      | 	Do Not Locate on Same Wing            | XXX  |
| SUP_LVL_TYPE | 	A	        | Cat A                                  | XXX  |
| SUP_LVL_TYPE | 	B	        | Cat B                                  | XXX  |
| SUP_LVL_TYPE | 	C	        | Cat C                                  | XXX  |
| SUP_LVL_TYPE | 	D	        | Cat D                                  | XXX  |
| SUP_LVL_TYPE | 	E	        | Cat A Ex                               | XXX  |
| SUP_LVL_TYPE | 	EL	       | Eligible                               | XXX  |
| SUP_LVL_TYPE | 	GRANTED   | 	Parole Granted                        | XXX  |
| SUP_LVL_TYPE | 	H	        | Cat A Hi                               | XXX  |
| SUP_LVL_TYPE | 	HI	       | High                                   | XXX  |
| SUP_LVL_TYPE | 	I	        | YOI Closed                             | XXX  |
| SUP_LVL_TYPE | 	INEL      | 	Ineligible                            | XXX  |
| SUP_LVL_TYPE | 	J	        | YOI Open                               | XXX  |
| SUP_LVL_TYPE | 	K	        | YOI Short Sentence                     | XXX  |
| SUP_LVL_TYPE | 	L	        | YOI Long-Term Closed                   | XXX  |
| SUP_LVL_TYPE | 	LOW	      | Low                                    | XXX  |
| SUP_LVL_TYPE | 	MED	      | Medium                                 | XXX  |
| SUP_LVL_TYPE | 	N	        | No                                     | XXX  |
| SUP_LVL_TYPE | 	N/A	      | Not Applicable                         | XXX  |
| SUP_LVL_TYPE | 	NA	       | Not Applicable                         | XXX  |
| SUP_LVL_TYPE | 	P	        | Prov A                                 | XXX  |
| SUP_LVL_TYPE | 	PEND      | 	Pending                               | XXX  |
| SUP_LVL_TYPE | 	Q	        | Female Restricted                      | XXX  |
| SUP_LVL_TYPE | 	R	        | Female Closed                          | XXX  |
| SUP_LVL_TYPE | REF/REVIEW | Ref / Review - Date set for Review     | XXX  |
| SUP_LVL_TYPE | 	REFUSED   | Refused - No Review possible           | XXX  |
| SUP_LVL_TYPE | 	S	        | Female Semi                            | XXX  |
| SUP_LVL_TYPE | 	STANDARD  | 	Standard                              | XXX  |
| SUP_LVL_TYPE | 	T	        | Female Open                            | XXX  |
| SUP_LVL_TYPE | 	U	        | Unsentenced                            | XXX  |
| SUP_LVL_TYPE | 	V	        | YOI Restricted                         | XXX  |
| SUP_LVL_TYPE | 	X	        | Uncategorised Sentenced Male           | XXX  |
| SUP_LVL_TYPE | 	Y	        | Yes                                    | XXX  |
| SUP_LVL_TYPE | 	Z	        | Unclass                                | XXX  |

### Entity Diagram

```mermaid
---
title: Locations Inside Prison Class Diagram
---

classDiagram
direction BT
class Capacity {
  + toDto() Capacity
   Int operationalCapacity
   Long? id
   Int capacity
}
class Certification {
  + toDto() Certification
   Int capacityOfCertifiedCell
   Long? id
   Boolean certified
}
class Companion {
   Logger log
}
class DeactivatedReason {
<<enumeration>>
  + valueOf(String) DeactivatedReason
  + values() DeactivatedReason[]
   String description
   EnumEntries~DeactivatedReason~ entries
}
class Location {
  + toString() String
  + findTopLevelLocation() Location
  + addChildLocation(Location) Location
  + reactivate(String, Clock) Unit
  - removeChildLocation(Location) Location
  + hashCode() Int
  - removeParent() Unit
  + deactivate(DeactivatedReason, String, Clock) Unit
  + findAllLeafLocations() List~Location~
  + equals(Object?) Boolean
  - updateHierarchicalPath() Unit
  + toDto(Boolean) Location
  + updateWith(PatchLocationRequest, String, Clock) Location
   String? description
   String pathHierarchy
   String prisonId
   String updatedBy
   String? comments
   LocalDate? deactivatedDate
   LocationType locationType
   String code
   LocalDateTime whenCreated
   Boolean active
   DeactivatedReason? deactivatedReason
   UUID? id
   List~Location~ childLocations
   Location? parent
   Boolean cell
   String hierarchicalPath
   LocalDateTime whenUpdated
   Boolean wingLandingSpur
   Int? orderWithinParentLocation
   String key
   LocalDate? reactivatedDate
}
class LocationType {
<<enumeration>>
  + valueOf(String) LocationType
  + values() LocationType[]
   String description
   EnumEntries~LocationType~ entries
}
class NonResidentialLocation {
  + addUsage(NonResidentialUsageType, Int?, Int) Unit
  + toDto(Boolean) Location
  + updateWith(PatchLocationRequest, String, Clock) NonResidentialLocation
}
class NonResidentialUsage {
  + toDto() NonResidentialUsageDto
  + hashCode() Int
  + equals(Object?) Boolean
   NonResidentialUsageType usageType
   Long? id
   Int sequence
   Location location
   Int? capacity
}
class NonResidentialUsageType {
<<enumeration>>
  + valueOf(String) NonResidentialUsageType
  + values() NonResidentialUsageType[]
   String description
   EnumEntries~NonResidentialUsageType~ entries
}
class ResidentialAttribute {
  + equals(Object?) Boolean
  + hashCode() Int
   ResidentialAttributeType attributeType
   Long? id
   ResidentialAttributeValue attributeValue
   Location location
}
class ResidentialAttributeType {
<<enumeration>>
  + valueOf(String) ResidentialAttributeType
  + values() ResidentialAttributeType[]
   String description
   EnumEntries~ResidentialAttributeType~ entries
}
class ResidentialAttributeValue {
<<enumeration>>
  + values() ResidentialAttributeValue[]
  + valueOf(String) ResidentialAttributeValue
   String description
   ResidentialAttributeType type
   EnumEntries~ResidentialAttributeValue~ entries
}
class ResidentialHousingType {
<<enumeration>>
  + values() ResidentialHousingType[]
  + valueOf(String) ResidentialHousingType
   String description
   EnumEntries~ResidentialHousingType~ entries
}
class ResidentialLocation {
  + updateWith(PatchLocationRequest, String, Clock) ResidentialLocation
  + addAttribute(ResidentialAttributeValue) Unit
  + toDto(Boolean) Location
  - cellLocations() List~ResidentialLocation~
   ResidentialHousingType residentialHousingType
   Certification? certification
   Int baselineCapacity
   Int operationalCapacity
   Capacity? capacity
   Int maxCapacity
}

Location  -->  Companion 
Location "1" *--> "deactivatedReason 1" DeactivatedReason 
Location "1" *--> "locationType 1" LocationType 
NonResidentialLocation  -->  Location 
NonResidentialLocation  ..>  Location : «create»
NonResidentialLocation "1" *--> "nonResidentialUsages *" NonResidentialUsage 
NonResidentialLocation  ..>  NonResidentialUsage : «create»
NonResidentialUsage "1" *--> "location 1" Location 
NonResidentialUsage "1" *--> "usageType 1" NonResidentialUsageType 
ResidentialAttribute "1" *--> "location 1" Location 
ResidentialAttribute "1" *--> "attributeType 1" ResidentialAttributeType 
ResidentialAttribute "1" *--> "attributeValue 1" ResidentialAttributeValue 
ResidentialAttributeValue "1" *--> "type 1" ResidentialAttributeType 
ResidentialLocation "1" *--> "capacity 1" Capacity 
ResidentialLocation "1" *--> "certification 1" Certification 
ResidentialLocation  -->  Location 
ResidentialLocation  ..>  Location : «create»
ResidentialLocation "1" *--> "attributes *" ResidentialAttribute 
ResidentialLocation  ..>  ResidentialAttribute : «create»
ResidentialLocation "1" *--> "residentialHousingType 1" ResidentialHousingType 

```



[Next >>](9999-end.md)