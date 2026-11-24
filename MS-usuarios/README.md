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

## Bootstrap inicial de administrador

Para una instalacion local sin perfiles, se puede crear un perfil ADMIN una sola vez. El proceso esta desactivado por defecto y requiere `ADMIN_BOOTSTRAP_ENABLED=true`, `ADMIN_BOOTSTRAP_NAME` y `ADMIN_BOOTSTRAP_EMAIL`. No establece ni almacena contrasenas; las credenciales se crean despues en `MS_gateway_security`.

En PowerShell, desde esta carpeta:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_NAME = "ADMIN Gmoon"
$env:ADMIN_BOOTSTRAP_EMAIL = "admin@example.com"
$env:SERVER_PORT = "0"
$env:EUREKA_CLIENT_REGISTER_WITH_EUREKA = "false"
$env:EUREKA_CLIENT_FETCH_REGISTRY = "false"
.\mvnw.cmd spring-boot:run
```

Anota el ID del perfil que aparece en el log y detén el proceso con `Ctrl+C`. Luego elimina las variables de bootstrap y arranca Usuarios normalmente:

```powershell
Remove-Item Env:ADMIN_BOOTSTRAP_ENABLED, Env:ADMIN_BOOTSTRAP_NAME, Env:ADMIN_BOOTSTRAP_EMAIL, Env:SERVER_PORT, Env:EUREKA_CLIENT_REGISTER_WITH_EUREKA, Env:EUREKA_CLIENT_FETCH_REGISTRY
```

No habilites el bootstrap otra vez para el mismo correo: el servicio rechazara perfiles duplicados.

## Persistencia

Flyway aplica las migraciones de `src/main/resources/db/migration`. La migración V2 elimina la columna antigua `password_hash` y la cuenta de demostración de Market Express. Las contraseñas se almacenan únicamente en el microservicio de seguridad.

La base de datos configurada por defecto es `db_usuarios` en MySQL local. Configura conexión y credenciales de base de datos para el entorno donde se despliegue el servicio.

## Pruebas

```powershell
.\mvnw.cmd test
```
