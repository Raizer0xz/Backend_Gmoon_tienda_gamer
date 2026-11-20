package com.example.MS_usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearUsuarioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "Entre 2 y 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email invalido")
    @Size(max = 255, message = "El email no puede superar 255 caracteres")
    private String email;

    @Size(min = 9, max = 15, message = "Telefono entre 9 y 15 caracteres")
    private String telefono;

    @Pattern(regexp = "(?i)CLIENTE|VENDEDOR|ADMIN", message = "El rol debe ser CLIENTE, VENDEDOR o ADMIN")
    private String rol;
}
