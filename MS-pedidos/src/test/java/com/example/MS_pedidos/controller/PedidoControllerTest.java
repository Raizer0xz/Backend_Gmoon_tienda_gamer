package com.example.MS_pedidos.controller;

import com.example.MS_pedidos.dto.CrearDetallePedidoRequest;
import com.example.MS_pedidos.dto.CrearPedidoRequest;
import com.example.MS_pedidos.model.DetallePedido;
import com.example.MS_pedidos.model.EstadoPedido;
import com.example.MS_pedidos.model.Pedido;
import com.example.MS_pedidos.model.TipoComprobante;
import com.example.MS_pedidos.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(PedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService service;

    @Test
    void deberiaRetornarTodosLosPedidos() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setUsuarioId(5L);
        pedido.setSucursalId(2L);
        pedido.setDireccionEntrega("Av. Siempreviva 742");
        pedido.setTotal(new BigDecimal("1550.50"));
        pedido.setEstado(EstadoPedido.PENDIENTE);

        when(service.findAll()).thenReturn(List.of(pedido));

        // El controller devuelve CollectionModel (HAL JSON),
        // los pedidos van dentro de "_embedded.pedidoList"
        mockMvc.perform(get("/api/v1/pedidos")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.pedidoList[0].id").value(1))
                .andExpect(jsonPath("$._embedded.pedidoList[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$._embedded.pedidoList[0]._links.todos.href").exists())
                .andExpect(jsonPath("$._embedded.pedidoList[0]._links.update.href").exists())
                .andExpect(jsonPath("$._embedded.pedidoList[0]._links.delete.href").exists())
                .andExpect(jsonPath("$._links.self.href").exists());

        verify(service).findAll();
    }

    @Test
    void deberiaCrearPedido() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setUsuarioId(42L);
        pedido.setNumeroPedido("GM-test");
        pedido.setClienteNombre("Cliente Gmoon");
        pedido.setEmail("cliente@example.com");
        pedido.setTipoComprobante(TipoComprobante.BOLETA);
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setTotal(new BigDecimal("159980.00"));
        DetallePedido item = new DetallePedido();
        item.setProductoId("fc-27-ps5");
        item.setNombre("FC 2027 PS5");
        item.setCantidad(2);
        item.setPrecioUnitario(new BigDecimal("79990.00"));
        item.setPedido(pedido);
        pedido.setItems(new ArrayList<>(List.of(item)));

        when(service.crearPedido(eq(42L), any(CrearPedidoRequest.class))).thenReturn(pedido);

        String json = """
                {
                    "clienteNombre": "Cliente Gmoon",
                    "email": "cliente@example.com",
                    "tipoComprobante": "boleta",
                    "usuarioId": 999,
                    "total": 1,
                    "estado": "CANCELADO",
                    "items": [
                        {
                            "id": "fc-27-ps5",
                            "nombre": "FC 2027 PS5",
                            "cantidad": 2,
                            "precioUnitario": 79990.00
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/boletas")
                        .contentType("application/json")
                        .accept(MediaTypes.HAL_JSON)
                        .header("X-Usuario-Id", "42")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.usuarioId").value(42))
                .andExpect(jsonPath("$.numeroPedido").value("GM-test"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tipoComprobante").value("boleta"))
                .andExpect(jsonPath("$.total").value(159980.00))
                .andExpect(jsonPath("$.items[0].id").value("fc-27-ps5"))
                .andExpect(jsonPath("$.items[0].nombre").value("FC 2027 PS5"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$._links.todos.href").exists())
                .andExpect(jsonPath("$._links.update.href").exists())
                .andExpect(jsonPath("$._links.delete.href").exists());

        verify(service).crearPedido(eq(42L), any(CrearPedidoRequest.class));
    }

    @Test
    void deberiaRetornar400CuandoFaltanCamposObligatorios() throws Exception {
        String json = """
                {
                    "clienteNombre": "Cliente Gmoon",
                    "email": "cliente@example.com",
                    "tipoComprobante": "boleta",
                    "items": [
                        {
                            "id": "fc-27-ps5",
                            "nombre": "FC 2027 PS5",
                            "cantidad": 0,
                            "precioUnitario": 79990
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/boletas")
                        .contentType("application/json")
                        .header("X-Usuario-Id", "42")
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deberiaActualizarEstadoDePedido() throws Exception {
        Pedido pedidoActualizado = new Pedido();
        pedidoActualizado.setId(1L);
        pedidoActualizado.setUsuarioId(1L);
        pedidoActualizado.setSucursalId(10L);
        pedidoActualizado.setEstado(EstadoPedido.CONFIRMADO);
        pedidoActualizado.setTotal(new BigDecimal("1550.50"));
        pedidoActualizado.setDireccionEntrega("Av. Siempreviva 742");

        when(service.update(eq(1L), eq(EstadoPedido.CONFIRMADO)))
                .thenReturn(Optional.of(pedidoActualizado));

        mockMvc.perform(put("/api/v1/pedidos/1/estado")
                        .param("nuevoEstado", "CONFIRMADO")
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$._links.todos.href").exists())
                .andExpect(jsonPath("$._links.update.href").exists())
                .andExpect(jsonPath("$._links.delete.href").exists());

        verify(service).update(1L, EstadoPedido.CONFIRMADO);
    }
}