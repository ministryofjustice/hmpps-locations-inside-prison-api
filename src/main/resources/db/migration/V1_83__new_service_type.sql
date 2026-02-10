insert into service_usage (location_id, service_type)
select l.id, 'VIDEO_LINK' as service_type
FROM location l
where location_type_discriminator = 'NON_RESIDENTIAL' and location_type = 'VIDEO_LINK'
and l.status = 'ACTIVE';