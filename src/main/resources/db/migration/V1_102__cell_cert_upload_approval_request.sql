-- MAPA-323: the cell certificate import request details page needs to find the ingestion that produced it.
-- Nothing recorded the approval request on the upload, and the only link ran the other way, through a
-- loose cell_certificate_id with no index and no finder.

ALTER TABLE cell_certificate_upload
    ADD COLUMN certification_approval_request_id UUID NULL;

-- Backfill uploads that already produced a certificate, so prisons ingested before this change still
-- resolve. Uploads that never reached a certificate stay null and simply have no report to show.
UPDATE cell_certificate_upload u
   SET certification_approval_request_id = c.certification_approval_request_id
  FROM cell_certificate c
 WHERE c.id = u.cell_certificate_id;

CREATE INDEX cell_certificate_upload_approval_request_idx
    ON cell_certificate_upload (certification_approval_request_id);
