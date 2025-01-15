CREATE TABLE linked_transaction
(
    id                     UUID NOT NULL constraint linked_transaction_pk primary key,
    transaction_type       VARCHAR(60) NOT NULL,
    transaction_detail     text NOT NULL,
    transaction_invoked_by VARCHAR(80) NOT NULL,
    tx_start_time          TIMESTAMP NOT NULL,
    tx_end_time            TIMESTAMP
);

CREATE INDEX idx_linked_transaction_tx_start_time ON linked_transaction (tx_start_time);

ALTER TABLE location_history ADD COLUMN linked_transaction_id UUID;

ALTER TABLE location_history ADD CONSTRAINT linked_transaction_fk FOREIGN KEY (linked_transaction_id) REFERENCES linked_transaction (id) on delete cascade;

CREATE INDEX idx_location_history_linked_transaction_id ON location_history (linked_transaction_id);