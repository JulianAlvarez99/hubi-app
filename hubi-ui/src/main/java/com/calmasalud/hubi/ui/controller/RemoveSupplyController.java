package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.service.CatalogService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RemoveSupplyController {

    private CatalogService catalogService;
    private Supply selectedSupply;
    private Runnable onStockUpdated;

    // Campos FXML
    @FXML private Label lblInsumoInfo;
    @FXML private TextField txtCantidad;

    public void setCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public void setSupply(Supply supply) {
        this.selectedSupply = supply;
        lblInsumoInfo.setText(String.format(
                "Descartando: %s %s (%s) | Stock Disponible: %.2f g",
                supply.getTipoFilamento(),
                supply.getColorFilamento(),
                supply.getName(),
                supply.getCantidadDisponible()
        ));

        // Configurar validación numérica (solo números y punto)
        txtCantidad.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*([\\.]\\d{0,2})?")) {
                txtCantidad.setText(oldValue);
            }
        });
    }

    public void setOnStockUpdated(Runnable onStockUpdated) {
        this.onStockUpdated = onStockUpdated;
    }

    @FXML
    private void handleDescartar(ActionEvent event) {
        if (selectedSupply == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "No se ha seleccionado ningún insumo.");
            return;
        }

        double quantityToDeduct;
        try {
            quantityToDeduct = Double.parseDouble(txtCantidad.getText().trim());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Formato", "La cantidad debe ser un número válido.");
            return;
        }

        if (quantityToDeduct <= 0) {
            showAlert(Alert.AlertType.ERROR, "Error de Cantidad", "Debe ingresar una cantidad positiva a descartar.");
            return;
        }

        // 1. CAPTURAR EL ESTADO ANTERIOR (Stock y Umbral)
        // El objeto selectedSupply contiene el stock y umbral antes de la acción del usuario.
        final double previousStock = selectedSupply.getCantidadDisponible();
        final double umbral = selectedSupply.getUmbralAlerta();
        final double EPSILON = 0.0001;

        try {
            // 2. Llama al servicio para descontar el stock
            catalogService.removeSupplyStock(selectedSupply.getId(), quantityToDeduct);

            // 3. Actualizar la tabla principal
            if (onStockUpdated != null) {
                onStockUpdated.run();
            }

            // 4. RECUPERAR EL INSUMO ACTUALIZADO (Estado post-descuento)
            Supply updatedSupply = catalogService.getSupplyById(selectedSupply.getId());

            if (updatedSupply != null) {
                double currentStock = updatedSupply.getCantidadDisponible();

                // --- LÓGICA DE NOTIFICACIÓN DE CRUCE DE UMBRAL (EXACTA) ---

                if (currentStock < EPSILON) { // Caso 1: STOCK CERO (Prioridad máxima)
                    showAlert(Alert.AlertType.WARNING,
                            "¡Stock Agotado! ⚠️",
                            "El insumo '" + updatedSupply.getName() + "' ha llegado a 0g de stock. \nSe mantendrá en el catálogo para ser repuesto.");
                }
                // Caso 2: CRUCE DE UMBRAL (Solo si venía de arriba y ahora está abajo)
                else if (previousStock > umbral + EPSILON && currentStock < umbral + EPSILON) {
                    showAlert(Alert.AlertType.WARNING,
                            "Alerta de Umbral 🟡",
                            "El insumo '" + updatedSupply.getName() + "' ha caído por debajo de su umbral de alerta (" + umbral + "g). Stock actual: " + String.format("%.2f", currentStock) + "g.");
                }
                // Caso 3: Éxito normal (Queda stock y no cruzó el umbral, O ya estaba debajo y se redujo más)
                else {
                    showAlert(Alert.AlertType.INFORMATION, "Éxito", String.format("Se han descartado %.2f gramos de stock. Stock restante: %.2f g", quantityToDeduct, currentStock));
                }

            } else {
                // Si el insumo fue eliminado (lo cual no debería pasar con las correcciones previas)
                showAlert(Alert.AlertType.INFORMATION, "Éxito", String.format("Se han descartado %.2f gramos de stock. Insumo removido del catálogo.", quantityToDeduct));
            }

            // 5. Cerrar la ventana
            closeWindow(event);

        } catch (IllegalArgumentException e) {
            // Captura el error de stock insuficiente del CatalogService
            showAlert(Alert.AlertType.ERROR, "Error de Descarte", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Ocurrió un error inesperado al descartar stock.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancelar(ActionEvent event) {
        closeWindow(event);
    }

    private void closeWindow(ActionEvent event) {
        ((Stage) ((Button) event.getSource()).getScene().getWindow()).close();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}