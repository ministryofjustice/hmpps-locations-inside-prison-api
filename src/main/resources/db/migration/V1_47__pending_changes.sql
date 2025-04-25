ALTER table location
    add column pending_change_id bigint null;

CREATE TABLE pending_location_change
(
    id                         SERIAL  not null
        constraint pending_location_change_pk primary key,
    capacity_id                  bigint       NULL
);

alter table pending_location_change
    add constraint fk_pending_location_change_capacity_id
        foreign key (capacity_id) references capacity
            on delete cascade;

create index location_pending_location_change_id_idx
    on location (pending_change_id);

alter table location
    add constraint fk_pending_location_change
        foreign key (pending_change_id) references pending_location_change
            on delete cascade;

