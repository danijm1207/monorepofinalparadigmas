-- Seed inicial para el Sistema de Investigación Criminal
-- Dos usuarios iniciales con UUID fijos y contraseñas hasheadas con bcrypt (coste 10)
-- No se crean casos, sospechosos ni evidencias en el seed.

INSERT INTO users (id, name, email, password_hash, role, state)
VALUES 
  (
    '00000000-0000-4000-8000-000000000001',
    'Administrador',
    'admin@investigacion.test',
    '$2b$10$Y339ukw.ko9.I4.RAgn1g.glHtS8AAYZ7GACzs5wbLlUGmhhQS6A6',
    'ADMIN',
    'ACTIVE'
  ),
  (
    '00000000-0000-4000-8000-000000000002',
    'Investigador',
    'user@investigacion.test',
    '$2b$10$2Xc0x3NKr6JsghhEdlpUtuSVsntT8IELcyCWthqQRhDYLyDNACZcm',
    'USER',
    'ACTIVE'
  )
ON CONFLICT (id) DO NOTHING;
