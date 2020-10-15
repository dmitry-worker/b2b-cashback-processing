update cashback_transaction set parent_txn = null where true;
alter table cashback_transaction alter column parent_txn type uuid using (parent_txn::text)::uuid;