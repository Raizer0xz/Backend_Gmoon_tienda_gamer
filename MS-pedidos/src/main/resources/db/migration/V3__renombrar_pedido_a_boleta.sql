RENAME TABLE pedido TO boleta;
RENAME TABLE detalle_pedido TO detalle_boleta;

ALTER TABLE detalle_boleta
    DROP FOREIGN KEY fk_detalle_pedido_pedido,
    CHANGE COLUMN pedido_id boleta_id BIGINT NOT NULL;

ALTER TABLE detalle_boleta
    ADD CONSTRAINT fk_detalle_boleta_boleta
        FOREIGN KEY (boleta_id) REFERENCES boleta(id) ON DELETE CASCADE;
