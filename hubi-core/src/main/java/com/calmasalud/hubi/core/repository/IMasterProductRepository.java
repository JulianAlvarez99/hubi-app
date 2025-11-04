package com.calmasalud.hubi.core.repository;

import com.calmasalud.hubi.core.model.MasterProduct;
import java.util.List;

public interface IMasterProductRepository {

    /**
     * Extrae el prefijo de 3 letras (Ej: SOP) a partir del nombre completo del producto.
     * Esta es una función de utilidad esencial para el servicio.
     * @param productName Nombre del producto.
     * @return El prefijo de 3 letras en mayúsculas (Ej: SOP).
     */
    String getPrefixFromName(String productName); // <-- ¡ESTE MÉTODO ES EL QUE FALTABA!

    /**
     * Genera el siguiente Código Maestro único (Ej: SOP01) para el producto finalizado (RF8).
     * Consulta y actualiza la tabla master_correlatives.
     * @param masterPrefix Prefijo de 3 letras del producto (Ej: SOP).
     * @return El código maestro completo (ej: SOP01).
     */
    String getNextMasterCode(String masterPrefix);

    /**
     * Guarda un nuevo producto maestro en master_products e inicializa su stock.
     */
    long saveNewProduct(MasterProduct product, double initialPrice);

    /**
     * Método requerido por el compilador para el contrato base de repositorio (si existe).
     * Delega la llamada a saveNewProduct con precio inicial 0.0.
     */
    long save(MasterProduct product);

    /**
     * Aumenta el stock disponible de un producto finalizado (RF4).
     */
    void increaseStock(String masterCode, int quantity);

    MasterProduct findByMasterCode(String masterCode);

    List<MasterProduct> findAll();
    MasterProduct findByProductName(String productName);
    void deleteProduct(String masterCode);
    MasterProduct findByProductPrefix(String prefix);
    public void decreaseStock(String masterCode, int quantity);
}