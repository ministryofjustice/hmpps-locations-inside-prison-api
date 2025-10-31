create table service_usage
(
    id           serial
        constraint non_residential_service_pk
            primary key,
    location_id  uuid        not null
        references location on delete cascade,
    service_type varchar(80) not null
);

create unique index service_usage_location_uk
    on service_usage (location_id, service_type);

insert into service_usage (location_id, service_type)
select l.id,
       case
           when nru.usage_type = 'APPOINTMENT' then 'APPOINTMENT'
           when nru.usage_type = 'PROGRAMMES_ACTIVITIES' then 'PROGRAMMES_AND_ACTIVITIES'
           when nru.usage_type = 'ADJUDICATION_HEARING' then 'HEARING_LOCATION'
           when nru.usage_type = 'OCCURRENCE' then 'LOCATION_OF_INCIDENT'
           when nru.usage_type = 'MOVEMENT' then 'INTERNAL_MOVEMENTS'
           when nru.usage_type = 'VISIT' then 'OFFICIAL_VISITS'
           end as service_type
FROM location l join non_residential_usage nru on l.id = nru.location_id
where location_type_discriminator = 'NON_RESIDENTIAL' and l.code != 'RTU'
  and nru.usage_type in ('APPOINTMENT', 'PROGRAMMES_ACTIVITIES', 'ADJUDICATION_HEARING', 'OCCURRENCE', 'MOVEMENT', 'VISIT')
union
select l.id, 'USE_OF_FORCE' as service_type
FROM location l join non_residential_usage nru on l.id = nru.location_id
where location_type_discriminator = 'NON_RESIDENTIAL'
  and nru.usage_type = 'OCCURRENCE' and l.code != 'RTU'
union
select l.id, 'INTERNAL_MOVEMENTS' as service_type
FROM location l
where location_type_discriminator = 'NON_RESIDENTIAL' and l.internal_movement_allowed = true
  and l.code != 'RTU'