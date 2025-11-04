package com.calmasalud.hubi.core.repository;

import com.calmasalud.hubi.core.model.ProductComposition;
import java.util.List;

public interface IProductCompositionRepository {

    /**
     * Guarda la composición de un producto maestro. Esto ocurre solo la primera vez.
     */
    void saveComposition(String masterCode, List<ProductComposition> composition);

    /**
     * Obtiene la composición de un producto maestro.
     */
    List<ProductComposition> getComposition(String masterCode);

    /**
     * Verifica si la composición (BOM) para un producto maestro ya existe.
     */
    boolean compositionExists(String masterCode);
}
