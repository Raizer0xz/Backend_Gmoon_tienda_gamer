# MS-gateway

Gateway de entrada para los microservicios de Gmoon. Usa Eureka para localizar servicios y requiere `JWT_SECRET` para validar tokens.

## Rutas públicas

| Ruta | Servicio | Comportamiento |
|---|---|---|
| `/auth/**` | `MS-SEGURIDAD` | Login, registro de clientes y validación de tokens |
| `GET /api/v1/productos/**` | `MS-PRODUCTOS` | Lectura pública; se reescribe temporalmente a `/api/v2/productos/**` |
| `GET /api/v1/categorias/**` | `MS-PRODUCTOS` | Lectura pública; se reescribe temporalmente a `/api/v2/categorias/**` |
| `/api/v1/usuarios/**` | `MS-USUARIOS` | Requiere JWT, excepto el alta pública por `POST` |
| `/api/v1/boletas/**` | `MS-PEDIDOS` | Ruta versionada protegida para compras y comprobantes Gmoon |
| `/api/v1/pedidos/**` | `MS-PEDIDOS` | Ruta antigua conservada temporalmente |
| `/docs/{servicio}/v3/api-docs/**` | Microservicios | Especificación OpenAPI |

Las rutas antiguas `/api/v2/productos/**` y `/api/v2/categorias/**` se mantienen durante la transición. Los precios por sucursal fueron retirados al adaptar `MS-productos`; el precio vigente ahora forma parte del producto. `/api/v1/pedidos/**` se conserva como alias temporal de `/api/v1/boletas/**`.

Las solicitudes con JWT válido reciben `X-Usuario-Id`, `X-Usuario-Email` y `X-Usuario-Rol` derivados del token; los valores enviados por el cliente se reemplazan. Los permisos por rol se aplican antes de reenviar la solicitud.

## Configuración local

- Gateway: `http://localhost:5050`
- Frontend React: `http://localhost:5173` (origen CORS configurado)
- Configurar `JWT_SECRET` con la misma clave segura que usa `MS_gateway_security`; no guardar claves reales en el repositorio.
- Para el frontend, reservar `VITE_API_URL` para este gateway. El servicio Express de correos debe usar una variable separada, por ejemplo `VITE_EMAIL_URL`.

Ejecutar pruebas:

```powershell
.\mvnw.cmd '-Dtest=JwtAuthFilterTest,MsGatewayApplicationTests' test
```
