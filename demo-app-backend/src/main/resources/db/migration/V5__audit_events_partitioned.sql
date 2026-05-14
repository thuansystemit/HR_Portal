CREATE TABLE audit_events (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    actor_id     UUID,
    action       VARCHAR(80)  NOT NULL,
    entity_type  VARCHAR(60)  NOT NULL,
    entity_id    UUID,
    before_state JSONB,
    after_state  JSONB,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(512),
    outcome      VARCHAR(20)  NOT NULL DEFAULT 'success'
                     CHECK (outcome IN ('success', 'failure')),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ae_actor_fk FOREIGN KEY (actor_id)
        REFERENCES users (id) ON DELETE SET NULL
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit_events_2026_05
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_events_2026_06
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE audit_events_2026_07
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE audit_events_2026_08
    PARTITION OF audit_events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

ALTER TABLE audit_events
    ADD PRIMARY KEY (id, occurred_at);
