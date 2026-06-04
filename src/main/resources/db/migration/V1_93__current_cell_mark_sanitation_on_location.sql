-- Surface the current (pre-change) cell mark and in-cell sanitation per location so CELL_MARK and
-- CELL_SANITATION approval requests can play back "current -> new" at the location level, matching the
-- current_cell_mark / current_in_cell_sanitation already exposed at the top-level request.
ALTER TABLE certification_approval_request_location ADD COLUMN current_cell_mark VARCHAR(255);
ALTER TABLE certification_approval_request_location ADD COLUMN current_in_cell_sanitation BOOLEAN;
