-- Master / detail tables tracking asynchronous cell certificate uploads.
-- The master holds the overall request and progress; the detail holds each cell's requested values,
-- the previous values, and the outcome once processed (in a later step).

CREATE TABLE cell_certificate_upload
(
    id                              UUID PRIMARY KEY,
    prison_id                       VARCHAR(5)  NOT NULL,
    status                          VARCHAR(20) NOT NULL,
    requested_by                    VARCHAR(255) NOT NULL,
    requested_date                  TIMESTAMP   NOT NULL,
    start_time                      TIMESTAMP   NULL,
    end_time                        TIMESTAMP   NULL,
    total_records                   INT         NOT NULL DEFAULT 0,
    processed_records               INT         NOT NULL DEFAULT 0,
    skipped_records                 INT         NOT NULL DEFAULT 0,
    failed_records                  INT         NOT NULL DEFAULT 0,
    reason_for_change               VARCHAR(255) NULL,
    cell_certificate_id             UUID        NULL
);

CREATE INDEX cell_certificate_upload_prison_id_idx ON cell_certificate_upload (prison_id);

-- Only one active (PENDING/STARTED) upload may exist per prison at a time.
CREATE UNIQUE INDEX cell_certificate_upload_active_prison_idx
    ON cell_certificate_upload (prison_id)
    WHERE status IN ('PENDING', 'STARTED');

CREATE TABLE cell_certificate_upload_location
(
    id                                         UUID PRIMARY KEY,
    cell_certificate_upload_id                 UUID         NOT NULL,
    location_key                               VARCHAR(255) NOT NULL,
    max_capacity                               INT          NOT NULL,
    working_capacity                           INT          NOT NULL,
    certified_normal_accommodation             INT          NULL,
    cell_mark                                  VARCHAR(12)  NULL,
    in_cell_sanitation                         BOOLEAN      NULL,
    status                                     VARCHAR(20)  NOT NULL,
    previous_max_capacity                      INT          NULL,
    previous_working_capacity                  INT          NULL,
    previous_certified_normal_accommodation    INT          NULL,
    previous_cell_mark                         VARCHAR(12)  NULL,
    previous_in_cell_sanitation                BOOLEAN      NULL,
    message                                    VARCHAR(255) NULL,
    processed_date                             TIMESTAMP    NULL,
    CONSTRAINT fk_cell_certificate_upload_location_upload
        FOREIGN KEY (cell_certificate_upload_id) REFERENCES cell_certificate_upload (id) ON DELETE CASCADE
);

CREATE INDEX cell_certificate_upload_location_upload_id_idx
    ON cell_certificate_upload_location (cell_certificate_upload_id);
