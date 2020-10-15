
create table merchant_offers_diff (
                                    offer_id text not null,
                                    timestamp timestamp not null,
                                    diff jsonb,
                                    constraint merchant_offers_diff_pk primary key (offer_id, timestamp)
);

create index merchant_offers_diff_timestamp_idx on merchant_offers_diff(timestamp);


-- alter table cashback_transaction add column user_tracking_hash text;
alter table cashback_transaction add column offer_id text;
alter table cashback_transaction add column offer_timestamp timestamp;

