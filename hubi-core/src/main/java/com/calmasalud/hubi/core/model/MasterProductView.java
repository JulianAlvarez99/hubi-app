package com.calmasalud.hubi.core.model;

import java.util.List;

/**
 * Clase DTO (Data Transfer Object) para el Inventario.
 * Combina MasterProduct y la información de FinishedStock.
 */
public class MasterProductView extends MasterProduct {

    private final int quantityAvailable;
    private final double price;
    private List<PieceView> pieces;

    public MasterProductView(String masterCode, String productPrefix, String productName, String description, int quantityAvailable, double price) {
        super(masterCode, productPrefix, productName, description);
        this.quantityAvailable = quantityAvailable;
        this.price = price;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public double getPrice() {
        return price;
    }
    public List<PieceView> getPieces() {
        return pieces;
    }

    public void setPieces(List<PieceView> pieces) {
        this.pieces = pieces;
    }
}
