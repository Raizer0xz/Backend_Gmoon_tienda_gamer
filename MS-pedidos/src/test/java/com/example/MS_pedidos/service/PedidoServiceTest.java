package com.example.MS_pedidos.service;

import com.example.MS_pedidos.dto.CrearDetallePedidoRequest;
import com.example.MS_pedidos.dto.CrearPedidoRequest;
import com.example.MS_pedidos.model.DetallePedido;
import com.example.MS_pedidos.model.EstadoPedido;
import com.example.MS_pedidos.model.Pedido;
import com.example.MS_pedidos.model.TipoComprobante;
import com.example.MS_pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository repository;

    @InjectMocks
    private PedidoService service;

    @Test
    void deberiaRetornarTodosLosPedidos() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setUsuarioId(5L);
        pedido.setSucursalId(2L);
        pedido.setDireccionEntrega("Av. Siempreviva 742");
        pedido.setTotal(new BigDecimal("1550.50"));
        pedido.setEstado(EstadoPedido.PENDIENTE);

        Mockito.when(repository.findAll())
                .thenReturn(List.of(pedido));

        List<Pedido> resultado = service.findAll();

        assertFalse(resultado.isEmpty());
        assertEquals(1L, resultado.get(0).getId());
        assertEquals(EstadoPedido.PENDIENTE, resultado.get(0).getEstado());

        verify(repository).findAll();
    }

    @Test
    void deberiaCrearPedidoGmoonYCalcularElTotalDesdeSusItems() {
        CrearPedidoRequest request = new CrearPedidoRequest(
                " Cliente Gmoon ",
                "CLIENTE@EXAMPLE.COM ",
                TipoComprobante.FACTURA,
                List.of(
                        new CrearDetallePedidoRequest("fc-27-ps5", "FC 2027 PS5", 2, new BigDecimal("79990.00")),
                        new CrearDetallePedidoRequest("mando-ps5", "Control PS5", 1, new BigDecimal("49990.00"))
                )
        );
        Mockito.when(repository.save(any(Pedido.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Pedido resultado = service.crearPedido(42L, request);

        assertThat(resultado.getUsuarioId()).isEqualTo(42L);
        assertThat(resultado.getClienteNombre()).isEqualTo("Cliente Gmoon");
        assertThat(resultado.getEmail()).isEqualTo("cliente@example.com");
        assertThat(resultado.getTipoComprobante()).isEqualTo(TipoComprobante.FACTURA);
        assertThat(resultado.getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
        assertThat(resultado.getNumeroPedido()).startsWith("GM-");
        assertThat(resultado.getTotal()).isEqualByComparingTo("209970.00");
        assertThat(resultado.getItems()).hasSize(2);
        assertThat(resultado.getItems()).allSatisfy(item -> assertThat(item.getPedido()).isSameAs(resultado));

        verify(repository).save(resultado);
    }

    @Test
    void deberiaRechazarTotalesQueExcedanLaPrecisionDeLaBaseDeDatos() {
        CrearPedidoRequest request = new CrearPedidoRequest(
                "Cliente Gmoon",
                "cliente@example.com",
                TipoComprobante.BOLETA,
                List.of(new CrearDetallePedidoRequest(
                        "producto-caro",
                        "Producto",
                        2,
                        new BigDecimal("9999999999.99")
                ))
        );

        assertThatThrownBy(() -> service.crearPedido(42L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El total excede el máximo permitido");

        verify(repository, never()).save(any(Pedido.class));
    }

    @Test
    void deberiaActualizarEstadoDePedido() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setUsuarioId(5L);
        pedido.setSucursalId(2L);
        pedido.setDireccionEntrega("Av. Siempreviva 742");
        pedido.setTotal(new BigDecimal("1550.50"));

        Mockito.when(repository.findById(1L))
                .thenReturn(Optional.of(pedido));

        Pedido pedidoActualizado = new Pedido();
        pedidoActualizado.setId(1L);
        pedidoActualizado.setEstado(EstadoPedido.CONFIRMADO);
        pedidoActualizado.setUsuarioId(5L);
        pedidoActualizado.setSucursalId(2L);
        pedidoActualizado.setDireccionEntrega("Av. Siempreviva 742");
        pedidoActualizado.setTotal(new BigDecimal("1550.50"));

        Mockito.when(repository.save(pedido))
                .thenReturn(pedidoActualizado);

        Optional<Pedido> resultado = service.update(1L, EstadoPedido.CONFIRMADO);

        assertTrue(resultado.isPresent());
        assertEquals(EstadoPedido.CONFIRMADO, resultado.get().getEstado());

        verify(repository).findById(1L);
    }

}