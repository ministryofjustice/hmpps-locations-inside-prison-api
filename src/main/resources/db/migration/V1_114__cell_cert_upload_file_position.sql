-- MAPA-457: record where each row sat in the uploaded file, so the report can list the cells in the same order as the
-- prison's certificate rather than by location key. Null for uploads made before this change, which keep the
-- location key order.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN file_position INTEGER NULL;

COMMENT ON COLUMN cell_certificate_upload_location.file_position IS 'Position of the row in the uploaded file, starting at 1. Null for uploads made before MAPA-457. [Sensitivity: NONE]';
