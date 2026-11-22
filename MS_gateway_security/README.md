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

El servicio no crea una cuenta ADMIN por defecto. Para un entorno nuevo se puede habilitar un único bootstrap de ejecución, siempre que el perfil ADMIN ya exista en `MS-usuarios`. El proceso valida ID, correo y rol, y se detiene si ya hay un ADMIN activo:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_USER_ID = "12"
$env:ADMIN_BOOTSTRAP_EMAIL = "admin@example.com"
$securePassword = Read-Host "Contraseña temporal del administrador" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
  $env:ADMIN_BOOTSTRAP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
.\mvnw.cmd spring-boot:run
```

El proceso debe ejecutarse en una terminal temporal: al iniciar, crea las credenciales y valida el perfil en `MS-usuarios`. Cierra esa instancia y borra las cuatro variables de entorno antes de iniciar el servicio normal. La opción permanece desactivada por defecto. No guardes estas variables ni una contraseña fija en el repositorio. Usa HTTPS y configura `REFRESH_COOKIE_SECURE=true` fuera del entorno local.

## API y pruebas

El gateway expone `/auth/**`. `POST /auth/login` recibe correo y contraseña; el login es insensible a mayúsculas en el correo. El JWT de acceso dura 15 minutos. La sesión se renueva por `POST /auth/refresh` usando una cookie `HttpOnly`, rotatoria y revocable; `POST /auth/logout` revoca la cookie. `POST /auth/validar` sirve para validar tokens.

Ejecuta `.\mvnw.cmd test` desde esta carpeta. La prueba que carga el contexto completo requiere MySQL disponible según `application.properties`.
