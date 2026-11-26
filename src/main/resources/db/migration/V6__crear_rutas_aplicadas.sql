-- Rutas cuya distancia ya se sumó al odómetro (route_finished_event de Telemetría). Garantiza que cada
-- ruta se sume una sola vez aunque llegue repetida con otro event_id.
CREATE TABLE applied_route (
    route_id    UUID           NOT NULL,
    vehicle_id  UUID           NOT NULL,
    distance_km NUMERIC(10, 1) NOT NULL,
    applied_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_applied_route PRIMARY KEY (route_id),
    CONSTRAINT ck_applied_route_distance CHECK (distance_km >= 0),
    CONSTRAINT fk_applied_route_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicle (vehicle_id) ON DELETE CASCADE
);
