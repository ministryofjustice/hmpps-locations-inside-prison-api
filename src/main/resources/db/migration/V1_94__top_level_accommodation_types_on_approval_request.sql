-- Resulting (post-change) top-level (wing) accommodation types / used-for, captured on a certification approval
-- request when the change alters the set of accommodation types held by the wing above the location being approved.
ALTER TABLE certification_approval_request ADD COLUMN top_level_accommodation_types VARCHAR(255);
ALTER TABLE certification_approval_request ADD COLUMN top_level_used_for_types VARCHAR(255);
