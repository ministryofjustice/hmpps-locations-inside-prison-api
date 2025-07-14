call setup_prison_demo_locations('LEI', 'ITAG_USER');
call setup_prison_demo_locations('BXI', 'ITAG_USER');
call setup_prison_demo_locations('MDI', 'ITAG_USER');

update prison_configuration set certification_approval_required = true;