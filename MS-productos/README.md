# MS-productos

Microservicio Spring Boot que administra el catálogo de productos y categorías de Gmoon. El gateway expone las lecturas del catálogo en `/api/v1/productos` y `/api/v1/categorias`, y las reescribe a las rutas internas `/api/v2`.

## Modelo del catálogo

Un producto contiene `nombre`, `descripcion`, `precio`, `stock`, `descuento`, `imagenUrl`, `imagenInicio`, `activo` y una categoría. El precio y el stock pertenecen al producto; no se manejan precios por sucursal.

Una categoría contiene `nombre`, `grupo`, `descripcion`, `imagenUrl` y `activo`. Al desactivar una categoría se conserva la fila para mantener las referencias de sus productos. Los listados omiten las categorías desactivadas y sus productos.

Para crear un producto, envía el identificador de una categoría existente y activa:

```json
{
  "nombre": "FC 2027 PS5",
  "descripcion": "Juego deportivo para PS5",
  "precio": 79990,
  "stock": 4,
  "descuento": 0,
  "imagenUrl": "/img/Juegos_Deportes/FC27_PS5.png",
  "categoria": { "id": 1 }
}
```

## Migración de base de datos

Flyway aplica `V2__adaptar_catalogo_gmoon.sql` después de crear el esquema inicial. La migración agrega los campos del catálogo, asigna a cada producto el precio histórico más reciente (desempate por ID), desactiva los datos de prueba heredados y elimina `unidad_medida` y la tabla `precio_producto`.

La conexión local está configurada para MySQL en `localhost:3307`, base `db_productos`. Ajusta la configuración local si tu instancia usa otros datos.

## Pruebas

Desde esta carpeta, ejecuta las pruebas del módulo:

```powershell
.\mvnw.cmd test
```

La prueba de contexto de Spring requiere que MySQL esté disponible y que Flyway pueda aplicar las migraciones.
