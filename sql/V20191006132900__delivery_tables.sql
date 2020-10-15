CREATE TABLE public.txn_delivery_log (
    txn_id uuid NOT NULL,
    batch_id uuid NOT NULL,
    customer_name text NOT NULL,
    when_delivered timestamp NOT NULL,
    diff jsonb
);

ALTER TABLE ONLY public.txn_delivery_log
    ADD CONSTRAINT txn_delivery_log_pk PRIMARY KEY (txn_id, batch_id);


CREATE TABLE public.txn_change_log (
    txn_id uuid NOT NULL,
    when_created timestamp NOT NULL,
    customer_name text NOT NULL,
    diff jsonb NOT NULL
);

ALTER TABLE ONLY public.txn_change_log
    ADD CONSTRAINT txn_change_log_pk PRIMARY KEY (txn_id, when_created);
