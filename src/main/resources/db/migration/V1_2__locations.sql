DROP TABLE location;

CREATE TABLE location
(
    id             uuid         not null
        constraint location_pk primary key DEFAULT gen_random_uuid(),

    prison_id      varchar(3)   NOT NULL,
    path_hierarchy varchar(150) NOT NULL,
    code           varchar(40)  NOT NULL,
    location_type  varchar(30)  NOT NULL,
    parent_id      uuid         NULL,
    active         boolean      NOT NULL   DEFAULT true,
    when_created   timestamp    NOT NULL,
    when_updated   timestamp    NOT NULL,
    updated_by     varchar(80)  NOT NULL,
    constraint fk_location_parent_id foreign key (parent_id) references location (id),
    constraint uk_location_prison_code_id unique (prison_id, path_hierarchy)
);

CREATE INDEX idx_location_prison_id ON location (prison_id);

CREATE INDEX idx_location_parent_id ON location (parent_id);

CREATE UNIQUE INDEX idx_location_prison_id_path_hierarchy ON location (prison_id, path_hierarchy);