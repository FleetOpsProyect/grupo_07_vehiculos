-- Mensajería (HU-13): outbox transaccional y registro de mensajes ya consumidos.

-- Eventos por publicar en SNS. Se guardan en la misma transacción del cambio que los genera.
-- payload es el sobre completo del evento: {event_id, event_type, occurred_at, source, correlation_id, data}.
CREATE TABLE outbox_events (
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id UUID         NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

-- El publicador busca los PENDING más antiguos primero
CREATE INDEX ix_outbox_status_created ON outbox_events (status, created_at);

-- Mensajes de SQS ya aplicados. event_id es texto y no UUID: G1 usa ids que no son UUID.
CREATE TABLE processed_events (
    event_id     VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
