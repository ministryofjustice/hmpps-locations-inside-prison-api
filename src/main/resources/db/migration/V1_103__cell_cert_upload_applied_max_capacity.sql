-- MAPA-336: the certificate records the max capacity the prison uploaded, which may be zero. A live
-- location cannot hold a max capacity of zero, so ingestion floors the value it writes onto the
-- location - record what the location actually took so the import report can show the real change.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN applied_max_capacity INT;
