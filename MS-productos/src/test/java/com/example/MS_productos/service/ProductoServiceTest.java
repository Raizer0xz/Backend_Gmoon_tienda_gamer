package com.example.MS_productos.service;

import com.example.MS_productos.model.Categoria;
import com.example.MS_productos.model.Producto;
import com.example.MS_productos.repository.CategoriaRepository;
import com.example.MS_productos.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProductoService productoService;

    private Categoria categoria;
    private Producto producto;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .id(1L)
                .nombre("Videojuegos Deportes")
                .grupo("Videojuegos")
                .descripcion("Juegos deportivos")
                .activo(true)
                .build();

        producto = Producto.builder()
                .id(1L)
                .nombre("FC 2027 PS5")
                .descripcion("Juego deportivo para PS5")
                .precio(new BigDecimal("79990.00"))
                .stock(4)
                .descuento(0)
                .imagenUrl("/img/Juegos_Deportes/FC27_PS5.png")
                .activo(true)
                .categoria(categoria)
                .build();
    }

    // -------------------------------------------------------------------------
    // listarProductos()
    // -------------------------------------------------------------------------
    @Test
    void listarProductos_deberiaRetornarProductosActivos() {
        when(productoRepository.findByActivoTrueAndCategoriaActivoTrue()).thenReturn(List.of(producto));

        List<Producto> resultado = productoService.listarProductos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("FC 2027 PS5");
        assertThat(resultado.get(0).getPrecio()).isEqualByComparingTo("79990.00");
        assertThat(resultado.get(0).getStock()).isEqualTo(4);
        assertThat(resultado.get(0).getActivo()).isTrue();
        verify(productoRepository).findByActivoTrueAndCategoriaActivoTrue();
    }

    @Test
    void listarProductos_deberiaRetornarListaVaciaCuandoNoHayActivos() {
        when(productoRepository.findByActivoTrueAndCategoriaActivoTrue()).thenReturn(List.of());

        List<Producto> resultado = productoService.listarProductos();

        assertThat(resultado).isEmpty();
        verify(productoRepository).findByActivoTrueAndCategoriaActivoTrue();
    }

    // -------------------------------------------------------------------------
    // obtenerPorId()
    // -------------------------------------------------------------------------
    @Test
    void obtenerPorId_deberiaRetornarProductoCuandoExiste() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        Producto resultado = productoService.obtenerPorId(1L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getNombre()).isEqualTo("FC 2027 PS5");
        verify(productoRepository).findById(1L);
    }

    @Test
    void obtenerPorId_deberiaLanzarExcepcionCuandoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.obtenerPorId(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");

        verify(productoRepository).findById(99L);
    }

    // -------------------------------------------------------------------------
    // listarPorCategoria()
    // -------------------------------------------------------------------------
    @Test
    void listarPorCategoria_deberiaRetornarProductosDeLaCategoria() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.findByCategoriaIdAndActivoTrueAndCategoriaActivoTrue(1L))
                .thenReturn(List.of(producto));

        List<Producto> resultado = productoService.listarPorCategoria(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCategoria().getId()).isEqualTo(1L);
        verify(categoriaRepository).findById(1L);
        verify(productoRepository).findByCategoriaIdAndActivoTrueAndCategoriaActivoTrue(1L);
    }

    @Test
    void listarPorCategoria_deberiaLanzarExcepcionCuandoCategoriaNoExiste() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.listarPorCategoria(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Categoría no encontrada: 99");

        verify(categoriaRepository).findById(99L);
        verify(productoRepository, never()).findByCategoriaIdAndActivoTrueAndCategoriaActivoTrue(any());
    }

    // -------------------------------------------------------------------------
    // buscarPorNombre()
    // -------------------------------------------------------------------------
    @Test
    void buscarPorNombre_deberiaRetornarProductosQueCoinciden() {
        when(productoRepository.findByNombreContainingIgnoreCaseAndActivoTrueAndCategoriaActivoTrue("FC"))
                .thenReturn(List.of(producto));

        List<Producto> resultado = productoService.buscarPorNombre("FC");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).containsIgnoringCase("FC");
        verify(productoRepository).findByNombreContainingIgnoreCaseAndActivoTrueAndCategoriaActivoTrue("FC");
    }

    // -------------------------------------------------------------------------
    // crearProducto()
    // -------------------------------------------------------------------------
    @Test
    void crearProducto_deberiaGuardarYRetornarProducto() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenReturn(producto);

        Producto resultado = productoService.crearProducto(producto);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getNombre()).isEqualTo("FC 2027 PS5");
        verify(categoriaRepository).findById(1L);
        verify(productoRepository).save(producto);
    }

    @Test
    void crearProducto_deberiaLanzarExcepcionCuandoCategoriaNoExiste() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.crearProducto(producto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Categoría no encontrada");

        verify(categoriaRepository).findById(1L);
        verify(productoRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // actualizarProducto()
    // -------------------------------------------------------------------------
    @Test
    void actualizarProducto_deberiaActualizarYRetornarProducto() {
        Producto datosNuevos = Producto.builder()
                .id(1L)
                .nombre("FC 2026 PS5")
                .descripcion("Juego deportivo PS5")
                .imagenUrl("/img/Juegos_Deportes/FC26_PS5.png")
                .imagenInicio("/img/Juegos_Deportes/FC26_PS5.png")
                .precio(new BigDecimal("49990.00"))
                .stock(8)
                .descuento(15)
                .categoria(categoria)
                .build();

        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenReturn(datosNuevos);

        Producto resultado = productoService.actualizarProducto(1L, datosNuevos);

        assertThat(resultado.getNombre()).isEqualTo("FC 2026 PS5");
        assertThat(resultado.getPrecio()).isEqualByComparingTo("49990.00");
        assertThat(resultado.getStock()).isEqualTo(8);
        assertThat(resultado.getDescuento()).isEqualTo(15);
        verify(productoRepository).findById(1L);
        verify(categoriaRepository).findById(1L);
        verify(productoRepository).save(any(Producto.class));
    }

    @Test
    void actualizarProducto_deberiaLanzarExcepcionCuandoProductoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizarProducto(99L, producto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");

        verify(productoRepository).findById(99L);
        verify(productoRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // desactivarProducto()
    // -------------------------------------------------------------------------
    @Test
    void desactivarProducto_deberiaPonerActivoEnFalse() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenReturn(producto);

        productoService.desactivarProducto(1L);

        assertThat(producto.getActivo()).isFalse();
        verify(productoRepository).findById(1L);
        verify(productoRepository).save(producto);
    }

    @Test
    void desactivarProducto_deberiaLanzarExcepcionCuandoProductoNoExiste() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.desactivarProducto(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Producto no encontrado: 99");

        verify(productoRepository).findById(99L);
        verify(productoRepository, never()).save(any());
    }
}