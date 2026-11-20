ALTER TABLE categoria
    ADD COLUMN grupo VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

UPDATE categoria
SET grupo = nombre
WHERE grupo = '';

ALTER TABLE producto
    ADD COLUMN imagen_inicio VARCHAR(255) NULL,
    ADD COLUMN precio DECIMAL(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN stock INT NOT NULL DEFAULT 0,
    ADD COLUMN descuento INT NOT NULL DEFAULT 0;

UPDATE producto p
SET p.precio = COALESCE(
    (
        SELECT pp.precio
        FROM precio_producto pp
        WHERE pp.producto_id = p.id
        ORDER BY pp.vigente_desde DESC, pp.id DESC
        LIMIT 1
    ),
    0
);

UPDATE producto
SET activo = 0
WHERE nombre IN ('Manzana', 'Tomate', 'Leche Entera');

UPDATE categoria
SET activo = 0
WHERE nombre IN ('Frutas y Verduras', 'Lácteos', 'Bebidas');

ALTER TABLE producto
    DROP COLUMN unidad_medida;

DROP TABLE precio_producto;
