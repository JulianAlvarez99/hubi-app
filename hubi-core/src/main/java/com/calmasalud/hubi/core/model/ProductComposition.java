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
}
