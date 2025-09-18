alter table certification_approval_request_location
    drop constraint certification_approval_reques_certification_approval_reque_fkey,
    add constraint certification_approval_reques_certification_approval_reque_fkey
        foreign key (certification_approval_request_id)
            references certification_approval_request(id)
            on delete cascade;

alter table certification_approval_request_location
    drop constraint certification_approval_request_location_parent_location_id_fkey,
    add constraint certification_approval_request_location_parent_location_id_fkey
        foreign key (parent_location_id)
            references certification_approval_request_location(id)
            on delete cascade;

alter table certification_approval_request
    drop constraint fk_location,
    add constraint fk_location
        foreign key (location_id)
            references location(id)
            on delete cascade;

alter table cell_certificate_location
    add constraint cell_certificate_location_parent_location_id_fkey
        foreign key (parent_location_id)
            references cell_certificate_location(id)
            on delete cascade;

alter table pending_location_change
    drop constraint pending_location_change_approval_request_id_fkey,
    add constraint pending_location_change_approval_request_id_fkey
        foreign key (approval_request_id)
            references certification_approval_request(id)
            on delete cascade;

