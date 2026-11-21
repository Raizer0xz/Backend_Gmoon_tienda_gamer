# MS-pedidos

Microservicio de compras y comprobantes del catálogo Gmoon. La ruta principal del checkout es `POST /api/v1/boletas`; `POST /api/v1/pedidos` permanece como alias durante la transición.

## Registrar una compra

El gateway valida el JWT y entrega `X-Usuario-Id` al microservicio. El identificador del comprador se toma de ese encabezado, no del JSON enviado por el cliente. La boleta parte en estado `PENDIENTE` y su total se calcula sumando cantidad por precio unitario.

Antes de guardar la boleta, `MS-pedidos` consulta `MS-productos` por Eureka. El `id` de cada línea debe ser el ID numérico del catálogo. Se verifican el estado activo del producto y su categoría, la disponibilidad de stock y que el precio enviado coincida con el precio actual, incluido el descuento. El nombre y precio que se guardan se toman del catálogo, y un fallo de `MS-productos` responde `503`.

```json
{
  "clienteNombre": "Cliente Gmoon",
  "email": "cliente@example.com",
  "tipoComprobante": "boleta",
  "items": [
    {
      "id": "42",
      "nombre": "FC 2027 PS5",
      "cantidad": 2,
      "precioUnitario": 79990
    }
  ]
}
```

La respuesta incluye `numeroPedido`, `usuarioId`, `tipoComprobante`, `total`, estado y las líneas guardadas. Se aceptan `boleta` y `factura` sin distinguir mayúsculas.

El frontend React usa Axios para llamar a este endpoint a través del gateway. Si el catálogo cambia entre la carga del carrito y la compra, la solicitud se rechaza para que el cliente actualice el precio antes de confirmar.

El checkout sigue siendo una simulación: no procesa pagos reales. La boleta queda `PENDIENTE` y la comprobación de stock no lo descuenta ni crea una reserva. Una futura integración de pagos debe definir la reserva y liberación del inventario al cancelar o fallar una compra.

## Consultas y compatibilidad

- `GET /api/v1/boletas/usuario/{usuarioId}` lista las compras del usuario.
- Las rutas equivalentes bajo `/api/v1/pedidos` se mantienen por compatibilidad.
- Los campos heredados de sucursal y dirección quedan opcionales para conservar registros anteriores; el checkout Gmoon no los requiere.

El gateway limita el acceso a las compras mediante JWT y permisos por rol: clientes registran compras y consultan solo sus boletas; vendedores pueden consultar; administradores tienen acceso de gestión.

## Base de datos y pruebas

Flyway aplica las migraciones V1 y V2 para crear y adaptar el esquema inicial, y V3 renombra las tablas a `boleta` y `detalle_boleta` sin perder los registros existentes. También actualiza la clave foránea de las líneas para que apunte a `boleta`. Los registros históricos no tienen detalle de productos.

Ejecuta `.\mvnw.cmd test` desde esta carpeta. La prueba que carga el contexto completo requiere MySQL disponible según `application.properties`.
