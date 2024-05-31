alter table location
    drop constraint fk_location_capacity_id,
    add constraint fk_location_capacity_id
        foreign key (capacity_id)
            references capacity(id)
            on delete cascade;

alter table location
    drop constraint fk_location_certification_id,
    add constraint fk_location_certification_id
        foreign key (certification_id)
            references certification(id)
            on delete cascade;

alter table cell_used_for
    drop constraint cell_used_for_location_id_fkey,
    add constraint cell_used_for_location_id_fkey
        foreign key (location_id)
            references location(id)
            on delete cascade;

alter table location_history
    drop constraint location_history_location_id_fkey,
    add constraint location_history_location_id_fkey
        foreign key (location_id)
            references location(id)
            on delete cascade;

alter table non_residential_usage
    drop constraint location_usage_location_id_fkey,
    add constraint location_usage_location_id_fkey
        foreign key (location_id)
            references location(id)
            on delete cascade;

alter table residential_attribute
    drop constraint location_attribute_location_id_fkey,
    add constraint location_attribute_location_id_fkey
        foreign key (location_id)
            references location(id)
            on delete cascade;

alter table specialist_cell
    drop constraint specialist_cell_location_id_fkey,
    add constraint specialist_cell_location_id_fkey
        foreign key (location_id)
            references location(id)
            on delete cascade;