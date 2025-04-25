ALTER table location
    add column status varchar(20) not null default 'DRAFT';

update location
    set status = case
    when (archived = true) then 'ARCHIVED'
    when (active = false) then 'INACTIVE'
    else 'ACTIVE'
end where status = 'DRAFT';

CREATE INDEX location_status_idx ON location (prison_id, status);

ALTER table location
    add column locked boolean not null default false;