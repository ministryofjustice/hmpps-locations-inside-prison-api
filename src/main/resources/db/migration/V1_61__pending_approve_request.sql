ALTER TABLE pending_location_change ADD COLUMN approval_request_id UUID;
ALTER TABLE pending_location_change ADD FOREIGN KEY (approval_request_id) REFERENCES certification_approval_request (id);
create index pending_location_change_approval_request_id_idx on pending_location_change (approval_request_id);
