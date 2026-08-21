-- Functional mailbox email addresses for a prison, grouped by notification purpose
-- (e.g. certification admin/viewer/reviewer). One row per email address.
CREATE TABLE prison_notification_mailbox
(
    id                  UUID PRIMARY KEY,
    prison_id           VARCHAR(5)   NOT NULL,
    notification_group  VARCHAR(20)  NOT NULL,
    email_address       VARCHAR(255) NOT NULL,
    when_updated        TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(255) NOT NULL
);

-- Case-insensitive uniqueness so the same address can't be added twice with different casing
CREATE UNIQUE INDEX prison_notification_mailbox_unique_idx
    ON prison_notification_mailbox (prison_id, notification_group, lower(email_address));

insert into constant_transaction_type(sequence, code, description)
values (24, 'NOTIFICATION_MAILBOX_UPDATE', 'Notification mailbox updated'),
       (25, 'NOTIFICATION_MAILBOX_DELETE', 'Notification mailbox deleted');
