-- This is a COPY of the TransactionType enumeration for use in reporting; Analytical Platform & DPR.
--
-- The application continues to use the values solely from the internal enum
-- `uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType`.
--
-- NB:
--   - any additions, changes to order, codes or descriptions REQUIRE a new migration!
--   - this table should NOT be used in foreign key constraints, otherwise migrations would be overly-complicated
--   - kept in sync with the enum by ConstantsTableTest

create table constant_transaction_type
(
    code        varchar(60) primary key,
    sequence    integer     not null,
    description varchar(60) not null
);

insert into constant_transaction_type(sequence, code, description)
values (0, 'LOCATION_CREATE', 'Created residential location'),
       (1, 'LOCATION_CREATE_NON_RESI', 'Created non-residential location'),
       (2, 'LOCATION_UPDATE', 'Updated residential location'),
       (3, 'LOCATION_UPDATE_NON_RESI', 'Updated non-residential location'),
       (4, 'SYNC', 'NOMIS sync (Residential)'),
       (5, 'SYNC_NON_RESIDENTIAL', 'NOMIS sync (Non-Residential)'),
       (6, 'DELETE', 'Locations deleted'),
       (7, 'CAPACITY_CHANGE', 'Capacity change'),
       (8, 'CELL_TYPE_CHANGES', 'Cell type change'),
       (9, 'DEACTIVATION', 'Deactivation'),
       (10, 'PERMANENT_DEACTIVATION', 'Permanent deactivation'),
       (11, 'REACTIVATION', 'Reactivation'),
       (12, 'CELL_CONVERTION_TO_ROOM', 'Cell conversion to room'),
       (13, 'ROOM_CONVERTION_TO_CELL', 'Room conversion to cell'),
       (14, 'SIGNED_OP_CAP', 'Signed operational capacity'),
       (15, 'RESI_SERVICE_ACTIVATION', 'Residential service activation'),
       (16, 'NON_RESI_SERVICE_ACTIVATION', 'Non-residential service activation'),
       (17, 'INCLUDE_SEG_IN_ROLL_COUNT_ACTIVATION', 'Include segregation in roll count activation'),
       (18, 'APPROVAL_PROCESS_ACTIVATION', 'Certification approval activation'),
       (19, 'REQUEST_CERTIFICATION_APPROVAL', 'Request certification approval'),
       (20, 'APPROVE_CERTIFICATION_REQUEST', 'Approve request'),
       (21, 'REJECT_CERTIFICATION_REQUEST', 'Reject request'),
       (22, 'WITHDRAW_CERTIFICATION_REQUEST', 'Withdraw request'),
       (23, 'CERTIFICATE_BASELINE', 'Certificate baseline');
