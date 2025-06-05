CREATE TABLE certification_approval_request_location (
  id UUID PRIMARY KEY,
  certification_approval_request_id UUID NOT NULL,
  location_code VARCHAR(40) NOT NULL,
  cell_mark VARCHAR(255),
  local_name VARCHAR(255),
  path_hierarchy VARCHAR(255) NOT NULL,
  level INT NOT NULL,
  status VARCHAR(40) NOT NULL,
  capacity_of_certified_cell INT,
  working_capacity INT,
  max_capacity INT,
  in_cell_sanitation BOOLEAN,
  location_type VARCHAR(40) NOT NULL,
  specialist_cell_types VARCHAR(255),
  converted_cell_type VARCHAR(60),
  parent_location_id UUID,
  FOREIGN KEY (certification_approval_request_id) REFERENCES certification_approval_request(id),
  FOREIGN KEY (parent_location_id) REFERENCES certification_approval_request_location(id)
);

CREATE INDEX idx_cert_approval_req_loc_req_id ON certification_approval_request_location(certification_approval_request_id);
CREATE INDEX idx_cert_approval_req_loc_parent_id ON certification_approval_request_location(parent_location_id);