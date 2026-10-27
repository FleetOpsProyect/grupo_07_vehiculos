-- Trazabilidad de cambios de estado (HISTORIAL_ESTADO del DAS general)
CREATE TABLE status_history (
    history_id  UUID        NOT NULL,
    vehicle_id  UUID        NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    cause       VARCHAR(60) NOT NULL,
    source_id   VARCHAR(64),
    changed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_status_history PRIMARY KEY (history_id),
    CONSTRAINT fk_history_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicle (vehicle_id) ON DELETE CASCADE
);

CREATE INDEX ix_status_history_vehicle ON status_history (vehicle_id, changed_at);
