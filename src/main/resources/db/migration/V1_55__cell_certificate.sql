CREATE TABLE cell_certificate
(
    id                                UUID PRIMARY KEY,
    prison_id                         VARCHAR(5)   NOT NULL,
    approved_by                       VARCHAR(100) NOT NULL,
    approved_date                     TIMESTAMP    NOT NULL,
    certification_approval_request_id UUID         NOT NULL,
    total_working_capacity            INT          NOT NULL,
    total_max_capacity                INT          NOT NULL,
    total_capacity_of_certified_cell  INT          NOT NULL,
    current                           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cell_certificate_certification_approval_request FOREIGN KEY (certification_approval_request_id) REFERENCES certification_approval_request (id) on delete cascade
);

-- Create index on foreign key
CREATE INDEX cell_certificate_certification_approval_request_idx ON cell_certificate (certification_approval_request_id);

-- Create index on prison_id for efficient querying
CREATE INDEX cell_certificate_prison_id_idx ON cell_certificate (prison_id);

-- Create index on current flag for efficient querying of current certificates
CREATE INDEX cell_certificate_prison_id_current_idx ON cell_certificate (prison_id, current) WHERE current = TRUE;

-- Add unique constraint to certification_approval_request_id in cell_certificate table
-- This ensures a 0:1 relationship between CertificationApprovalRequest and CellCertificate
ALTER TABLE cell_certificate ADD CONSTRAINT uk_cell_certificate_certification_approval_request_id UNIQUE (certification_approval_request_id);