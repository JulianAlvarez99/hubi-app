package com.calmasalud.hubi.core.service;

public class CatalogService {

    // (Más adelante inyectarás los repositorios aquí)

    /**
     * Implementa la lógica de RF8 para generar códigos únicos.
     * [cite: 771-779]
     */
    public String generateProductCode(String productName, String color, boolean isPart) {
        String prefijoProd = productName.substring(0, 3).toUpperCase();
        String prefijoColor = color.substring(0, 3).toUpperCase();
        String tipo = isPart ? "0" : "1";

        // (Aquí faltaría la lógica para buscar el último número correlativo,
        // que vendrá del IProductoRepository)
        String correlativo = "001";

        return String.format("%s%s-%s-%s", prefijoProd, prefijoColor, tipo, correlativo);
    }
}