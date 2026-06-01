-- Columns for ConvertToCellApprovalRequest (convert a non-residential room back to a cell).
-- The pending capacity (working/max/CNA) and specialist cell type columns already exist and are reused.
ALTER TABLE certification_approval_request ADD COLUMN accommodation_type VARCHAR(60);
ALTER TABLE certification_approval_request ADD COLUMN used_for_types VARCHAR(255);
