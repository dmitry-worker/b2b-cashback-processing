ALTER TABLE cashback_transaction
ADD COLUMN when_received TIMESTAMP NOT NULL DEFAULT 'now';
