package com.example.MS_usuarios.service;

import com.example.MS_usuarios.model.Usuario;
import com.example.MS_usuarios.repository.RepositoryUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ServiceUsuario {

    @Autowired
    private RepositoryUsuario repository;

    public List<Usuario> findAll() {
        return repository.findAll();
    }

    public Optional<Usuario> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<Usuario> findByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email));
    }

    public List<Usuario> findByRol(String rol) {
        return repository.findByRol(rol);
    }

    public boolean existsByEmail(String email) {
        return repository.existsByEmail(normalizeEmail(email));
    }

    public Usuario save(Usuario usuario) {
        return repository.save(usuario);
    }

    // -------------------------------------------------------------------------
    // REGLA DE NEGOCIO: crear usuario validando email duplicado
    // Antes esta lógica estaba en UsuarioController — ahora vive aquí
    // -------------------------------------------------------------------------
    public Usuario crearUsuario(Usuario usuario) {
        return crearUsuario(usuario, null);
    }

    public Usuario crearUsuario(Usuario usuario, String rolActor) {
        String email = normalizeEmail(usuario.getEmail());
        if (repository.existsByEmail(email)) {
            throw new IllegalStateException("Ya existe un usuario con ese email");
        }
        usuario.setEmail(email);
        if (rolActor == null || rolActor.isBlank()) {
            usuario.setRol("CLIENTE");
        } else if ("ADMIN".equalsIgnoreCase(rolActor)) {
            usuario.setRol(usuario.getRol() == null || usuario.getRol().isBlank()
                    ? "CLIENTE"
                    : normalizeRole(usuario.getRol()));
        } else {
            throw new SecurityException("Solo ADMIN puede asignar roles al crear usuarios");
        }
        return repository.save(usuario);
    }

    public Optional<Usuario> actualizarUsuario(Long id, Usuario datosNuevos) {
        return repository.findById(id).map(existente -> {
            String email = normalizeEmail(datosNuevos.getEmail());
            if (!email.equals(normalizeEmail(existente.getEmail())) && repository.existsByEmail(email)) {
                throw new IllegalStateException("Ya existe un usuario con ese email");
            }

            existente.setNombre(datosNuevos.getNombre());
            existente.setEmail(email);
            existente.setTelefono(datosNuevos.getTelefono());
            existente.setRol(normalizeRole(datosNuevos.getRol()));
            return repository.save(existente);
        });
    }

    public boolean deleteById(Long id) {
        return repository.findById(id).map(usuario -> {
            repository.delete(usuario);
            return true;
        }).orElse(false);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CLIENTE", "VENDEDOR", "ADMIN").contains(normalizedRole)) {
            throw new IllegalArgumentException("El rol debe ser CLIENTE, VENDEDOR o ADMIN");
        }
        return normalizedRole;
    }
}