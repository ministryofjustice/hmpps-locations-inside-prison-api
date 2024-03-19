ALTER TABLE capacity RENAME COLUMN capacity TO max_capacity;
ALTER TABLE capacity RENAME COLUMN operational_capacity TO working_capacity;

ALTER TABLE location RENAME COLUMN description TO local_name;
