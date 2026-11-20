ALTER TABLE pedido
    MODIFY COLUMN sucursal_id BIGINT NULL,
    MODIFY COLUMN direccion_entrega VARCHAR(255) NULL,
    MODIFY COLUMN total DECIMAL(12, 2) NOT NULL,
    ADD COLUMN numero_pedido VARCHAR(40) NULL,
    ADD COLUMN cliente_nombre VARCHAR(150) NULL,
    ADD COLUMN cliente_email VARCHAR(254) NULL,
    ADD COLUMN tipo_comprobante VARCHAR(20) NOT NULL DEFAULT 'BOLETA';

UPDATE pedido
SET numero_pedido = CONCAT('GM-LEGACY-', id),
    cliente_nombre = 'Cliente legado',
    cliente_email = ''
WHERE numero_pedido IS NULL;

ALTER TABLE pedido
    MODIFY COLUMN numero_pedido VARCHAR(40) NOT NULL,
    ADD CONSTRAINT uk_pedido_numero_pedido UNIQUE (numero_pedido);

CREATE TABLE detalle_pedido (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id VARCHAR(255) NOT NULL,
    nombre_producto VARCHAR(150) NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_detalle_pedido_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedido(id) ON DELETE CASCADE
);
