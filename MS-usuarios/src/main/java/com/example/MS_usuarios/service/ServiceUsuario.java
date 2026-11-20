package com.example.MS_usuarios.service;

import com.example.MS_usuarios.model.Usuario;
import com.example.MS_usuarios.repository.RepositoryUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return repository.findByEmail(email);
    }

    public List<Usuario> findByRol(String rol) {
        return repository.findByRol(rol);
    }

    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
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
        if (repository.existsByEmail(usuario.getEmail())) {
            throw new IllegalStateException("Ya existe un usuario con ese email");
        }
        if (rolActor == null || rolActor.isBlank()) {
            usuario.setRol("CLIENTE");
        } else if ("ADMIN".equals(rolActor)) {
            if (!List.of("CLIENTE", "VENDEDOR", "ADMIN").contains(usuario.getRol())) {
                throw new IllegalArgumentException("El rol debe ser CLIENTE, VENDEDOR o ADMIN");
            }
        } else {
            throw new SecurityException("Solo ADMIN puede asignar roles al crear usuarios");
        }
        return repository.save(usuario);
    }

    public boolean deleteById(Long id) {
        return repository.findById(id).map(usuario -> {
            repository.delete(usuario);
            return true;
        }).orElse(false);
    }
}