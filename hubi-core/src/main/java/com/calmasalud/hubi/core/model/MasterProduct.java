package com.calmasalud.hubi.core.model;

/**
 * Representa la entidad Producto Maestro (RF4), que es el artículo finalizado.
 * Su clave única es independiente del color y tiene el formato: [PREFIJO 3 letras][CORRELATIVO 2 dígitos] -> Ej: SOP01
 */
public class MasterProduct {
    private String masterCode;      // Código único (Ej: SOP01)
    private String productPrefix;   // Prefijo de 3 letras (Ej: SOP)
    private String productName;     // Nombre completo (Ej: Soporte Filamento)
    private String description;

    public MasterProduct(String masterCode, String productPrefix, String productName, String description) {
        this.masterCode = masterCode;
        this.productPrefix = productPrefix;
        this.productName = productName;
        this.description = description;
    }

    // --- Getters ---
    public String getMasterCode() {
        return masterCode;
    }

    public String getProductPrefix() {
        return productPrefix;
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }
}