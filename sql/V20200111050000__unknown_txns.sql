CREATE TABLE unknown_transaction AS TABLE cashback_transaction WITH NO DATA;

ALTER TABLE ONLY public.unknown_transaction
    ADD CONSTRAINT unknown_transaction_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.unknown_transaction
    ADD CONSTRAINT unknown_transaction_ref_unique UNIQUE (reference, merchant_network);
