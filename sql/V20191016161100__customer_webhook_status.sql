alter table customer add column webhook_active boolean not null default true;

create index customer_webhook_url_key_active_idx on customer (webhook_url, webhook_key, webhook_active);

alter table txn_delivery_log add column failure_reason text;