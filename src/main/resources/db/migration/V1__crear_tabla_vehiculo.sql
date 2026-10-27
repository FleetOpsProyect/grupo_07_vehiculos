CREATE TABLE vehicle (
    vehicle_id      UUID           NOT NULL,
    plate           VARCHAR(6)     NOT NULL,
    vehicle_type    VARCHAR(30)    NOT NULL,
    brand           VARCHAR(50)    NOT NULL,
    model           VARCHAR(50)    NOT NULL,
    vehicle_year    INTEGER        NOT NULL,
    color           VARCHAR(30),
    notes           VARCHAR(500),
    odometer_km     NUMERIC(10, 1) NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    active_route_id UUID,
    deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
    version         BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_vehicle PRIMARY KEY (vehicle_id),
    CONSTRAINT ck_vehicle_year CHECK (vehicle_year BETWEEN 1980 AND 2100),
    CONSTRAINT ck_vehicle_odometer CHECK (odometer_km BETWEEN 0 AND 2000000)
);

-- La placa es única solo entre vehículos no eliminados (baja lógica)
CREATE UNIQUE INDEX ux_vehicle_plate_active ON vehicle (plate) WHERE deleted = FALSE;

-- Listados filtrados por estado y tipo
CREATE INDEX ix_vehicle_status_type ON vehicle (status, vehicle_type);
