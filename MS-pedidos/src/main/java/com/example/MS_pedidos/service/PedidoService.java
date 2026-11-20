package com.example.MS_pedidos.service;

import com.example.MS_pedidos.dto.CrearDetallePedidoRequest;
import com.example.MS_pedidos.dto.CrearPedidoRequest;
import com.example.MS_pedidos.model.EstadoPedido;
import com.example.MS_pedidos.model.DetallePedido;
import com.example.MS_pedidos.model.Pedido;
import com.example.MS_pedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PedidoService {
    private final PedidoRepository repository;

    @Transactional(readOnly = true)
    public List<Pedido> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Pedido> findByUsuario(Long usuarioId) {
        return repository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> findBySucursalIdAndEstado(Long sucursalId, EstadoPedido estado) {
        return repository.findBySucursalIdAndEstado(sucursalId, estado);
    }

    @Transactional
    public Pedido crearPedido(Long usuarioId, CrearPedidoRequest request) {
        Pedido pedido = new Pedido();
        pedido.setUsuarioId(usuarioId);
        pedido.setNumeroPedido(Pedido.generarNumeroPedido());
        pedido.setClienteNombre(request.clienteNombre().trim());
        pedido.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        pedido.setTipoComprobante(request.tipoComprobante());
        pedido.setEstado(EstadoPedido.PENDIENTE);

        BigDecimal total = BigDecimal.ZERO;
        for (CrearDetallePedidoRequest item : request.items()) {
            BigDecimal precioUnitario = item.precioUnitario();
            total = total.add(precioUnitario.multiply(BigDecimal.valueOf(item.cantidad())));

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProductoId(item.id().trim());
            detalle.setNombre(item.nombre().trim());
            detalle.setCantidad(item.cantidad());
            detalle.setPrecioUnitario(precioUnitario);
            pedido.getItems().add(detalle);
        }

        if (total.precision() - total.scale() > 10) {
            throw new IllegalArgumentException("El total excede el máximo permitido");
        }
        pedido.setTotal(total);
        return repository.save(pedido);
    }

    @Transactional
    public Optional<Pedido> update(Long id, EstadoPedido nuevoEstado) {
        return repository.findById(id).map(p -> {
            p.setEstado(nuevoEstado);
            return repository.save(p);
        });
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
