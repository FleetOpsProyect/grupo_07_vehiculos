-- Versión de negocio del vehículo: es el data.version_number de los eventos (0 en el alta y +1 con cada
-- cambio). No es la columna version, que es el bloqueo optimista de JPA y no sale del servicio.
-- Los vehículos que ya existen toman el valor de version: también empezó en 0 y subió con cada cambio.
ALTER TABLE vehicle ADD COLUMN version_number BIGINT NOT NULL DEFAULT 0;

UPDATE vehicle SET version_number = version;

ALTER TABLE vehicle ADD CONSTRAINT ck_vehicle_version_number CHECK (version_number >= 0);
