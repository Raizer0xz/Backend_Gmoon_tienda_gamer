# MS-pedidos

Microservicio de compras y comprobantes del catálogo Gmoon. La ruta principal del checkout es `POST /api/v1/boletas`; `POST /api/v1/pedidos` permanece como alias durante la transición.

## Registrar una compra

El gateway valida el JWT y entrega `X-Usuario-Id` al microservicio. El identificador del comprador se toma de ese encabezado, no del JSON enviado por el cliente. La boleta parte en estado `PENDIENTE` y su total se calcula sumando cantidad por precio unitario.

```json
{
  "clienteNombre": "Cliente Gmoon",
  "email": "cliente@example.com",
  "tipoComprobante": "boleta",
  "items": [
    {
      "id": "fc-27-ps5",
      "nombre": "FC 2027 PS5",
      "cantidad": 2,
      "precioUnitario": 79990
    }
  ]
}
```

La respuesta incluye `numeroPedido`, `usuarioId`, `tipoComprobante`, `total`, estado y las líneas guardadas. Se aceptan `boleta` y `factura` sin distinguir mayúsculas.

El checkout actual es una simulación y todavía obtiene el catálogo/carrito del navegador. Por eso este servicio conserva los precios unitarios como snapshot recibido y calcula el total desde esas líneas; validar el precio contra el catálogo debe hacerse cuando el frontend use el catálogo backend. No se procesa ningún pago real.

El frontend todavía usa autenticación local y no llama a este endpoint. Para conectarlo, primero debe enviar el JWT que emite `MS-seguridad` a través del gateway; el gateway exige ese token para registrar y consultar compras.

## Consultas y compatibilidad

- `GET /api/v1/boletas/usuario/{usuarioId}` lista las compras del usuario.
- Las rutas equivalentes bajo `/api/v1/pedidos` se mantienen por compatibilidad.
- Los campos heredados de sucursal y dirección quedan opcionales para conservar registros anteriores; el checkout Gmoon no los requiere.

El gateway limita el acceso a las compras mediante JWT y permisos por rol.

## Base de datos y pruebas

Flyway aplica las migraciones V1 y V2 para crear y adaptar el esquema inicial, y V3 renombra las tablas a `boleta` y `detalle_boleta` sin perder los registros existentes. También actualiza la clave foránea de las líneas para que apunte a `boleta`. Los registros históricos no tienen detalle de productos.

Ejecuta `.\mvnw.cmd test` desde esta carpeta. La prueba que carga el contexto completo requiere MySQL disponible según `application.properties`.
