CREATE TABLE capacity
(
    id                   uuid not null
        constraint capacity_pk primary key DEFAULT gen_random_uuid(),
    capacity             int  not null     default 0,
    operational_capacity int  not null     default 0,
    current_occupancy    int  not null     default 0
);

CREATE TABLE certification
(
    id                         uuid    not null
        constraint certification_pk primary key DEFAULT gen_random_uuid(),
    certified                  boolean not null default false,
    capacity_of_certified_cell int     not null default 0
);

DROP TABLE location;

CREATE TABLE location
(
    id                           uuid         not null
        constraint location_pk primary key             DEFAULT gen_random_uuid(),

    prison_id                    varchar(3)   NOT NULL,
    path_hierarchy               varchar(150) NOT NULL,
    code                         varchar(40)  NOT NULL,
    location_type                varchar(30)  NOT NULL,
    parent_id                    uuid         NULL,

    description                  varchar(80)  NULL,
    comments                     varchar(255) NULL,
    order_within_parent_location int          NULL,

    residential_housing_type     varchar(30)  NULL,
    certification_id             uuid         NULL,
    capacity_id                  uuid         NULL,

    active                       boolean      NOT NULL DEFAULT true,
    deactivated_date             date         NULL,
    deactivated_reason           varchar(30)  NULL,
    reactivated_date             date         NULL,

    when_created                 timestamp    NOT NULL,
    when_updated                 timestamp    NOT NULL,
    updated_by                   varchar(80)  NOT NULL,

    constraint fk_location_parent_id foreign key (parent_id) references location (id),
    constraint uk_location_prison_code_id unique (prison_id, path_hierarchy),
    constraint fk_location_capacity_id foreign key (capacity_id) references capacity (id),
    constraint fk_location_certification_id foreign key (certification_id) references certification (id)
);

CREATE UNIQUE INDEX idx_location_prison_id_path_hierarchy ON location (prison_id, path_hierarchy);

CREATE INDEX idx_location_path_hierarchy ON location (path_hierarchy);

CREATE INDEX idx_location_parent_id ON location (parent_id);

CREATE INDEX idx_location_capacity_id ON location (capacity_id);

CREATE INDEX idx_location_certification_id ON location (certification_id);