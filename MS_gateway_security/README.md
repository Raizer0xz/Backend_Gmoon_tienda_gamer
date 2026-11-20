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

**Bootstrap pendiente:** no se crea una cuenta ADMIN por defecto. Antes de usar el registro privilegiado en un entorno nuevo, hay que provisionar de forma controlada el primer perfil y sus credenciales ADMIN; no se debe agregar una contraseña fija al código ni a una migración.

## API y pruebas

El gateway expone `/auth/**`. `POST /auth/login` recibe correo y contraseña; el login es insensible a mayúsculas en el correo. `POST /auth/validar` sirve para validar tokens.

Ejecuta `.\mvnw.cmd test` desde esta carpeta. La prueba que carga el contexto completo requiere MySQL disponible según `application.properties`.
