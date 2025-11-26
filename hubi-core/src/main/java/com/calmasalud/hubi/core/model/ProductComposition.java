package com.calmasalud.hubi.core.model;

/**
 * Representa una fila en la tabla product_composition: qué pieza y cuántas unidades
 * de ella se necesitan para un MasterProduct (Receta / BOM).
 */
public class ProductComposition {
    private final String masterCode;
    private final String pieceNameBase; // Nombre base de la pieza (Ej: Llave_base)
    private int requiredQuantity; // Cantidad requerida de esta pieza

    public ProductComposition(String masterCode, String pieceNameBase, int requiredQuantity) {
        this.masterCode = masterCode;
        this.pieceNameBase = pieceNameBase;
        this.requiredQuantity = requiredQuantity;
    }

    // Getters
    public String getMasterCode() { return masterCode; }
    public String getPieceNameBase() { return pieceNameBase; }
    public int getRequiredQuantity() { return requiredQuantity; }

    // Setter (necesario para que el controlador del modal modifique la cantidad)
    public void setRequiredQuantity(int requiredQuantity) {
        this.requiredQuantity = requiredQuantity;
    }
    // --- MÉTODOS ALIAS (Para compatibilidad con el Controlador) ---

    // El controlador llama a getQuantity(), nosotros le damos requiredQuantity
    public int getQuantity() {
        return requiredQuantity;
    }

    // El controlador pide un Código, usamos el pieceNameBase
    public String getComponentCode() {
        return pieceNameBase;
    }

    // El controlador pide un Nombre, usamos el pieceNameBase
    public String getComponentName() {
        return pieceNameBase;
    }
}
