-- Widen location.archived_reason so an archive can always be approved (MAPA-312).
--
-- The reason a location is being archived is free text typed by the person raising the request. It is
-- stored twice: on certification_approval_request.reason_for_change, which V1_67 made TEXT, and then
-- on location.archived_reason when the request is approved, which V1_19 made varchar(200) - the only
-- 200 character column in the schema. Nothing between the two bounds the length: the field is an
-- unbounded textarea in the UI and PermanentDeactivationApprovalRequestDto.reason has no size limit.
--
-- So a request with a longer reason saved happily and only failed at the point of approval, when
-- ApprovalDecisionService.handlePermanentDeactivation copied the reason onto the location. Postgres
-- rejected the update, the API returned 500 and the authorising director saw a generic error page,
-- with no way to tell what was wrong or to edit the reason. This happened 14 times in production
-- between August and September 2026 and left two archive requests stuck - NWI-E and WTI-B-2-625.
--
-- TEXT rather than a larger varchar, to match reason_for_change and so the two can never drift apart
-- again. Widening is not a rewrite in Postgres: varchar(n) and text share a representation, so this
-- is a catalogue change and takes only a brief ACCESS EXCLUSIVE lock regardless of table size.
--
-- The column comment survives ALTER COLUMN, but is restated so the data dictionary stays greppable
-- from the migration that last touched the column.

ALTER TABLE location ALTER COLUMN archived_reason TYPE TEXT;

COMMENT ON COLUMN location.archived_reason IS 'Why the location was permanently archived rather than merely deactivated. Free text: assume it can name a prisoner. [Sensitivity: PERSONAL]';
