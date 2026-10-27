-- Bloqueos activos de un vehículo (ADR-09): el estado se deriva de ellos.
-- block_key es determinista (INCIDENT:<id>, MAINTENANCE:<id>, DOCUMENT:<tipo>)
-- para que un evento repetido no cree dos bloqueos.
CREATE TABLE vehicle_block (
    block_id   UUID         NOT NULL,
    vehicle_id UUID         NOT NULL,
    block_key  VARCHAR(80)  NOT NULL,
    block_type VARCHAR(20)  NOT NULL,
    source_id  VARCHAR(64)  NOT NULL,
    reason     VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_vehicle_block PRIMARY KEY (block_id),
    CONSTRAINT uq_vehicle_block_key UNIQUE (vehicle_id, block_key),
    CONSTRAINT fk_block_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicle (vehicle_id) ON DELETE CASCADE
);
