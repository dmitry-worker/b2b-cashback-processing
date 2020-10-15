create index txn_delivery_queue_attempt_search_idx on txn_delivery_queue  (when_next_attempt,customer_name,attempt_count);

create index txn_delivery_queue_batch_id_idx on txn_delivery_queue  (batch_id);

create index txn_delivery_log_txn_id_idx on txn_delivery_log  (txn_id);

create index txn_change_log_when_created_txn_id_idx on txn_change_log  (when_created, txn_id);