-- Allow notification mailboxes to be configured globally for a notification group.
ALTER TABLE prison_notification_mailbox ALTER COLUMN prison_id DROP NOT NULL;

DROP INDEX prison_notification_mailbox_unique_idx;

CREATE UNIQUE INDEX prison_notification_mailbox_prison_unique_idx
    ON prison_notification_mailbox (prison_id, notification_group, lower(email_address))
    WHERE prison_id IS NOT NULL;

CREATE UNIQUE INDEX prison_notification_mailbox_default_unique_idx
    ON prison_notification_mailbox (notification_group, lower(email_address))
    WHERE prison_id IS NULL;
