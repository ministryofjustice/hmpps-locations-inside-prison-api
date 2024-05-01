
/**
    CAT_A_CELL  ->  SpecialistCellType.CAT_A
    E_LIST_CELL-->  SpecialistCellType.ESCAPE_LIST
    GATED_CELL --> SpecialistCellType.CONSTANT_SUPERVISION
    LISTENER_CELL --> SpecialistCellType.LISTENER_CRISIS
    LOCATE_FLAT --> SpecialistCellType.LOW_MOBILITY
    OBSERVATION_CELL --> SpecialistCellType.CONSTANT_SUPERVISION
    WHEELCHAIR_ACCESS -->  SpecialistCellType.WHEELCHAIR_ACCESSIBLE
 */
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'CAT_A' from residential_attribute WHERE attribute_value = 'CAT_A_CELL';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'ESCAPE_LIST' from residential_attribute WHERE attribute_value = 'E_LIST_CELL';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'CONSTANT_SUPERVISION' from residential_attribute WHERE attribute_value = 'GATED_CELL';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'LISTENER_CRISIS' from residential_attribute WHERE attribute_value = 'LISTENER_CELL';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'LOW_MOBILITY' from residential_attribute WHERE attribute_value = 'LOCATE_FLAT';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'CONSTANT_SUPERVISION' from residential_attribute WHERE attribute_value = 'OBSERVATION_CELL';
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'WHEELCHAIR_ACCESSIBLE' from residential_attribute WHERE attribute_value = 'WHEELCHAIR_ACCESS';

UPDATE location
SET accommodation_type =
        CASE
            WHEN residential_housing_type = 'NORMAL_ACCOMMODATION'  THEN 'NORMAL_ACCOMMODATION'
            WHEN residential_housing_type = 'HEALTHCARE'  THEN 'HEALTHCARE_INPATIENTS'
            WHEN residential_housing_type = 'SEGREGATION'  THEN 'CARE_AND_SEPARATION'
      ELSE 'OTHER_NON_RESIDENTIAL'
END
WHERE residential_housing_type is not null;

UPDATE location
SET deactivated_reason =
        CASE
            WHEN deactivated_reason = 'REFURBISHMENT'  THEN 'REFURBISHMENT'
            WHEN deactivated_reason = 'LOCAL_WORK'  THEN 'MAINTENANCE'
            WHEN deactivated_reason = 'STAFF_SHORTAGE'  THEN 'STAFF_SHORTAGE'
            WHEN deactivated_reason = 'MOTHBALLED'  THEN 'MOTHBALLED'
            WHEN deactivated_reason = 'DAMAGED'  THEN 'DAMAGED'
            ELSE 'OTHER'
        END
WHERE deactivated_reason is not null;

UPDATE location
SET converted_cell_type = 'HOLDING_ROOM',
    accommodation_type = 'OTHER_NON_RESIDENTIAL',
    capacity_id = null,
    certification_id = null
WHERE residential_housing_type = 'HOLDING_CELL';