ALTER TABLE location ADD COLUMN temporarily_off_cell_cert BOOLEAN;

-- Default all cells to FALSE; only inactive cells that match the rules below flip to TRUE.
UPDATE location
SET temporarily_off_cell_cert = FALSE
WHERE location_type_discriminator = 'CELL';

-- Backfill temporarily_off_cell_cert for existing inactive cells.
--
-- An inactive cell is treated as short-term (temporarily off cell cert) when both:
--   1. There is no PENDING certification approval covering the cell. Because
--      LocationCertificationApprovalRequest materialises the full descendant
--      subtree into certification_approval_request_location, a direct match on
--      path_hierarchy + prison_id is sufficient — no recursive walk needed.
--   2. The cell's row in the prison's current cell certificate still has a
--      non-zero working_capacity, i.e. the certificate expects the cell to be
--      online shortly.
WITH inactive_cells AS (
    SELECT id, prison_id, path_hierarchy
    FROM location
    WHERE status = 'INACTIVE'
      AND location_type_discriminator = 'CELL'
),
cells_with_pending_approval AS (
    SELECT DISTINCT ic.id
    FROM inactive_cells ic
             JOIN certification_approval_request_location ccrl
                  ON ccrl.path_hierarchy = ic.path_hierarchy
             JOIN certification_approval_request car
                  ON car.id = ccrl.certification_approval_request_id
                      AND car.prison_id = ic.prison_id
    WHERE car.status = 'PENDING'
),
short_term_inactive_cells AS (
    SELECT ic.id
    FROM inactive_cells ic
             JOIN cell_certificate cc
                  ON cc.prison_id = ic.prison_id
                      AND cc.current = TRUE
             JOIN cell_certificate_location ccl
                  ON ccl.cell_certificate_id = cc.id
                      AND ccl.path_hierarchy = ic.path_hierarchy
    WHERE COALESCE(ccl.working_capacity, 0) <> 0
      AND ic.id NOT IN (SELECT id FROM cells_with_pending_approval)
)
UPDATE location l
SET temporarily_off_cell_cert = TRUE
FROM short_term_inactive_cells s
WHERE l.id = s.id;

-- Enforce: CELL rows must have a value; non-CELL rows must not (single-table inheritance).
ALTER TABLE location
    ADD CONSTRAINT location_temp_off_cell_cert_required_for_cells
        CHECK (location_type_discriminator <> 'CELL' OR temporarily_off_cell_cert IS NOT NULL);
