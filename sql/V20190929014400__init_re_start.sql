CREATE TABLE public.azigo_txn (
    program_name text NOT NULL,
    user_email text,
    status text NOT NULL,
    unique_record_id text NOT NULL,
    supplied_sub_program_id text,
    transaction_id bigint NOT NULL,
    store_order_id text,
    user_id text,
    supplied_user_id text,
    store_name text,
    tentative_cannot_change_after_date bigint NOT NULL,
    tentative_cannot_change_after_datetime text,
    "timestamp" bigint NOT NULL,
    datetime timestamp NOT NULL,
    post_datetime timestamp NOT NULL,
    sale double precision NOT NULL,
    commission double precision NOT NULL,
    user_commission double precision NOT NULL,
    source_type text NOT NULL,
    reseller_payout_date timestamp,
    logo_url text,
    cashback_failed_reason text,
    common_user_id bigint,
    raw_txn jsonb DEFAULT '{}'::jsonb NOT NULL
);



CREATE TABLE public.cashback_transaction (
    id uuid NOT NULL,
    reference text NOT NULL,
    merchant_name text NOT NULL,
    merchant_network text NOT NULL,
    description text,
    when_created timestamp NOT NULL,
    when_updated timestamp NOT NULL,
    when_claimed timestamp,
    when_settled timestamp,
    when_posted timestamp,
    purchase_date timestamp NOT NULL,
    purchase_amount numeric(12,2) NOT NULL,
    purchase_currency text NOT NULL,
    cashback_base_usd numeric(12,2) NOT NULL,
    cashback_amount_usd numeric(12,2) NOT NULL,
    cashback_user_usd numeric(12,2) NOT NULL,
    cashback_own_usd numeric(12,2) NOT NULL,
    status text NOT NULL,
    parent_txn bigint,
    payout_id text,
    user_id text NOT NULL,
    customer_name text NOT NULL,
    failed_reason text,
    raw_txn jsonb DEFAULT '{}'::jsonb NOT NULL
);



CREATE TABLE public.merchants (
    merchant_name text NOT NULL,
    description text,
    logo_url text NOT NULL,
    image_url text,
    categories text[] NOT NULL,
    price_range text,
    website text,
    phone text,
    search_index text
);



CREATE MATERIALIZED VIEW public.cashback_transaction_stats AS
 SELECT date_trunc('day'::text, t.purchase_date) AS date,
    t.customer_name,
    t.merchant_name,
    t.merchant_network,
    t.status,
    m.categories,
    count(t.id) AS transactions_count,
    sum(t.purchase_amount) AS purchase_amount_sum,
    sum(t.cashback_base_usd) AS cashback_base_usd_sum,
    sum(t.cashback_amount_usd) AS cashback_amount_usd_sum,
    sum(t.cashback_user_usd) AS cashback_user_usd_sum,
    sum(t.cashback_own_usd) AS cashback_own_usd_sum
   FROM (public.cashback_transaction t
     JOIN public.merchants m ON ((t.merchant_name = m.merchant_name)))
  GROUP BY (date_trunc('day'::text, t.purchase_date)), t.customer_name, t.merchant_name, t.merchant_network, t.status, m.categories
  WITH NO DATA;



CREATE TABLE public.config (
    key character varying NOT NULL,
    value jsonb
);



CREATE TABLE public.customer (
    name text NOT NULL,
    api_key text NOT NULL,
    hash text,
    role text[],
    webhook_url text,
    webhook_key text,
    CONSTRAINT webhook_key_url_nonempty CHECK (((webhook_url IS NULL) OR (webhook_key IS NOT NULL)))
);



CREATE TABLE public.customer_networks (
    customer_name text NOT NULL,
    network_name text NOT NULL
);



CREATE TABLE public.event_log (
    event_id bigint NOT NULL,
    "timestamp" timestamp NOT NULL,
    object_type text NOT NULL,
    object_id text,
    raw_object jsonb,
    message text,
    details text
);



CREATE SEQUENCE public.event_log_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.event_log_event_id_seq OWNED BY public.event_log.event_id;



CREATE TABLE public.merchant_category_mappings (
    foreign_category text[] NOT NULL,
    common_category text[] NOT NULL
);



CREATE TABLE public.merchant_name_mappings (
    foreign_name text NOT NULL,
    common_name text NOT NULL
);



CREATE TABLE public.merchant_offers (
    offer_id text NOT NULL,
    offer_description text NOT NULL,
    offer_location public.geometry,
    offer_address text,
    merchant_name text NOT NULL,
    merchant_network text NOT NULL,
    toc_text text,
    toc_url text,
    images text[],
    when_activated timestamp NOT NULL,
    when_deactivated timestamp,
    requires_activation boolean NOT NULL,
    requires_browser_cookies boolean NOT NULL,
    requires_bank_link boolean NOT NULL,
    requires_card_link boolean NOT NULL,
    requires_geo_tracking boolean NOT NULL,
    requires_exclusive boolean DEFAULT false NOT NULL,
    requires_experimental boolean DEFAULT false NOT NULL,
    reward_fixed_best numeric(12,2),
    reward_percent_best numeric(12,2),
    reward_limit numeric(12,2),
    reward_currency text,
    accepted_cards text[],
    tracking_rule text,
    offer_raw_src jsonb NOT NULL,
    when_updated timestamp NOT NULL,
    reward_items jsonb DEFAULT '[]'::jsonb NOT NULL,
    when_modified timestamp NOT NULL,
    search_index text
);



CREATE TABLE public.merchant_usebutton (
    id text NOT NULL,
    name text NOT NULL,
    categories character varying[] NOT NULL,
    urls jsonb NOT NULL,
    metadata jsonb NOT NULL,
    available_platforms character varying[] NOT NULL,
    supported_products character varying[] NOT NULL,
    status character varying NOT NULL,
    cpi_fixed numeric(11,2),
    cpa_percent numeric(11,2),
    cpa_fixed numeric(11,2),
    featured jsonb DEFAULT '{}'::jsonb NOT NULL,
    experimental boolean DEFAULT false NOT NULL,
    deactivation_date timestamp,
    revenue_share_percent integer DEFAULT 80 NOT NULL,
    terms_and_conditions text,
    exclusive_offer boolean DEFAULT false NOT NULL
);



CREATE TABLE public.network (
    network_name text NOT NULL,
    description text
);



CREATE TABLE public.session (
    session_id bigint NOT NULL,
    customer_name text,
    session_token text NOT NULL,
    started_at timestamp,
    expired_at timestamp
);



CREATE SEQUENCE public.session_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.session_session_id_seq OWNED BY public.session.session_id;



CREATE TABLE public.txn_delivery_queue (
    txn_id uuid NOT NULL,
    customer_name text NOT NULL,
    when_created timestamp NOT NULL,
    when_next_attempt timestamp NOT NULL,
    when_last_attempt timestamp,
    last_attempt_outcome text,
    attempt_count integer,
    batch_id uuid
);



ALTER TABLE ONLY public.event_log ALTER COLUMN event_id SET DEFAULT nextval('public.event_log_event_id_seq'::regclass);



ALTER TABLE ONLY public.session ALTER COLUMN session_id SET DEFAULT nextval('public.session_session_id_seq'::regclass);



COPY public.config (key, value) FROM stdin;
build	{"envMode": "Test", "version": "0.0.1"}
http	{"port": 8500}
offers	{"baseUrl": "https://localhost:8500", "maxCategory": 5, "minCategory": 30}
scheduler	{"enabled": false, "eventLogCron": "0 0 0 * * ?", "statisticsCron": "0 0 * * * ?", "eventLogTresholdDays": 30}
auth	{"key": "F0p3LFStPqF2IdJXOq15A2fBWO11bgANHaHU6YEb", "enabled": true, "expirationMinutes": 20160, "sessionCacheSeconds": 10}
\.



COPY public.network (network_name, description) FROM stdin;
_generated_	Test generated offers
usebutton	Usebutton offers
coupilia	Coupilia offers
azigo	Azigo offers
mogl	Empyr/Mogl offers
\.



SELECT pg_catalog.setval('public.event_log_event_id_seq', 1, false);



SELECT pg_catalog.setval('public.session_session_id_seq', 1, false);



ALTER TABLE ONLY public.azigo_txn
    ADD CONSTRAINT azigo_txn_pkey PRIMARY KEY (transaction_id);



ALTER TABLE ONLY public.azigo_txn
    ADD CONSTRAINT azigo_txn_unique_record_id_key UNIQUE (unique_record_id);



ALTER TABLE ONLY public.cashback_transaction
    ADD CONSTRAINT cashback_transaction_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.cashback_transaction
    ADD CONSTRAINT cashback_transaction_ref_unique UNIQUE (reference, merchant_network);



ALTER TABLE ONLY public.config
    ADD CONSTRAINT config_pkey PRIMARY KEY (key);



ALTER TABLE ONLY public.customer
    ADD CONSTRAINT customer_api_key_key UNIQUE (api_key);



ALTER TABLE ONLY public.customer
    ADD CONSTRAINT customer_name_pk PRIMARY KEY (name);



ALTER TABLE ONLY public.customer_networks
    ADD CONSTRAINT customer_networks_pk PRIMARY KEY (customer_name, network_name);



ALTER TABLE ONLY public.event_log
    ADD CONSTRAINT event_log_pkey PRIMARY KEY (event_id);



ALTER TABLE ONLY public.merchant_category_mappings
    ADD CONSTRAINT merchant_category_mappings_pk PRIMARY KEY (foreign_category);



ALTER TABLE ONLY public.merchant_name_mappings
    ADD CONSTRAINT merchant_name_mapping_pkey PRIMARY KEY (foreign_name);



ALTER TABLE ONLY public.merchant_offers
    ADD CONSTRAINT merchant_offers_pk PRIMARY KEY (offer_id, merchant_network);



ALTER TABLE ONLY public.merchant_usebutton
    ADD CONSTRAINT merchant_usebutton_name_unique UNIQUE (name);



ALTER TABLE ONLY public.merchant_usebutton
    ADD CONSTRAINT merchant_usebutton_pk PRIMARY KEY (id);



ALTER TABLE ONLY public.merchants
    ADD CONSTRAINT merchants_pk PRIMARY KEY (merchant_name);



ALTER TABLE ONLY public.network
    ADD CONSTRAINT network_pkey PRIMARY KEY (network_name);



ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (session_id);



ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_session_token_key UNIQUE (session_token);



ALTER TABLE ONLY public.txn_delivery_queue
    ADD CONSTRAINT txn_delivery_queue_pkey PRIMARY KEY (txn_id);



CREATE INDEX cashback_transaction_merchant_name_gin ON public.cashback_transaction USING gin (to_tsvector('english'::regconfig, merchant_name));



CREATE INDEX customer_api_key_idx ON public.customer USING btree (api_key);



CREATE INDEX merchant_offers_merchant_name_gin ON public.merchant_offers USING gin (to_tsvector('english'::regconfig, merchant_name));



CREATE INDEX merchant_offers_search_index_gin ON public.merchant_offers USING gin (to_tsvector('english'::regconfig, search_index));



CREATE INDEX merchants_search_index_gin ON public.merchants USING gin (to_tsvector('english'::regconfig, search_index));



CREATE INDEX session_token_expired_at_idx ON public.session USING btree (session_token, expired_at);



CREATE INDEX session_token_idx ON public.session USING btree (session_token);



ALTER TABLE ONLY public.customer_networks
    ADD CONSTRAINT customer_networks_customer_name_fk FOREIGN KEY (customer_name) REFERENCES public.customer(name);



ALTER TABLE ONLY public.customer_networks
    ADD CONSTRAINT customer_networks_network_name_fk FOREIGN KEY (network_name) REFERENCES public.network(network_name) ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY public.merchant_offers
    ADD CONSTRAINT merchant_offers_merchant_name_fk FOREIGN KEY (merchant_name) REFERENCES public.merchants(merchant_name) ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY public.merchant_offers
    ADD CONSTRAINT merchant_offers_merchant_network_fk FOREIGN KEY (merchant_network) REFERENCES public.network(network_name) ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY public.txn_delivery_queue
    ADD CONSTRAINT txn_delivery_queue_txn_id FOREIGN KEY (txn_id) REFERENCES public.cashback_transaction(id) ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_customer_name_fk FOREIGN KEY (customer_name) REFERENCES public.customer(name);



REFRESH MATERIALIZED VIEW public.cashback_transaction_stats;



