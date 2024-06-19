delete from cell_used_for
where id IN (select cuf.id FROM location l join cell_used_for cuf on cuf.location_id = l.id where l.accommodation_type != 'NORMAL_ACCOMMODATION');