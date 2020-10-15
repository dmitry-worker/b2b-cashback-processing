alter table merchants add column search_index_gin tsvector;
alter table merchant_offers add column search_index_gin tsvector;
alter table cashback_transaction add column search_index_gin tsvector;

update merchants set search_index_gin = to_tsvector('english', search_index) where true;
update merchant_offers set search_index_gin = to_tsvector('english', search_index) where true;
update cashback_transaction set search_index_gin = to_tsvector('english', merchant_name) where true;

create index merchants_search_index_gin_ft on merchants using gin(search_index_gin);
create index merchant_offers_search_index_gin_ft on merchant_offers using gin(search_index_gin);
create index cashback_transaction_index_gin_ft on cashback_transaction using gin(search_index_gin);

alter table merchants drop column search_index;
alter table merchant_offers drop column search_index;
