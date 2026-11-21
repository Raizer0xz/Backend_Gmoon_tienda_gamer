# Gmoon Tienda Gamer: guía de estudio y documentación (puntos 1–6)

Este documento describe la arquitectura y el trabajo realizado para adaptar los microservicios existentes a Gmoon, conectar el frontend React mediante Axios y validar el flujo de compra. Está escrito para estudiar y explicar el proyecto; no reemplaza la pauta oficial del curso.

> **Estado importante:** la compra está integrada como simulación, no procesa pagos y no reserva ni descuenta inventario. Además, el bootstrap de las credenciales del primer ADMIN todavía requiere una decisión/provisión controlada. No se deben agregar contraseñas ni secretos reales al repositorio.

## 1. ¿Qué hace Gmoon y cuál es el alcance?

Gmoon es una tienda de productos gamer. El frontend muestra el catálogo, permite crear una cuenta e iniciar sesión, y registra una boleta simulada. El backend separa las responsabilidades para que perfiles, credenciales, catálogo y compras se puedan mantener de forma independiente.

El modelo de negocio de cinco entidades se distribuye entre cuatro bases de datos y dos microservicios de dominio:

| Entidad del modelo | Microservicio responsable | Base de datos | Responsabilidad |
|---|---|---|---|
| Usuario | `MS-usuarios` | `db_usuarios` | Perfil, correo, teléfono y rol |
| Producto | `MS-productos` | `db_productos` | Nombre, precio, stock, descuento e imágenes |
| Categoría | `MS-productos` | `db_productos` | Agrupación y estado de los productos |
| Boleta | `MS-pedidos` | `db_pedidos` | Cabecera de la compra, comprador, total y estado |
| Detalle de boleta | `MS-pedidos` | `db_pedidos` | Producto, cantidad, nombre y precio unitario de cada línea |

No son cinco procesos separados: producto y categoría comparten `MS-productos`; boleta y detalle comparten `MS-pedidos`. Seguridad y los componentes de infraestructura son servicios adicionales.

## 2. Arquitectura y recorrido de una solicitud

```text
Navegador React
    │ Axios (JWT en rutas protegidas)
    ▼
MS-gateway :5050 ─────► MS-eureka-server :8761
    │                         ▲
    ├── MS-seguridad :8082 ───┤
    ├── MS-usuarios :8084 ────┤
    ├── MS-productos :8085 ───┤
    └── MS-pedidos :8088 ─────┘
                                  MS-pedidos consulta MS-productos
                                  mediante Feign y Eureka
```

Eureka registra las aplicaciones y permite resolverlas por nombre. El Gateway es la entrada HTTP del frontend: aplica autenticación/autorización y dirige las rutas al microservicio correspondiente. `MS-pedidos` usa un cliente Feign para consultar el catálogo; el navegador no decide el precio autoritativo.

| Componente | Puerto local | Función |
|---|---:|---|
| Eureka | 8761 | Registro y descubrimiento de servicios |
| Gateway | 5050 | Rutas públicas/protegidas, JWT, permisos y CORS |
| Seguridad | 8082 | BCrypt, login y emisión/validación de JWT |
| Usuarios | 8084 | Perfiles de clientes, vendedores y administradores |
| Productos | 8085 | Productos y categorías |
| Pedidos/Boletas | 8088 | Boletas y sus detalles |

La configuración local de MySQL usa `db_seguridad`, `db_usuarios` y `db_pedidos` en el puerto 3306, y `db_productos` normalmente en 3307. En la prueba integrada de esta etapa Productos se ejecutó con un override local hacia 3306; no se debe confundir ese ajuste de prueba con su configuración normal.

### Conceptos útiles para explicar

- **REST:** los recursos se consultan o modifican con métodos HTTP como GET, POST, PUT y DELETE.
- **JWT:** token firmado que identifica al usuario y su rol. El frontend lo adjunta como `Authorization: Bearer ...`; no es una contraseña.
- **BCrypt:** función de hash usada para guardar contraseñas sin almacenarlas en texto plano.
- **Eureka:** registro que evita fijar la dirección de cada servicio en cada consumidor.
- **Gateway:** punto de entrada que reúne rutas y reglas de acceso.
- **Feign:** cliente declarativo que permite a un servicio llamar a otro mediante su nombre registrado.
- **HATEOAS:** algunas respuestas de listas/entidades incluyen enlaces a acciones relacionadas.
- **Flyway:** aplica migraciones SQL versionadas para evolucionar la base de datos.
- **Axios:** cliente HTTP de React usado para llamar al Gateway y adjuntar el JWT.

## 3. Qué hace cada microservicio en Gmoon

### MS-eureka-server

Mantiene el registro de los servicios disponibles. El Gateway y las llamadas internas usan los nombres registrados, en vez de depender de una IP fija. Eureka no guarda productos, usuarios ni boletas.

### MS-gateway

Expone el puerto 5050 y dirige rutas, por ejemplo `/auth/**` hacia Seguridad, `/api/v1/usuarios/**` hacia Usuarios y `/api/v1/boletas/**` hacia Pedidos. Para productos y categorías presenta rutas públicas `/api/v1/...` y reescribe al contrato interno `/api/v2/...` que mantienen sus controladores.

El filtro valida firma y expiración del JWT, obtiene usuario/rol y reemplaza los encabezados internos `X-Usuario-Id`, `X-Usuario-Email` y `X-Usuario-Rol`. Así no se confía en valores de identidad enviados por el navegador. Las solicitudes sin token reciben 401 cuando la ruta requiere autenticación; un token válido sin permiso recibe 403.

Reglas centrales:

- GET de productos y categorías es público; las modificaciones requieren ADMIN.
- El alta de perfil de cliente es pública; la administración posterior de usuarios requiere ADMIN.
- CLIENTE puede crear una boleta y consultar únicamente la propia.
- VENDEDOR puede consultar boletas, pero no crear ni administrar.
- ADMIN puede gestionar las operaciones administrativas.

El frontend local permitido por CORS es `http://localhost:5173`. En esta configuración, `localhost` y `127.0.0.1` no son orígenes equivalentes.

### MS-seguridad

Separa credenciales de perfiles. Guarda el hash BCrypt, verifica las credenciales del login y emite JWT con identidad y rol. Antes de registrar credenciales comprueba mediante Eureka que el perfil exista y que coincidan usuario, correo y rol. Un perfil inválido se rechaza; si Usuarios no está disponible, se informa el fallo de dependencia.

Rutas principales:

- `POST /auth/registrar`: credenciales de un CLIENTE cuyo perfil ya existe.
- `POST /auth/login`: valida email y contraseña y devuelve el JWT.
- `POST /auth/validar`: verifica el token; lo usan componentes internos.
- `POST /auth/registrar-privilegiado`: registro de ADMIN/VENDEDOR, protegido para ADMIN.
- `GET /auth/health`: estado del servicio.

**Bootstrap pendiente:** Seguridad no crea un ADMIN por defecto. En la base local inspeccionada no hay credenciales ADMIN activas. La creación del primer ADMIN debe hacerse mediante un procedimiento autorizado y controlado; no debe resolverse agregando una contraseña fija a código, migraciones o documentación.

### MS-usuarios

Administra el perfil, no la contraseña. El registro público crea un perfil CLIENTE; luego Seguridad crea las credenciales asociadas. Esta separación evita duplicar contraseñas y permite que cada microservicio mantenga su responsabilidad.

Rutas de referencia: `POST /api/v1/usuarios`, `GET /api/v1/usuarios`, `GET /api/v1/usuarios/{id}`, `PUT /api/v1/usuarios/{id}` y `DELETE /api/v1/usuarios/{id}`. El Gateway limita las operaciones de consulta/administración a ADMIN. El endpoint de perfil público se usa durante el registro.

### MS-productos

Mantiene el catálogo y las categorías. Un producto incluye nombre, descripción, precio, stock, descuento, imágenes, estado activo y referencia a categoría. Una categoría contiene nombre, grupo, descripción, imagen y estado activo. Desactivar producto/categoría es baja lógica: se conserva la referencia histórica y se evita ofrecer elementos inactivos.

El contrato interno es `/api/v2/productos` y `/api/v2/categorias`. El Gateway expone `/api/v1/productos` y `/api/v1/categorias` para el frontend. Las colecciones usan respuestas HAL; el adaptador de catálogo del frontend convierte ese formato al que consume la tienda. Si hay productos con el mismo nombre, el adaptador mantiene identificadores de storefront únicos, pero conserva el ID numérico del catálogo para el checkout.

### MS-pedidos (Boletas)

Representa la compra y sus líneas. El nombre del servicio sigue siendo `MS-pedidos` por compatibilidad, pero el recurso principal y las tablas quedaron alineados con el diagrama: `boleta` y `detalle_boleta`. `/api/v1/pedidos` se mantiene como alias legado.

Al recibir una compra, el servicio toma el ID del usuario del encabezado confiable del Gateway, consulta el catálogo por Feign y valida que producto/categoría estén activos, que el stock alcance para la cantidad solicitada y que el precio enviado coincida con el precio actual, incluido el descuento. El nombre y precio guardados se toman del catálogo. El total se recalcula en el backend.

La boleta queda `PENDIENTE`. La consulta agrupa cantidades repetidas del mismo producto para verificar el stock total solicitado. **La verificación no reserva ni descuenta stock**; por eso dos compras concurrentes todavía podrían superar las existencias. Una compra real requiere definir pagos, reserva de inventario y liberación al cancelar o fallar.

## 4. Explicación de los puntos trabajados (1–5)

Los siguientes títulos resumen el orden del trabajo técnico realizado; si la pauta usa otros encabezados, conserva la explicación y cambia los títulos para que coincidan literalmente.

### Punto 1 — Alinear el modelo de negocio

El modelo pedido identifica Usuario, Producto, Categoría, Boleta y Detalle de boleta. Se repartieron por responsabilidad y se renombraron las tablas de Pedidos de `pedido`/`detalle_pedido` a `boleta`/`detalle_boleta` con Flyway V3. La migración renombra tablas y actualiza la clave foránea de detalle; no se pierde la cabecera histórica.

Los servicios tienen bases separadas, así que las relaciones entre microservicios se representan con IDs y llamadas REST/Feign, no con una clave foránea SQL entre bases distintas. Dentro de Pedidos, el detalle sí tiene relación con su boleta.

### Punto 2 — Completar los servicios y la seguridad del flujo

Se conectaron las responsabilidades de Seguridad, Usuarios, Productos y Pedidos. El alta crea primero el perfil y luego las credenciales; el registro de credenciales verifica el perfil. El Gateway valida el JWT y filtra operaciones según CLIENTE, VENDEDOR o ADMIN. Los IDs de usuario no se aceptan como autoridad desde el body de compra.

La seguridad protege las rutas del Gateway y establece el límite de confianza entre frontend y servicios. Las claves de firma se suministran por configuración del entorno; el valor real no forma parte de este documento ni debe quedar en Git.

### Punto 3 — Integrar React con Axios

El frontend utiliza un cliente Axios configurado con `VITE_GATEWAY_URL` (por defecto `http://localhost:5050`). Las funciones de API agrupan auth, catálogo, boletas y usuarios; el token se adjunta a las rutas protegidas.

El flujo de registro crea perfil y credenciales. Si la segunda operación falla, la interfaz informa el error y no finge que el registro terminó por completo; el perfil ya creado puede requerir atención. El catálogo adapta la respuesta HAL. Checkout envía los IDs del catálogo y conserva el carrito si el backend rechaza la operación. El total y el número de boleta se toman de la respuesta del backend.

No todo el panel está conectado: gestión de usuarios usa el backend, mientras que CRUD de productos/categorías e historial administrativo siguen siendo locales.

### Punto 4 — Validar el sistema integrado

Se levantaron MySQL, Eureka, Gateway y los cinco microservicios; se comprobó su registro y el recorrido desde React, pasando por Axios y Gateway, hasta Pedidos y Productos. La prueba usó `localhost`, que coincide con el origen CORS configurado.

En una compra de prueba se usó un producto temporal de $1.000 con 10% de descuento y cantidad 2. El backend calculó $900 por unidad y una boleta de $1.800. Se comprobó que un precio manipulado y una cantidad superior al stock fueran rechazados con 400.

También se verificaron permisos: un cliente pudo consultar sus boletas (200) y recibió 403 al consultar las de otra persona o la lista de usuarios; un vendedor pudo consultar boletas (200) y recibió 403 al intentar crear una; sin token se recibió 401. Al terminar se eliminaron la cuenta, credenciales, boleta, detalle y producto temporales. Las consultas de verificación confirmaron cero filas de esos datos de QA.

### Punto 5 — Documentar y entregar

Este documento reúne la especificación resumida, arquitectura, guía de uso, rutas principales, flujo de integración, configuración y resultados de pruebas. Para los esquemas exactos de petición/respuesta se deben consultar las anotaciones OpenAPI de los controladores y los documentos JSON expuestos por el Gateway:

- `/docs/seguridad/v3/api-docs`
- `/docs/usuarios/v3/api-docs`
- `/docs/productos/v3/api-docs`
- `/docs/pedidos/v3/api-docs`

Los Swagger UI individuales se pueden abrir directamente en el puerto local de cada servicio cuando estén levantados. Los endpoints y permisos pueden evolucionar; OpenAPI y el código son la fuente del contrato vigente.

## 5. Requisitos del sistema (ERS resumida)

### Actores

- **Visitante:** consulta catálogo y puede registrarse como cliente.
- **Cliente:** inicia sesión, consulta el catálogo y registra/consulta sus propias boletas.
- **Vendedor:** consulta boletas para apoyar la atención de la tienda.
- **Administrador:** gestiona perfiles, catálogo y operaciones administrativas.
- **Servicios internos:** Gateway, Seguridad, Eureka y los microservicios de dominio.

### Requisitos funcionales implementados

1. Consultar productos y categorías activos.
2. Crear perfil de cliente y credenciales separadas.
3. Iniciar sesión y usar JWT en solicitudes protegidas.
4. Restringir operaciones según rol y propiedad de la boleta.
5. Registrar una boleta con líneas, comprobando existencia, estado, stock y precio vigente del catálogo.
6. Calcular el total en el backend y devolver el resultado al frontend.
7. Mantener el contrato de boletas alineado con `boleta`/`detalle_boleta` y conservar rutas antiguas compatibles.

### Requisitos no funcionales y límites

- Las contraseñas se guardan como hashes BCrypt; los secretos se configuran fuera del código.
- Las rutas entre servicios se descubren con Eureka y la consulta del catálogo desde Pedidos usa Feign.
- Los errores de autenticación/permisos distinguen 401 y 403; si el catálogo no está disponible durante el checkout, se informa indisponibilidad (503).
- El checkout no cobra dinero ni reserva stock.
- El bootstrap de ADMIN sigue pendiente y debe realizarse de manera segura antes de habilitar administración en un entorno nuevo.
- No existe una transacción distribuida entre la creación de perfil y credenciales; un fallo en la segunda operación puede dejar un perfil sin credenciales.

## 6. Guía de uso y prueba local

### Configuración

1. Instalar/levantar MySQL y crear/configurar las bases indicadas en la tabla de arquitectura.
2. Configurar `JWT_SECRET` en el entorno de ejecución para Seguridad y Gateway, usando el mismo secreto en ambos. Mantenerlo fuera del repositorio y no copiar el secreto de pruebas.
3. Confirmar las URL/credenciales de MySQL para cada servicio. Productos usa 3307 en su configuración local normal.
4. En el frontend, usar `VITE_GATEWAY_URL=http://localhost:5050`. `VITE_API_URL` sigue reservado para el servicio local de correo.
5. Usar `localhost` en lugar de `127.0.0.1` con la configuración CORS actual.

### Orden de arranque sugerido

1. MySQL.
2. Eureka.
3. Seguridad y Usuarios.
4. Productos.
5. Pedidos.
6. Gateway.
7. Frontend.

En cada microservicio Spring se puede ejecutar desde su carpeta:

```powershell
.\mvnw.cmd spring-boot:run
```

En `frontend`:

```powershell
npm install
npm run dev
```

### Flujo del cliente

1. Abrir la tienda y cargar productos/categorías.
2. Registrarse. El sistema crea un perfil CLIENTE y luego sus credenciales.
3. Iniciar sesión con email y contraseña; el frontend guarda el JWT de sesión y lo adjunta a llamadas protegidas.
4. Agregar productos al carrito y confirmar la compra.
5. Si el catálogo cambió, no hay stock suficiente o falla un servicio, leer el error y volver a actualizar/reintentar. El carrito no se vacía si la API no confirma la boleta.
6. La respuesta muestra el número, total y estado de la boleta. No se realiza un cobro real.

### Endpoints principales

| Método | Ruta pública del Gateway | Acceso resumido |
|---|---|---|
| POST | `/auth/registrar` | Registro de credenciales CLIENTE |
| POST | `/auth/login` | Público; devuelve JWT |
| POST | `/auth/validar` | Público para validación del token |
| POST | `/auth/registrar-privilegiado` | ADMIN |
| GET | `/api/v1/productos` y `/api/v1/categorias` | Público |
| POST/PUT/DELETE | Rutas de productos y categorías | ADMIN |
| POST | `/api/v1/usuarios` | Alta pública de perfil CLIENTE |
| GET/PUT/DELETE | `/api/v1/usuarios/**` | ADMIN |
| POST | `/api/v1/boletas` | CLIENTE o ADMIN |
| GET | `/api/v1/boletas/usuario/{id}` | CLIENTE solo para su ID; VENDEDOR/ADMIN según permiso |
| GET | `/api/v1/boletas` | VENDEDOR o ADMIN |
| PUT/DELETE | `/api/v1/boletas/{id}...` | ADMIN |

`/api/v1/pedidos/**` permanece como alias legado. Aunque el servicio de dominio se llame Pedidos, el contrato de checkout recomendado es Boletas.

Ejemplo ilustrativo del cuerpo de compra (no usar IDs inventados en una prueba real; deben existir en el catálogo):

```json
{
  "clienteNombre": "Cliente Gmoon",
  "email": "cliente@example.com",
  "tipoComprobante": "boleta",
  "items": [
    {
      "id": "42",
      "nombre": "Nombre del catálogo",
      "cantidad": 2,
      "precioUnitario": 79990
    }
  ]
}
```

El Gateway obtiene el usuario real desde el JWT; el backend vuelve a consultar el producto y no confía en el precio ni el nombre recibidos como fuente autoritativa.

### Validación ejecutada

| Área | Resultado registrado |
|---|---|
| Eureka | 1 prueba aprobada |
| Gateway | 18 pruebas aprobadas |
| Seguridad | 30 pruebas aprobadas |
| Usuarios | 32 pruebas aprobadas |
| Productos | 60 pruebas aprobadas |
| Pedidos | 14 pruebas aprobadas |
| **Backend total** | **155 pruebas aprobadas, 0 fallos/errores** |
| Frontend | 16 pruebas aprobadas; build exitoso |
| ESLint dirigido a archivos modificados | Aprobado |

La ejecución completa de ESLint del frontend mantiene un aviso/error previo de Fast Refresh en `CartContext.jsx`; no se atribuye a los cambios de esta integración. La prueba manual integrada cubrió registro, login, catálogo, checkout, precio/stock, permisos y limpieza de datos temporales.

## 7. Decisiones, pendientes y preguntas para la defensa

### Limitaciones conocidas

- No hay pago real ni descuento/reserva de inventario. Puede existir una condición de carrera entre dos compras que lean el mismo stock.
- La gestión de productos/categorías y el historial administrativo del frontend todavía son locales.
- Si falla Seguridad después de crear el perfil, queda un perfil sin credenciales; la interfaz informa el error, pero todavía no hay compensación automática.
- El esquema local de Seguridad aparece en `flyway_schema_history` como un **baseline versión 1**, no como una migración V1 ejecutada. Se verificó que la tabla `credenciales` existe y su estructura es utilizable; para una instalación limpia, comprobar explícitamente que Flyway ejecuta la migración V1.
- No había credenciales ADMIN activas en la base local inspeccionada. No declarar listo el acceso administrativo hasta completar el bootstrap de forma autorizada.

### Preguntas que conviene saber responder

**¿Por qué hay Gateway si cada servicio tiene su propio puerto?**
El frontend usa un único origen y el Gateway centraliza rutas, JWT y permisos. Los puertos internos no forman parte del contrato que consume React.

**¿Por qué se separan perfil y credenciales?**
Usuarios mantiene datos del perfil; Seguridad mantiene hashes y tokens. Seguridad consulta el perfil antes de crear credenciales para que no queden cuentas asociadas a IDs inexistentes.

**¿Por qué el backend vuelve a consultar el catálogo?**
El navegador puede modificar precio o nombre. Pedidos consulta el valor vigente en Productos para calcular el total y rechaza precios desactualizados.

**¿Validar stock significa que ya se descontó?**
No. Hoy se comprueba que la cantidad solicitada no supere la disponible en ese momento; no se modifica ni reserva el inventario.

**¿Qué significan 401 y 403?**
401 indica que falta un token válido; 403 que la identidad autenticada no tiene permiso para esa operación.

**¿Se cobró dinero en la prueba?**
No. Solo se registró una boleta de prueba; no existe pasarela de pago en este flujo.

## Referencias del código

- [MS-gateway/README.md](../MS-gateway/README.md)
- [MS-pedidos/README.md](../MS-pedidos/README.md)
- [MS-seguridad/README.md](../MS_gateway_security/README.md)
- [MS-usuarios/README.md](../MS-usuarios/README.md)
- [MS-productos/README.md](../MS-productos/README.md)
- [README del frontend](../../Gmoon-tienda-gamer/frontend/README.md)
- [Filtro JWT del Gateway](../MS-gateway/src/main/java/com/example/MS_gateway/filter/JwtAuthFilter.java)
- [Servicio de compras/boletas](../MS-pedidos/src/main/java/com/example/MS_pedidos/service/PedidoService.java)
- [Migración de nombres Boleta](../MS-pedidos/src/main/resources/db/migration/V3__renombrar_pedido_a_boleta.sql)
