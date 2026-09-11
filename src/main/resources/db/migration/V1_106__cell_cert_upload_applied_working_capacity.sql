-- MAPA-387: since MAPA-362 an import raises a working capacity of zero to the certified value, so the
-- location's working capacity can now change. Record what the location actually took, as already done
-- for max capacity, so the import report can show the change rather than the old value.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN applied_working_capacity INT;

COMMENT ON COLUMN cell_certificate_upload_location.applied_working_capacity IS 'The working capacity actually applied to the location, which can differ from the uploaded value where the location kept the one it already held. [Sensitivity: OFFICIAL-SENSITIVE]';
