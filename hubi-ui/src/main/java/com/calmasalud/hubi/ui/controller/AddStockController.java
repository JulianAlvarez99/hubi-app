package com.calmasalud.hubi.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AddStockController {

    @FXML private Label lblProductoNombre;
    @FXML private TextField txtCantidad;

    private int cantidad = 0;
    private boolean accepted = false;

    public void setProductName(String name) {
        lblProductoNombre.setText("(Producto: " + name + ")");
    }

    // Devuelve la cantidad ingresada
    public int getCantidad() {
        return cantidad;
    }

    // Devuelve si el usuario presionó ACEPTAR
    public boolean isAccepted() {
        return accepted;
    }

    @FXML
    private void handleAccept() {
        try {
            int input = Integer.parseInt(txtCantidad.getText());
            if (input <= 0) {
                showAlert(AlertType.ERROR, "Error de Cantidad", "La cantidad debe ser un número positivo.");
                return;
            }
            this.cantidad = input;
            this.accepted = true;
            closeStage();
        } catch (NumberFormatException e) {
            showAlert(AlertType.ERROR, "Error de Entrada", "Por favor, ingrese un número entero válido.");
        }
    }

    @FXML
    private void handleCancel() {
        this.accepted = false;
        closeStage();
    }

    private void closeStage() {
        // Cierra la ventana modal
        Stage stage = (Stage) txtCantidad.getScene().getWindow();
        stage.close();
    }

    // Método auxiliar para mostrar alertas (para la validación dentro del modal)
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }
}
