package com.calmasalud.hubi.core.repository;
import com.calmasalud.hubi.core.model.Product;

import java.util.List;

public interface IProductRepository {

    // Usado por CatalogService para RF8 (pieza)
    String getNextCorrelative(String prefijoSeisLetras);

    // Usado por CatalogService para guardar la Pieza (RF1)
    // Guarda un objeto Product en la base de datos.
    long save(Product product);

    // Usado por CatalogService para buscar (correlación/detalles)
    // Busca un Producto (o Pieza) por su Código Único.
    Product findByCode(String code);

    // Usado por CatalogService para eliminar pieza/archivos
    // Elimina una entidad (pieza o producto)
    void deleteByCode(String code);

    /**
     * Busca todas las piezas (archivos) que pertenecen a un producto maestro
     * por su prefijo de 3 letras (Ej: 'LLA').
     */
    List<Product> findPiecesByMasterPrefix(String masterPrefix); // <-- ¡ESTO ES LO QUE DEBES AÑADIR!
}
