package com.example.MS_productos.service;

import com.example.MS_productos.model.Categoria;
import com.example.MS_productos.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .id(1L)
                .nombre("Videojuegos Deportes")
                .grupo("Videojuegos")
                .descripcion("Juegos deportivos")
                .imagenUrl("https://img.example.com/lacteos.png")
                .activo(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // listarCategorias()
    // -------------------------------------------------------------------------
    @Test
    void listarCategorias_deberiaRetornarCategoriasActivas() {
        when(categoriaRepository.findByActivoTrue()).thenReturn(List.of(categoria));

        List<Categoria> resultado = categoriaService.listarCategorias();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Videojuegos Deportes");
        verify(categoriaRepository).findByActivoTrue();
    }

    @Test
    void listarCategorias_deberiaRetornarListaVaciaCuandoNoHayCategorias() {
        when(categoriaRepository.findByActivoTrue()).thenReturn(List.of());

        List<Categoria> resultado = categoriaService.listarCategorias();

        assertThat(resultado).isEmpty();
        verify(categoriaRepository).findByActivoTrue();
    }

    // -------------------------------------------------------------------------
    // obtenerPorId()
    // -------------------------------------------------------------------------
    @Test
    void obtenerPorId_deberiaRetornarCategoriaCuandoExiste() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

        Categoria resultado = categoriaService.obtenerPorId(1L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getNombre()).isEqualTo("Videojuegos Deportes");
        verify(categoriaRepository).findById(1L);
    }

    @Test
    void obtenerPorId_deberiaLanzarExcepcionCuandoNoExiste() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.obtenerPorId(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Categoría no encontrada: 99");

        verify(categoriaRepository).findById(99L);
    }

    // -------------------------------------------------------------------------
    // crearCategoria()
    // -------------------------------------------------------------------------
    @Test
    void crearCategoria_deberiaGuardarYRetornarCategoria() {
        when(categoriaRepository.save(any(Categoria.class))).thenReturn(categoria);

        Categoria resultado = categoriaService.crearCategoria(categoria);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getNombre()).isEqualTo("Videojuegos Deportes");
        verify(categoriaRepository).save(categoria);
    }

    // -------------------------------------------------------------------------
    // actualizarCategoria()
    // -------------------------------------------------------------------------
    @Test
    void actualizarCategoria_deberiaActualizarYRetornarCategoria() {
        Categoria datosNuevos = Categoria.builder()
                .id(1L)
                .nombre("PC Gamer Pro")
                .grupo("PC Gamer")
                .descripcion("Equipos gamer de alto rendimiento")
                .build();

        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(categoriaRepository.save(any(Categoria.class))).thenReturn(datosNuevos);

        Categoria resultado = categoriaService.actualizarCategoria(1L, datosNuevos);

        assertThat(resultado.getNombre()).isEqualTo("PC Gamer Pro");
        assertThat(resultado.getGrupo()).isEqualTo("PC Gamer");
        assertThat(resultado.getDescripcion()).isEqualTo("Equipos gamer de alto rendimiento");
        verify(categoriaRepository).findById(1L);
        verify(categoriaRepository).save(any(Categoria.class));
    }

    @Test
    void actualizarCategoria_deberiaLanzarExcepcionCuandoCategoriaNoExiste() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.actualizarCategoria(99L, categoria))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Categoría no encontrada: 99");

        verify(categoriaRepository).findById(99L);
        verify(categoriaRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // eliminarCategoria()
    // -------------------------------------------------------------------------
    @Test
    void eliminarCategoria_deberiaDesactivarCuandoExiste() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(categoriaRepository.save(any(Categoria.class))).thenReturn(categoria);

        categoriaService.eliminarCategoria(1L);

        verify(categoriaRepository).findById(1L);
        assertThat(categoria.getActivo()).isFalse();
        verify(categoriaRepository).save(categoria);
    }

    @Test
    void eliminarCategoria_deberiaLanzarExcepcionCuandoCategoriaNoExiste() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.eliminarCategoria(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Categoría no encontrada: 99");

        verify(categoriaRepository).findById(99L);
        verify(categoriaRepository, never()).save(any());
    }
}