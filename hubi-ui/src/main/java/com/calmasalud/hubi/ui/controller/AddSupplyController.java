package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.service.CatalogService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class AddSupplyController {

    private CatalogService catalogService;
    private Supply supplyToEdit;
    private boolean isEditMode = false;
    private Runnable onStockUpdated;

    // Campos FXML
    @FXML private Label lblTitulo;
    @FXML private Label lblCantidad;
    @FXML private TextField txtName;
    @FXML private ComboBox<String> comboTipoFilamento;
    @FXML private TextField txtColorFilamento;
    @FXML private TextField txtCantidad;
    @FXML private TextField txtUmbralAlerta;

    @FXML
    public void initialize() {
        // Inicializar opciones comunes para el tipo de filamento
        comboTipoFilamento.getItems().addAll("PLA", "ABS", "PETG", "TPU", "ASA", "Otro");

        // Configurar validación para campos numéricos
        setupNumericValidation(txtCantidad);
        setupNumericValidation(txtUmbralAlerta);
    }

    private void setupNumericValidation(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            // Permite dígitos, un punto, y hasta dos decimales
            if (!newValue.matches("\\d*([\\.]\\d{0,2})?")) {
                field.setText(oldValue);
            }
        });
    }

    public void setCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public void setOnStockUpdated(Runnable onStockUpdated) {
        this.onStockUpdated = onStockUpdated;
    }

    /**
     * Configura el modal para editar datos y añadir stock a un insumo existente (Modo de edición)
     */
    public void setSupplyToEdit(Supply supply) {
        this.supplyToEdit = supply;
        this.isEditMode = true;
        lblTitulo.setText("Modificar Datos y Añadir Stock");
        lblCantidad.setText("Cantidad a Añadir (g):"); // El servicio suma al stock existente

        // Rellenar campos con datos del insumo existente
        txtName.setText(supply.getName());
        txtColorFilamento.setText(supply.getColorFilamento());
        txtUmbralAlerta.setText(String.valueOf(supply.getUmbralAlerta()));

        String tipo = supply.getTipoFilamento();
        if (comboTipoFilamento.getItems().contains(tipo)) {
            comboTipoFilamento.getSelectionModel().select(tipo);
        } else {
            comboTipoFilamento.getSelectionModel().select("Otro");
        }

        // La cantidad se deja vacía o en 0 para que el usuario ingrese la cantidad a sumar.
        txtCantidad.setText("0.0");


        txtName.setEditable(false);
        txtName.setStyle("-fx-control-inner-background: #e9e9e9;"); // Opcional: para indicarle al usuario que es de solo lectura

        comboTipoFilamento.setDisable(true);

        txtColorFilamento.setEditable(false);
        txtColorFilamento.setStyle("-fx-control-inner-background: #e9e9e9;"); // Opcional: para indicarle al usuario que es de solo lectura

    }

    @FXML
    private void handleGuardar(ActionEvent event) {
        // 1. Validar y obtener datos de UI
        String name = txtName.getText().trim();
        String tipo = comboTipoFilamento.getValue();
        String color = txtColorFilamento.getText().trim();
        String cantidadStr = txtCantidad.getText().trim();
        String umbralStr = txtUmbralAlerta.getText().trim();

        if (name.isEmpty() || tipo == null || color.isEmpty() || cantidadStr.isEmpty() || umbralStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error de Validación", "Todos los campos son obligatorios.");
            return;
        }

        double cantidad;
        double umbral;

        try {
            cantidad = Double.parseDouble(cantidadStr);
            umbral = Double.parseDouble(umbralStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Formato", "Cantidad y Umbral deben ser números válidos.");
            return;
        }

        if (cantidad <= 0 && !isEditMode) {
            showAlert(Alert.AlertType.ERROR, "Error de Stock", "La cantidad inicial a añadir debe ser positiva.");
            return;
        }

        // 2. Preparar el Código (CORREGIDO PARA EVITAR SALTOS DE NUMERACIÓN)
        String finalCode = null;

        if (isEditMode && supplyToEdit != null) {
            // Si estamos editando, MANTENEMOS el código que ya tiene el insumo.
            finalCode = supplyToEdit.getCode();
        }
        // Si es nuevo (!isEditMode), dejamos finalCode en NULL.
        // Esto le indica al CatalogService que NO gaste un número correlativo todavía.

        // 3. Crear objeto Supply y ASIGNAR TODOS LOS VALORES
        Supply newOrUpdatedSupply = new Supply();

        if (isEditMode && supplyToEdit != null) {
            newOrUpdatedSupply.setId(supplyToEdit.getId()); // Mantener el ID de base de datos
        }

        newOrUpdatedSupply.setCode(finalCode); // Pasamos el código existente o NULL si es nuevo
        newOrUpdatedSupply.setName(name);
        newOrUpdatedSupply.setTipoFilamento(tipo);
        newOrUpdatedSupply.setColorFilamento(color);
        newOrUpdatedSupply.setCantidadDisponible(cantidad);
        newOrUpdatedSupply.setUmbralAlerta(umbral);

        // 4. Llamar al servicio
        try {
            // El servicio ahora se encarga:
            // - Si existe, lo fusiona.
            // - Si no existe y el código es null, genera el siguiente correlativo y guarda.
            catalogService.addOrModifySupplyStock(newOrUpdatedSupply);

            // 5. Actualizar la tabla principal y cerrar
            if (onStockUpdated != null) {
                onStockUpdated.run();
            }

            // Asumo que tienes este método auxiliar definido en tu clase, si no, usa el de abajo
            closeWindow(event);

        } catch (Exception e) {
            // Mostrar alerta REAL con el error
            showAlert(Alert.AlertType.ERROR, "Error de Operación", "No se pudo guardar el insumo: " + e.getMessage());
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