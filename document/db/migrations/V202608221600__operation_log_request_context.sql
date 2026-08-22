ALTER TABLE dms_operation_log
    ADD COLUMN ip_address VARCHAR(64) NULL AFTER remark,
    ADD COLUMN user_agent VARCHAR(500) NULL AFTER ip_address,
    ADD COLUMN request_id VARCHAR(64) NULL AFTER user_agent;

CREATE INDEX idx_operation_log_request_id ON dms_operation_log(request_id);
