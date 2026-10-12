INSERT INTO users (email, password_hash, full_name, role, enabled, created_at, updated_at) 
VALUES 
('admin@taskflow.com', '$2a$12$hhwSAgX3PgWbDTTOXpU.N.jxAFwWyP.Ahm8Tv7XMDxwE/DvVenoX2', 'Admin Principal', 'ADMIN', true, NOW(), NOW()),
('user@taskflow.com', '$2a$12$jwtz6/sB86Pm8Qi.SU.5/ukDoMRLeJAO1Wg.umkB1.Nij11MGC/Q.', 'Usuario Normal', 'USER', true, NOW(), NOW());

/* Cambiar el hash de las contraseñas a un valor real
    usuario -> usuario1234
    admin -> admin1234
 */