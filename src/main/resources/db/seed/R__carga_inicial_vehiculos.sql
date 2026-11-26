-- Carga inicial de la flota de FleetCorp S.A. para ambientes local y EC2: 50 vehículos de ejemplo
-- (20 automóviles, 20 camionetas y 10 vehículos especializados), todos en ACTIVE.
-- Es una migración repetible de Flyway: se vuelve a ejecutar solo si cambia este archivo, y es idempotente
-- (ON CONFLICT DO NOTHING), así que nunca duplica vehículos ni pisa los que se hayan editado por la API.
-- Cada vehículo nuevo recibe su fila VEHICLE_CREATED en el historial, igual que con POST /vehicles.
-- Las pruebas automáticas no cargan esta carpeta (ver PostgresIntegrationTest).
WITH seed (vehicle_id, plate, vehicle_type, brand, model, vehicle_year, color, notes, odometer_km, created_at) AS (
    VALUES
    ('3451d013-5675-46ad-b25b-55dd78572976', 'VZZ960', 'CAR', 'Chevrolet', 'Onix', 2020, 'Silver', 'Assigned to Barranquilla branch', 96395.4, '2026-01-05 08:00:00-05'),
    ('a72991b9-e8c1-4743-babe-c539007d1034', 'PDF497', 'CAR', 'Renault', 'Logan', 2023, 'White', 'Assigned to Medellin branch', 44723.1, '2026-01-05 08:10:00-05'),
    ('330698a1-c009-4492-b624-6771c8450070', 'VHS908', 'CAR', 'Mazda', '2', 2018, 'White', 'Assigned to Cali branch', 98975.9, '2026-01-05 08:20:00-05'),
    ('cd02c5e1-1635-4d03-951f-d8f9a2c68e45', 'RTR861', 'CAR', 'Kia', 'Picanto', 2016, 'Red', 'Assigned to Barranquilla branch', 60049.4, '2026-01-05 08:30:00-05'),
    ('28aaca51-b98c-47c2-95bd-448ff26149ed', 'HGB254', 'CAR', 'Toyota', 'Corolla', 2018, 'Blue', 'Assigned to Pereira branch', 119287.6, '2026-01-05 08:40:00-05'),
    ('ce76e9f4-7721-4e9e-a7a4-6309973f7986', 'GZZ585', 'CAR', 'Renault', 'Sandero', 2024, 'Black', 'Assigned to Cali branch', 43192.7, '2026-01-05 08:50:00-05'),
    ('27e9e06f-59b4-4e92-affd-deeaa842bc19', 'XXG121', 'CAR', 'Volkswagen', 'Gol', 2017, 'Gray', 'Assigned to Barranquilla branch', 67071.1, '2026-01-05 09:00:00-05'),
    ('b9f3635c-f88c-422b-8ca2-a92b03a56cc1', 'FWG544', 'CAR', 'Nissan', 'Versa', 2019, 'Red', 'Assigned to Pereira branch', 56507.0, '2026-01-05 09:10:00-05'),
    ('d37ee915-31de-44f4-9f2a-8b79fc8e80b3', 'JBL317', 'CAR', 'Hyundai', 'Accent', 2025, 'Silver', 'Assigned to Medellin branch', 6657.0, '2026-01-05 09:20:00-05'),
    ('c38084a0-3d93-4d4c-804c-25d64affdcd1', 'YNL657', 'CAR', 'Chevrolet', 'Joy', 2024, 'White', 'Assigned to Bucaramanga branch', 17003.3, '2026-01-05 09:30:00-05'),
    ('0f977044-218e-4b7b-958d-cdb46b446806', 'PTY934', 'CAR', 'Chevrolet', 'Onix', 2021, 'Gray', 'Assigned to Barranquilla branch', 74773.2, '2026-01-05 09:40:00-05'),
    ('d3bf6d01-6bae-4b5b-844a-7034e77ffe48', 'WGX255', 'CAR', 'Renault', 'Logan', 2019, 'Silver', 'Assigned to Bogota branch', 118180.6, '2026-01-05 09:50:00-05'),
    ('df703017-04c9-478d-82b3-359986048719', 'THZ104', 'CAR', 'Mazda', '2', 2024, 'Gray', 'Assigned to Bucaramanga branch', 26834.3, '2026-01-05 10:00:00-05'),
    ('2c1eea1f-2659-44a7-8c96-6f46c6aa7d55', 'GVZ842', 'CAR', 'Kia', 'Picanto', 2022, 'Gray', 'Assigned to Bucaramanga branch', 86652.0, '2026-01-05 10:10:00-05'),
    ('537390e5-0fcf-41ca-8e75-2fdf1ece615d', 'WWX594', 'CAR', 'Toyota', 'Corolla', 2021, 'Silver', 'Assigned to Medellin branch', 37919.2, '2026-01-05 10:20:00-05'),
    ('e21b37ca-1b29-4c99-86c8-0e2bc8c614b2', 'XCK295', 'CAR', 'Renault', 'Sandero', 2015, 'Blue', 'Assigned to Cali branch', 197975.3, '2026-01-05 10:30:00-05'),
    ('1905d591-c5b2-475a-8acd-8be146e40990', 'WTX128', 'CAR', 'Volkswagen', 'Gol', 2020, 'Gray', 'Assigned to Pereira branch', 65718.7, '2026-01-05 10:40:00-05'),
    ('1038f0b5-e998-40ee-a4dd-f9b9c28ee907', 'TNZ617', 'CAR', 'Nissan', 'Versa', 2022, 'White', 'Assigned to Cali branch', 84238.8, '2026-01-05 10:50:00-05'),
    ('b156d1ad-330c-46a3-831d-03bf9b2bd6c0', 'LTW646', 'CAR', 'Hyundai', 'Accent', 2025, 'White', 'Assigned to Cali branch', 17430.0, '2026-01-05 11:00:00-05'),
    ('f10637ce-81fc-469e-ba60-9683ceaf4915', 'KWL672', 'CAR', 'Chevrolet', 'Joy', 2024, 'Blue', 'Assigned to Barranquilla branch', 19676.6, '2026-01-05 11:10:00-05'),
    ('d70a39d1-33dc-477f-b179-f2d2e48b9662', 'TGS224', 'PICKUP_TRUCK', 'Toyota', 'Hilux', 2025, 'Gray', 'Assigned to Cali branch', 20991.4, '2026-01-05 11:20:00-05'),
    ('12926185-50e4-4d54-b12e-a6b36471fde4', 'KSD317', 'PICKUP_TRUCK', 'Nissan', 'Frontier', 2024, 'White', 'Assigned to Barranquilla branch', 12005.2, '2026-01-05 11:30:00-05'),
    ('1f525265-c8b0-47ee-8d82-feacab6286cd', 'GPG359', 'PICKUP_TRUCK', 'Ford', 'Ranger', 2017, 'Blue', 'Assigned to Bogota branch', 105879.3, '2026-01-05 11:40:00-05'),
    ('77bd891f-f7b1-43df-a323-1e1ee2015522', 'KFR598', 'PICKUP_TRUCK', 'Chevrolet', 'D-Max', 2022, 'White', 'Assigned to Bogota branch', 50545.3, '2026-01-05 11:50:00-05'),
    ('d51b1815-aaf7-49f3-bd68-373b29acf1a5', 'KHS627', 'PICKUP_TRUCK', 'Mitsubishi', 'L200', 2017, 'Black', 'Assigned to Bucaramanga branch', 87598.8, '2026-01-05 12:00:00-05'),
    ('321c5296-6bd8-4676-96d0-50cd67601367', 'PND839', 'PICKUP_TRUCK', 'Renault', 'Alaskan', 2020, 'Blue', 'Assigned to Barranquilla branch', 127688.7, '2026-01-05 12:10:00-05'),
    ('8dd63cb9-5685-4624-84fc-d5555daf106d', 'TTB493', 'PICKUP_TRUCK', 'Volkswagen', 'Amarok', 2017, 'Silver', 'Assigned to Bogota branch', 145753.1, '2026-01-05 12:20:00-05'),
    ('4ba2e161-9fb9-4f50-8476-8b8c54dd0ba5', 'WDF907', 'PICKUP_TRUCK', 'Mazda', 'BT-50', 2024, 'Silver', 'Assigned to Medellin branch', 19585.8, '2026-01-05 12:30:00-05'),
    ('1ad2d5f1-e05b-4e13-b8c1-10fb3a828159', 'DLL140', 'PICKUP_TRUCK', 'Toyota', 'Hilux', 2023, 'Gray', 'Assigned to Bucaramanga branch', 43884.4, '2026-01-05 12:40:00-05'),
    ('453bf491-2e7a-46e9-876c-603fe7e8f9f6', 'GSL515', 'PICKUP_TRUCK', 'Nissan', 'Frontier', 2023, 'Red', 'Assigned to Pereira branch', 49486.3, '2026-01-05 12:50:00-05'),
    ('83c8cb28-eb4e-42e3-895e-8b6b263cfa5e', 'YVN191', 'PICKUP_TRUCK', 'Ford', 'Ranger', 2025, 'Red', 'Assigned to Barranquilla branch', 11767.6, '2026-01-05 13:00:00-05'),
    ('b02e3d8d-ccb1-451d-8eba-0ea84770a087', 'HSD375', 'PICKUP_TRUCK', 'Chevrolet', 'D-Max', 2022, 'Blue', 'Assigned to Barranquilla branch', 24232.9, '2026-01-05 13:10:00-05'),
    ('16ac4191-a26a-40ae-844f-1574f037afc6', 'LDZ976', 'PICKUP_TRUCK', 'Mitsubishi', 'L200', 2018, 'Black', 'Assigned to Bogota branch', 54950.9, '2026-01-05 13:20:00-05'),
    ('dcded204-43b3-4f66-910e-2cb638efbaeb', 'FTB447', 'PICKUP_TRUCK', 'Renault', 'Alaskan', 2016, 'White', 'Assigned to Bucaramanga branch', 75715.1, '2026-01-05 13:30:00-05'),
    ('ed3a32a8-6af2-4748-8d95-9c31fe8ad4a1', 'LZG144', 'PICKUP_TRUCK', 'Volkswagen', 'Amarok', 2020, 'Red', 'Assigned to Cali branch', 37172.4, '2026-01-05 13:40:00-05'),
    ('f0290531-3d0a-470b-b5a4-32cf86e3e726', 'FHL151', 'PICKUP_TRUCK', 'Mazda', 'BT-50', 2021, 'Silver', 'Assigned to Pereira branch', 46442.3, '2026-01-05 13:50:00-05'),
    ('4fdebbec-eea7-4b64-b3a7-15682e5f950c', 'MWJ396', 'PICKUP_TRUCK', 'Toyota', 'Hilux', 2020, 'Black', 'Assigned to Cali branch', 41766.5, '2026-01-05 14:00:00-05'),
    ('2d8ad8c0-ac12-4e93-8005-ce74721888ff', 'LPB356', 'PICKUP_TRUCK', 'Nissan', 'Frontier', 2022, 'Black', 'Assigned to Barranquilla branch', 41206.1, '2026-01-05 14:10:00-05'),
    ('bbab27f6-04b8-457d-83ed-b92009758340', 'WXJ626', 'PICKUP_TRUCK', 'Ford', 'Ranger', 2020, 'Blue', 'Assigned to Medellin branch', 78820.1, '2026-01-05 14:20:00-05'),
    ('72723b9c-ef44-40d5-bee4-da5a7989e9d0', 'FSV659', 'PICKUP_TRUCK', 'Chevrolet', 'D-Max', 2023, 'White', 'Assigned to Bogota branch', 63500.8, '2026-01-05 14:30:00-05'),
    ('f86664ae-64a1-49f5-a383-8b9ed5a9422a', 'WMJ335', 'SPECIALIZED_VEHICLE', 'Chevrolet', 'NHR', 2017, 'White', 'Refrigerated van for perishable goods - Pereira branch', 128105.4, '2026-01-05 14:40:00-05'),
    ('e1c60aa3-d510-4b04-b2d9-0dcd57bb7d97', 'GRP155', 'SPECIALIZED_VEHICLE', 'Hino', 'Dutro', 2023, 'White', 'Crane truck for equipment installation - Medellin branch', 64904.4, '2026-01-05 14:50:00-05'),
    ('121ae3e6-03a6-4966-a13b-ca7fd644de2f', 'LSH156', 'SPECIALIZED_VEHICLE', 'Foton', 'Aumark', 2019, 'White', 'Tool van for field technical support - Bucaramanga branch', 78637.4, '2026-01-05 15:00:00-05'),
    ('618177ff-d75d-4769-aa4c-5c6015a0cce6', 'WMZ348', 'SPECIALIZED_VEHICLE', 'Isuzu', 'NPR', 2018, 'White', 'Mobile workshop for fleet maintenance - Bucaramanga branch', 113653.6, '2026-01-05 15:10:00-05'),
    ('759eb559-0b94-4f3a-8b05-e1aeb153d69c', 'HHL556', 'SPECIALIZED_VEHICLE', 'Mercedes-Benz', 'Sprinter', 2025, 'White', 'Field technical support unit - Bogota branch', 15425.7, '2026-01-05 15:20:00-05'),
    ('f637a468-5d38-4e06-8363-e5d900ed6b02', 'NXN350', 'SPECIALIZED_VEHICLE', 'JAC', 'N-Series', 2018, 'White', 'Utility truck for logistics operations - Bogota branch', 151294.0, '2026-01-05 15:30:00-05'),
    ('4f3e885e-e1e4-47b7-b735-efe608d18011', 'JPH101', 'SPECIALIZED_VEHICLE', 'Renault', 'Master', 2018, 'White', 'Staff transport van - Bogota branch', 110399.2, '2026-01-05 15:40:00-05'),
    ('79823eb2-1579-4a0a-a1b2-480c55d85e8d', 'LWJ354', 'SPECIALIZED_VEHICLE', 'Hyundai', 'HD35', 2015, 'White', 'Box truck for light logistics - Cali branch', 202751.8, '2026-01-05 15:50:00-05'),
    ('17420e94-0144-402b-86b7-89ef81365acc', 'LDG509', 'SPECIALIZED_VEHICLE', 'Chevrolet', 'NKR', 2018, 'White', 'Refrigerated truck for medical supplies - Pereira branch', 122299.3, '2026-01-05 16:00:00-05'),
    ('05c22d3f-64db-48d3-8aaa-af81963892a7', 'MMK186', 'SPECIALIZED_VEHICLE', 'Ford', 'Transit', 2020, 'White', 'Mobile network maintenance unit - Medellin branch', 38215.0, '2026-01-05 16:10:00-05')
),
inserted AS (
    INSERT INTO vehicle (vehicle_id, plate, vehicle_type, brand, model, vehicle_year, color, notes, odometer_km,
                         status, active_route_id, deleted, version, created_at, updated_at)
    SELECT vehicle_id::uuid, plate, vehicle_type, brand, model, vehicle_year, color, notes, odometer_km,
           'ACTIVE', NULL, FALSE, 0, created_at::timestamptz, created_at::timestamptz
    FROM seed
    ON CONFLICT DO NOTHING
    RETURNING vehicle_id, created_at
)
INSERT INTO status_history (history_id, vehicle_id, from_status, to_status, cause, source_id, changed_at)
SELECT gen_random_uuid(), vehicle_id, NULL, 'ACTIVE', 'VEHICLE_CREATED', NULL, created_at
FROM inserted;
