-- Current (pre-conversion) values surfaced on a ConvertToCellApprovalRequest so the UI can play
-- back "current -> new". converted_cell_type / other_converted_cell_type already exist and are reused.
ALTER TABLE certification_approval_request ADD COLUMN current_accommodation_types VARCHAR(255);
ALTER TABLE certification_approval_request ADD COLUMN current_used_for_types VARCHAR(255);
