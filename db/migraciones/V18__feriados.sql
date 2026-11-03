-- =====================================================================
-- Gestión Documental - Migración V18: calendario de feriados
--
-- Los días sin movimiento de un expediente y los plazos de los pases se
-- cuentan en días hábiles: lunes a viernes, menos los feriados de esta tabla.
--
-- Se cargan los feriados nacionales de FECHA FIJA de 2026 y 2027. Los
-- trasladables (Día de los Héroes, Paz del Chaco, Victoria de Boquerón) y
-- los asuetos los carga el administrador desde la pantalla, según el decreto
-- de cada año. No borra nada. Idempotente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS feriados (
    fecha               DATE PRIMARY KEY,
    descripcion         VARCHAR(150) NOT NULL,
    creado_por_id       INTEGER REFERENCES usuarios (id) ON DELETE RESTRICT,  -- NULL: carga inicial
    fecha_hora_creacion TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO feriados (fecha, descripcion) VALUES
    ('2026-01-01', 'Año Nuevo'),
    ('2026-04-02', 'Jueves Santo'),
    ('2026-04-03', 'Viernes Santo'),
    ('2026-05-01', 'Día del Trabajador'),
    ('2026-05-14', 'Independencia Nacional'),
    ('2026-05-15', 'Independencia Nacional'),
    ('2026-08-15', 'Fundación de Asunción'),
    ('2026-12-08', 'Virgen de Caacupé'),
    ('2026-12-25', 'Navidad'),
    ('2027-01-01', 'Año Nuevo'),
    ('2027-03-25', 'Jueves Santo'),
    ('2027-03-26', 'Viernes Santo'),
    ('2027-05-01', 'Día del Trabajador'),
    ('2027-05-14', 'Independencia Nacional'),
    ('2027-05-15', 'Independencia Nacional'),
    ('2027-08-15', 'Fundación de Asunción'),
    ('2027-12-08', 'Virgen de Caacupé'),
    ('2027-12-25', 'Navidad')
ON CONFLICT (fecha) DO NOTHING;
