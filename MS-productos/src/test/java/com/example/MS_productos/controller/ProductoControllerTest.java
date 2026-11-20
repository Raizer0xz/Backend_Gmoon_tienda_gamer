package com.example.MS_productos.controller;

import com.example.MS_productos.model.Categoria;
import com.example.MS_productos.model.Producto;
import com.example.MS_productos.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // FIX: era "ProductoServiceTest" — se corrige a "ProductoService"
    @MockitoBean
    private ProductoService service;

    private Categoria categoriaEjemplo() {
        return Categoria.builder()
                .id(1L)
                .nombre("Videojuegos Deportes")
                .grupo("Videojuegos")
                .descripcion("Juegos deportivos")
                .build();
    }

    private Producto productoEjemplo() {
        return Producto.builder()
                .id(1L)
                .nombre("FC 2027 PS5")
                .descripcion("Juego deportivo para PS5")
                .precio(new BigDecimal("79990.00"))
                .stock(4)
                .descuento(0)
                .imagenUrl("/img/Juegos_Deportes/FC27_PS5.png")
                .activo(true)
                .categoria(categoriaEjemplo())
                .build();
    }

    @Test
    void deberiaRetornarTodosLosProductosActivos() throws Exception {
        when(service.listarProductos()).thenReturn(List.of(productoEjemplo()));

        mockMvc.perform(get("/api/v2/productos").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.productoList[0].id").value(1))
                .andExpect(jsonPath("$._embedded.productoList[0].nombre").value("FC 2027 PS5"))
                .andExpect(jsonPath("$._embedded.productoList[0].precio").value(79990.00))
                .andExpect(jsonPath("$._embedded.productoList[0].stock").value(4))
                .andExpect(jsonPath("$._embedded.productoList[0].descuento").value(0))
                .andExpect(jsonPath("$._embedded.productoList[0].categoria.grupo").value("Videojuegos"))
                .andExpect(jsonPath("$._embedded.productoList[0].activo").value(true))
                .andExpect(jsonPath("$._embedded.productoList[0]._links.self.href").exists())
                .andExpect(jsonPath("$._embedded.productoList[0]._links.todos.href").exists())
                .andExpect(jsonPath("$._embedded.productoList[0]._links.update.href").exists())
                .andExpect(jsonPath("$._embedded.productoList[0]._links.desactivar.href").exists())
                .andExpect(jsonPath("$._links.self.href").exists());

        verify(service).listarProductos();
    }

    @Test
    void deberiaRetornar204CuandoNoHayProductos() throws Exception {
        when(service.listarProductos()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/productos"))
                .andExpect(status().isNoContent());

        verify(service).listarProductos();
    }

    @Test
    void deberiaRetornarProductoPorId() throws Exception {
        when(service.obtenerPorId(1L)).thenReturn(productoEjemplo());

        mockMvc.perform(get("/api/v2/productos/1").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("FC 2027 PS5"))
                .andExpect(jsonPath("$.precio").value(79990.00))
                .andExpect(jsonPath("$.stock").value(4))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.todos.href").exists())
                .andExpect(jsonPath("$._links.update.href").exists())
                .andExpect(jsonPath("$._links.desactivar.href").exists())
                .andExpect(jsonPath("$._links.categoria.href").exists());

        verify(service).obtenerPorId(1L);
    }

    @Test
    void deberiaRetornar404CuandoProductoNoExiste() throws Exception {
        when(service.obtenerPorId(99L))
                .thenThrow(new RuntimeException("Producto no encontrado: 99"));

        mockMvc.perform(get("/api/v2/productos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado: 99"));

        verify(service).obtenerPorId(99L);
    }

    @Test
    void deberiaRetornarProductosPorCategoria() throws Exception {
        when(service.listarPorCategoria(1L)).thenReturn(List.of(productoEjemplo()));

        mockMvc.perform(get("/api/v2/productos/categoria/1").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.productoList[0].id").value(1))
                .andExpect(jsonPath("$._embedded.productoList[0]._links.self.href").exists())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.todos.href").exists());

        verify(service).listarPorCategoria(1L);
    }

    @Test
    void deberiaRetornar404CuandoCategoriaNoExiste() throws Exception {
        when(service.listarPorCategoria(99L))
                .thenThrow(new RuntimeException("Categoría no encontrada: 99"));

        mockMvc.perform(get("/api/v2/productos/categoria/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Categoría no encontrada: 99"));

        verify(service).listarPorCategoria(99L);
    }

    @Test
    void deberiaBuscarProductosPorNombre() throws Exception {
        when(service.buscarPorNombre("leche")).thenReturn(List.of(productoEjemplo()));

        mockMvc.perform(get("/api/v2/productos/buscar")
                        .param("nombre", "leche")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.productoList[0].nombre").value("FC 2027 PS5"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.todos.href").exists());

        verify(service).buscarPorNombre("leche");
    }

    @Test
    void deberiaCrearProducto() throws Exception {
        when(service.crearProducto(any(Producto.class))).thenReturn(productoEjemplo());

        String json = """
                {
                    "nombre": "FC 2027 PS5",
                    "descripcion": "Juego deportivo para PS5",
                    "precio": 79990,
                    "stock": 4,
                    "descuento": 0,
                    "imagenUrl": "/img/Juegos_Deportes/FC27_PS5.png",
                    "categoria": { "id": 1 }
                }
                """;

        mockMvc.perform(post("/api/v2/productos")
                        .contentType("application/json")
                        .accept(MediaTypes.HAL_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("FC 2027 PS5"))
                .andExpect(jsonPath("$.precio").value(79990.00))
                .andExpect(jsonPath("$.stock").value(4))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.todos.href").exists())
                .andExpect(jsonPath("$._links.update.href").exists())
                .andExpect(jsonPath("$._links.desactivar.href").exists());

        verify(service).crearProducto(any(Producto.class));
    }

    @Test
    void deberiaRetornar400CuandoFaltanCamposObligatorios() throws Exception {
        String json = """
                {
                    "descripcion": "sin nombre"
                }
                """;

        mockMvc.perform(post("/api/v2/productos")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(service, never()).crearProducto(any());
    }

    @Test
    void deberiaActualizarProducto() throws Exception {
        Producto actualizado = Producto.builder()
                .id(1L)
                .nombre("FC 2026 PS5")
                .descripcion("Juego deportivo PS5")
                .precio(new BigDecimal("49990.00"))
                .stock(8)
                .descuento(15)
                .imagenUrl("/img/Juegos_Deportes/FC26_PS5.png")
                .activo(true)
                .categoria(categoriaEjemplo())
                .build();

        when(service.actualizarProducto(eq(1L), any(Producto.class))).thenReturn(actualizado);

        String json = """
                {
                    "nombre": "FC 2026 PS5",
                    "descripcion": "Juego deportivo PS5",
                    "precio": 49990,
                    "stock": 8,
                    "descuento": 15,
                    "imagenUrl": "/img/Juegos_Deportes/FC26_PS5.png",
                    "categoria": { "id": 1 }
                }
                """;

        mockMvc.perform(put("/api/v2/productos/1")
                        .contentType("application/json")
                        .accept(MediaTypes.HAL_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("FC 2026 PS5"))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.precio").value(49990.00))
                .andExpect(jsonPath("$.descuento").value(15))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.update.href").exists())
                .andExpect(jsonPath("$._links.desactivar.href").exists());

        verify(service).actualizarProducto(eq(1L), any(Producto.class));
    }

    @Test
    void deberiaRetornar404AlActualizarProductoInexistente() throws Exception {
        when(service.actualizarProducto(eq(99L), any(Producto.class)))
                .thenThrow(new RuntimeException("Producto no encontrado: 99"));

        String json = """
                {
                    "nombre": "X",
                    "precio": 1,
                    "stock": 0,
                    "descuento": 0,
                    "categoria": { "id": 1 }
                }
                """;

        mockMvc.perform(put("/api/v2/productos/99")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado: 99"));
    }

    @Test
    void deberiaDesactivarProducto() throws Exception {
        doNothing().when(service).desactivarProducto(1L);

        mockMvc.perform(delete("/api/v2/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Producto desactivado correctamente"));

        verify(service).desactivarProducto(1L);
    }

    @Test
    void deberiaRetornar404AlDesactivarProductoInexistente() throws Exception {
        doThrow(new RuntimeException("Producto no encontrado: 99"))
                .when(service).desactivarProducto(99L);

        mockMvc.perform(delete("/api/v2/productos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Producto no encontrado: 99"));

        verify(service).desactivarProducto(99L);
    }
}