-- Widen the columns an archive reason passes through, so an archive can always be approved (MAPA-312).
--
-- The reason a location is being archived is free text typed by the person raising the request. It is
-- stored on certification_approval_request.reason_for_change, which V1_67 made TEXT, and then copied
-- onto the location when the request is approved. Nothing between the two bounds the length: the field
-- is an unbounded textarea in the UI and PermanentDeactivationApprovalRequestDto.reason has no size
-- limit. So a request with a long reason saved happily and only failed at the point of approval, when
-- ApprovalDecisionService.handlePermanentDeactivation wrote it to the location. Postgres rejected the
-- update, the API returned 500 and the authorising director saw a generic error page, with no way to
-- tell what was wrong or to edit the reason. This happened 14 times in production between August and
-- September 2026 and left two archive requests stuck - NWI-E and WTI-B-2-625.
--
-- Approval writes the reason to two places, so both have to be widened or the same failure simply
-- returns at a higher threshold:
--
--   location.archived_reason         varchar(200)  from V1_19
--   location_history.new_value       varchar(1024) from V1_37, written by Location.addHistory
--
-- location_history.old_value takes the same treatment: Location.unarchive records the reason there
-- when it clears the archive, so a restore has the identical exposure. The other free-text attributes
-- that flow through location_history are bounded well below 1024 by their own columns (local_name is
-- varchar(80), comments varchar(255)), so in practice this only changes what the archive reason can
-- record - but history should be able to hold whatever the column it mirrors can hold.
--
-- TEXT rather than a larger varchar, to match reason_for_change and so these cannot drift apart again.
-- Widening is not a rewrite in Postgres: varchar(n) and text share a representation, so this is a
-- catalogue change and takes only a brief ACCESS EXCLUSIVE lock regardless of table size.
--
-- Column comments survive ALTER COLUMN, but are restated so the data dictionary stays greppable from
-- the migration that last touched each column.

ALTER TABLE location ALTER COLUMN archived_reason TYPE TEXT;

ALTER TABLE location_history ALTER COLUMN old_value TYPE TEXT;
ALTER TABLE location_history ALTER COLUMN new_value TYPE TEXT;

COMMENT ON COLUMN location.archived_reason IS 'Why the location was permanently archived rather than merely deactivated. Free text: assume it can name a prisoner. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN location_history.old_value IS 'The attribute''s value before the change, rendered as text. Carries the sensitivity of whichever attribute changed, so where the attribute was a free-text field this can name a prisoner. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN location_history.new_value IS 'The attribute''s value after the change, rendered as text. Same caveat as old_value. [Sensitivity: PERSONAL]';
