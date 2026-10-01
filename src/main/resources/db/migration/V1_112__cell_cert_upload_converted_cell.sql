-- MAPA-413: a converted cell (an office, store, shower ...) holds no capacity, so an import changes none and the
-- certificate records 0 for it, whatever the file says. Record the converted type on the row so the report can show
-- the cell's capacity and its certificate change correctly.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN converted_cell_type VARCHAR(255) NULL;

COMMENT ON COLUMN cell_certificate_upload_location.converted_cell_type IS 'Set when the cell is converted to another use, such as an office; it holds no capacity, so the import changed none and the certificate records 0. [Sensitivity: NONE]';
