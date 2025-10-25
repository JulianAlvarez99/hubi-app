package com.calmasalud.hubi.core.repository;
import com.calmasalud.hubi.core.model.Product;

public interface IProductRepository {
    String getNextCorrelative(String prefijoSeisLetras);
    //Guarda un objeto Product en la base de datos.
    long save(Product product);
    //Busca un Producto (o Pieza) por su Código Único.
    Product findByCode(String code);
}
