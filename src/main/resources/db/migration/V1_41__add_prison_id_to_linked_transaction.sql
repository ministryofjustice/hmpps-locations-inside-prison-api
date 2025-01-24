ALTER TABLE linked_transaction ADD COLUMN prison_id VARCHAR(5);

UPDATE linked_transaction t
SET prison_id = (select distinct (prison_id) from location_history lh join location l on lh.location_id = l.id
where lh.linked_transaction_id = t.id)
WHERE EXISTS (select 1 from location_history lh join location l on lh.location_id = l.id
where lh.linked_transaction_id = t.id);

DELETE FROM linked_transaction WHERE transaction_type = 'SIGNED_OP_CAP';

ALTER TABLE linked_transaction ALTER COLUMN prison_id SET NOT NULL;

CREATE INDEX idx_linked_transaction_prison_id ON linked_transaction (prison_id, transaction_type);