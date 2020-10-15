create table customer_users
(
  customer_name text not null,
  user_id text not null,
  hash text not null,
  constraint customer_users_pk
    primary key (customer_name, user_id)
);

create index customer_users_hash_idx
  on customer_users (hash);

