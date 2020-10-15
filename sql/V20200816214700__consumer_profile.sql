create table consumer_profiles
(
    hash text not null primary key
,   name text
,   age int
,   male bool
,   phone text
,   address text
,   income_class int
,   purchases_count int not null
,   purchases_amount numeric(12,2) not null
,   last_purchase_date timestamp
,   last_purchase_usd_amount numeric(12,2)
);