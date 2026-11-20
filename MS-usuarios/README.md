# MS-usuarios

Microservicio de perfiles de Gmoon. Administra nombre, email, teléfono y rol; las contraseñas y credenciales son responsabilidad de `MS_gateway_security`.

## API

La ruta pública del gateway para registrar un perfil es `POST /api/v1/usuarios`. El cuerpo acepta:

```json
{
  "nombre": "Nombre Apellido",
  "email": "persona@correo.cl",
  "telefono": "912345678"
}
```

El microservicio crea el perfil con rol `CLIENTE`, sin aceptar la asignación de roles desde una solicitud pública. La respuesta incluye el ID generado, necesario para registrar las credenciales en `POST /auth/registrar` del microservicio de seguridad.

Las rutas para listar, consultar, actualizar y eliminar perfiles conservan el prefijo `/api/v1/usuarios`; el gateway las limita a usuarios administradores.

## Persistencia

Flyway aplica las migraciones de `src/main/resources/db/migration`. La migración V2 elimina la columna antigua `password_hash` y la cuenta de demostración de Market Express. Las contraseñas se almacenan únicamente en el microservicio de seguridad.

La base de datos configurada por defecto es `db_usuarios` en MySQL local. Configura conexión y credenciales de base de datos para el entorno donde se despliegue el servicio.

## Pruebas

```powershell
.\mvnw.cmd test
```
