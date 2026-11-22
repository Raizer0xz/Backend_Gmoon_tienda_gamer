# MS-seguridad

Microservicio de credenciales y JWT para Gmoon. La contraseña se almacena con BCrypt; el token contiene `usuarioId`, correo como `sub` y rol (`CLIENTE`, `VENDEDOR` o `ADMIN`).

## Flujo de registro

1. Crear primero el perfil en `MS-usuarios` (`POST /api/v1/usuarios`). El alta pública crea perfiles `CLIENTE`.
2. Crear las credenciales con `POST /auth/registrar`:

```json
{
  "usuarioId": 12,
  "email": "cliente@example.com",
  "password": "una-clave-segura"
}
```

Antes de guardar la credencial, este servicio consulta el perfil por Eureka y comprueba que ID, correo y rol coincidan. Si `MS-usuarios` no está disponible, el registro devuelve `503`; no crea credenciales sin validar el perfil.

El endpoint `POST /auth/registrar-privilegiado` permite `VENDEDOR` o `ADMIN` y requiere un JWT de `ADMIN`. El perfil de usuario con el mismo rol debe existir antes de registrar sus credenciales.

## Configuración

Define `JWT_SECRET` en el entorno de ejecución; debe contener al menos 32 bytes para HS256 y ser idéntico al secreto del gateway. No guardes el valor real en el repositorio. La aplicación local usa `classpath:db.migration`, que coincide con la carpeta de migraciones existente.

El servicio no crea una cuenta ADMIN por defecto. Antes del bootstrap, comprueba que el correo nuevo no exista en `db_usuarios` y que no haya credenciales ADMIN activas en `db_seguridad`:

```sql
SELECT id, nombre, email, rol FROM db_usuarios.usuarios
WHERE LOWER(email) = 'admingmoon@gmoon.cl';

SELECT usuario_id, email, rol FROM db_seguridad.credenciales
WHERE UPPER(rol) = 'ADMIN' AND activo = TRUE;
```

El bootstrap crea un perfil nuevo con rol `ADMIN`; no cambia perfiles existentes. Seguridad rechazará la creación de credenciales si ya hay un ADMIN activo.

Desde `MS-usuarios`, configura el nombre y correo y arráncalo temporalmente:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_NAME = "ADMINISTRADOR"
$env:ADMIN_BOOTSTRAP_EMAIL = "AdminGmoon@gmoon.cl"
$env:SERVER_PORT = "0"
$env:EUREKA_CLIENT_REGISTER_WITH_EUREKA = "false"
$env:EUREKA_CLIENT_FETCH_REGISTRY = "false"
.\mvnw.cmd spring-boot:run
```

Anota el ID que aparece en el log. Detén esa instancia con `Ctrl+C`, elimina las variables y vuelve a arrancar `MS-usuarios` normalmente:

```powershell
Remove-Item Env:ADMIN_BOOTSTRAP_ENABLED, Env:ADMIN_BOOTSTRAP_NAME, Env:ADMIN_BOOTSTRAP_EMAIL, Env:SERVER_PORT, Env:EUREKA_CLIENT_REGISTER_WITH_EUREKA, Env:EUREKA_CLIENT_FETCH_REGISTRY
```

Después, desde `MS_gateway_security`, ejecuta el bootstrap de credenciales usando ese ID. El proceso valida que el perfil coincida en ID, correo y rol, y se detiene si ya hay un ADMIN activo:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_USER_ID = "ID_MOSTRADO_EN_EL_LOG"
$env:ADMIN_BOOTSTRAP_EMAIL = "AdminGmoon@gmoon.cl"
$env:SERVER_PORT = "0"
$env:EUREKA_CLIENT_REGISTER_WITH_EUREKA = "false"
$env:EUREKA_CLIENT_FETCH_REGISTRY = "true"
$secureJwtSecret = Read-Host "JWT_SECRET actual del Gateway y Seguridad" -AsSecureString
$jwtSecretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureJwtSecret)
try {
  $env:JWT_SECRET = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($jwtSecretPointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($jwtSecretPointer)
}
$securePassword = Read-Host "Contraseña temporal del administrador" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
  $env:ADMIN_BOOTSTRAP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
.\mvnw.cmd spring-boot:run
```

El proceso de Seguridad debe ejecutarse en una terminal temporal con Eureka y `MS-usuarios` disponibles. Al iniciar, crea las credenciales y valida el perfil. Detén esa instancia con `Ctrl+C`, elimina las variables de entorno y vuelve a iniciar Seguridad normalmente:

```powershell
Remove-Item Env:ADMIN_BOOTSTRAP_ENABLED, Env:ADMIN_BOOTSTRAP_USER_ID, Env:ADMIN_BOOTSTRAP_EMAIL, Env:ADMIN_BOOTSTRAP_PASSWORD, Env:JWT_SECRET, Env:SERVER_PORT, Env:EUREKA_CLIENT_REGISTER_WITH_EUREKA, Env:EUREKA_CLIENT_FETCH_REGISTRY
```

La opción permanece desactivada por defecto en ambos servicios. No guardes estas variables ni una contraseña fija en el repositorio. Usa HTTPS y configura `REFRESH_COOKIE_SECURE=true` fuera del entorno local.

## API y pruebas

El gateway expone `/auth/**`. `POST /auth/login` recibe correo y contraseña; el login es insensible a mayúsculas en el correo. El JWT de acceso dura 15 minutos. La sesión se renueva por `POST /auth/refresh` usando una cookie `HttpOnly`, rotatoria y revocable; `POST /auth/logout` revoca la cookie. `POST /auth/validar` sirve para validar tokens.

Ejecuta `.\mvnw.cmd test` desde esta carpeta. La prueba que carga el contexto completo requiere MySQL disponible según `application.properties`.
