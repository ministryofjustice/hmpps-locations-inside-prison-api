CREATE TABLE certification_approval_request
(
    id                        UUID PRIMARY KEY,
    location_id               UUID         NOT NULL,
    location_key              VARCHAR(255) NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    requested_by              VARCHAR(80)  NOT NULL,
    requested_date            TIMESTAMP    NOT NULL,
    approved_Or_Rejected_By   VARCHAR(80),
    approved_Or_Rejected_Date TIMESTAMP,
    comments                  TEXT,
    CONSTRAINT fk_location FOREIGN KEY (location_id) REFERENCES location (id)
);

create index certification_approval_request_location_id_idx on certification_approval_request (location_id);