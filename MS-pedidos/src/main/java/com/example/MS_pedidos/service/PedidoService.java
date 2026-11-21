package com.example.MS_pedidos.service;

import com.example.MS_pedidos.client.ProductoClient;
import com.example.MS_pedidos.dto.CrearDetallePedidoRequest;
import com.example.MS_pedidos.dto.CrearPedidoRequest;
import com.example.MS_pedidos.dto.ProductoCatalogoResponse;
import com.example.MS_pedidos.model.EstadoPedido;
import com.example.MS_pedidos.model.DetallePedido;
import com.example.MS_pedidos.model.Pedido;
import com.example.MS_pedidos.repository.PedidoRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PedidoService {
    private final PedidoRepository repository;
    private final ProductoClient productoClient;

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

    public Pedido crearPedido(Long usuarioId, CrearPedidoRequest request) {
        Map<Long, ProductoCatalogoResponse> productos = validarCatalogo(request.items());
        Pedido pedido = new Pedido();
        pedido.setUsuarioId(usuarioId);
        pedido.setNumeroPedido(Pedido.generarNumeroPedido());
        pedido.setClienteNombre(request.clienteNombre().trim());
        pedido.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        pedido.setTipoComprobante(request.tipoComprobante());
        pedido.setEstado(EstadoPedido.PENDIENTE);

        BigDecimal total = BigDecimal.ZERO;
        for (CrearDetallePedidoRequest item : request.items()) {
            Long productoId = Long.valueOf(item.id().trim());
            ProductoCatalogoResponse producto = productos.get(productoId);
            BigDecimal precioUnitario = obtenerPrecioVigente(producto);
            if (item.precioUnitario() == null || item.precioUnitario().compareTo(precioUnitario) != 0) {
                throw new IllegalArgumentException(
                        "El precio de " + producto.nombre() + " cambió. Actualiza el carrito antes de comprar"
                );
            }
            total = total.add(precioUnitario.multiply(BigDecimal.valueOf(item.cantidad())));

            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProductoId(productoId.toString());
            detalle.setNombre(producto.nombre());
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

    private Map<Long, ProductoCatalogoResponse> validarCatalogo(List<CrearDetallePedidoRequest> items) {
        Map<Long, Integer> cantidadesPorProducto = new HashMap<>();
        for (CrearDetallePedidoRequest item : items) {
            long productoId;
            try {
                productoId = Long.parseLong(item.id().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("El ID del producto debe ser un ID numérico del catálogo");
            }
            if (productoId <= 0 || item.cantidad() == null || item.cantidad() <= 0) {
                throw new IllegalArgumentException("El producto y la cantidad deben ser válidos");
            }
            try {
                cantidadesPorProducto.merge(productoId, item.cantidad(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("La cantidad solicitada excede el máximo permitido");
            }
        }

        Map<Long, ProductoCatalogoResponse> productos = new HashMap<>();
        for (Map.Entry<Long, Integer> entrada : cantidadesPorProducto.entrySet()) {
            ProductoCatalogoResponse producto;
            try {
                producto = productoClient.obtenerPorId(entrada.getKey());
            } catch (FeignException.NotFound exception) {
                throw new IllegalArgumentException("El producto " + entrada.getKey() + " no existe en el catálogo");
            } catch (FeignException exception) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo validar el producto en el catálogo"
                );
            }

            if (producto == null
                    || producto.id() == null
                    || producto.precio() == null
                    || producto.stock() == null
                    || producto.nombre() == null
                    || producto.nombre().isBlank()
                    || producto.descuento() == null
                    || producto.categoria() == null
                    || producto.precio().signum() < 0
                    || producto.stock() < 0
                    || producto.descuento() < 0
                    || producto.descuento() > 100) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "El catálogo devolvió datos de producto inválidos"
                );
            }
            if (!Boolean.TRUE.equals(producto.activo()) || !Boolean.TRUE.equals(producto.categoria().activo())) {
                throw new IllegalArgumentException("El producto " + entrada.getKey() + " no está disponible");
            }
            if (producto.stock() < entrada.getValue()) {
                throw new IllegalArgumentException(
                        "Stock insuficiente para " + producto.nombre() + ". Disponibles: " + producto.stock()
                );
            }
            productos.put(entrada.getKey(), producto);
        }
        return productos;
    }

    private BigDecimal obtenerPrecioVigente(ProductoCatalogoResponse producto) {
        if (producto.descuento() == 0) {
            return producto.precio();
        }
        return producto.precio()
                .multiply(BigDecimal.valueOf(100L - producto.descuento()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
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
