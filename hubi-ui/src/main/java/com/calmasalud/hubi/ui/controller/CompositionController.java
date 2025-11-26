package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.persistence.repository.ProductCompositionRepositorySQLite;
import com.calmasalud.hubi.ui.util.UISettings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class CompositionController {

    private final IProductCompositionRepository compositionRepository = new ProductCompositionRepositorySQLite();

    @FXML private Label lblProductoNombre;
    @FXML private TableView<ProductComposition> pieceCompositionTable;
    @FXML private TableColumn<ProductComposition, String> colNombrePieza;
    @FXML private TableColumn<ProductComposition, Integer> colCantidadRequerida;

    private String masterCode;
    private List<Product> rawPieces;
    private boolean compositionSaved = false;

    public void setCompositionData(String masterCode, String productName, List<Product> rawPieces) {
        this.masterCode = masterCode;
        this.rawPieces = rawPieces;
        lblProductoNombre.setText("Producto: " + productName);

        ObservableList<ProductComposition> initialComposition = rawPieces.stream()
                .map(p -> {
                    String baseName = p.getName().substring(0, p.getName().lastIndexOf('.'));
                    // Valor por defecto: 1 unidad
                    return new ProductComposition(masterCode, baseName, 1);
                })
                .distinct()
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        pieceCompositionTable.setItems(initialComposition);
    }

    public boolean isCompositionSaved() {
        return compositionSaved;
    }

    @FXML
    public void initialize() {
        colNombrePieza.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getPieceNameBase()));

        colCantidadRequerida.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getRequiredQuantity()));

        // --- CORRECCIÓN: Usamos nuestra celda inteligente en lugar de TextFieldTableCell ---
        colCantidadRequerida.setCellFactory(column -> new SmartIntegerEditingCell());

        // Evento que actualiza el modelo cuando la celda confirma el cambio
        colCantidadRequerida.setOnEditCommit(event -> {
            ProductComposition comp = event.getRowValue();
            Integer newValue = event.getNewValue();

            if (newValue != null && newValue > 0) {
                comp.setRequiredQuantity(newValue);
                System.out.println("✅ Cantidad actualizada en modelo: " + newValue);
            } else {
                UISettings.showAlert(AlertType.ERROR, "Error", null, "La cantidad debe ser mayor a 0.");
                pieceCompositionTable.refresh(); // Refrescar para borrar el valor inválido
            }
        });

        pieceCompositionTable.setEditable(true);
    }

    @FXML
    private void handleSave() {
        try {
            // 1. TRUCO DE FOCO: Quitamos el foco de la tabla.
            // Gracias a SmartIntegerEditingCell, esto forzará el COMMIT del valor (el "2")
            // en lugar de cancelarlo.
            lblProductoNombre.requestFocus();

            // 2. Obtener los datos (ahora sí tendrán el 2)
            List<ProductComposition> composition = pieceCompositionTable.getItems().stream()
                    .filter(row -> row.getRequiredQuantity() > 0)
                    .toList();

            if (composition.isEmpty()) {
                UISettings.showAlert(AlertType.ERROR, "Validación", null, "Debe especificar al menos una pieza.");
                return;
            }

            // 3. Guardar
            compositionRepository.saveComposition(this.masterCode, composition);
            this.compositionSaved = true;

            UISettings.showAlert(AlertType.INFORMATION, "Éxito", null, "Receta guardada correctamente.");
            closeStage();

        } catch (Exception e) {
            UISettings.showAlert(AlertType.ERROR, "Error", null, "No se pudo guardar: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        this.compositionSaved = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) lblProductoNombre.getScene().getWindow();
        stage.close();
    }

    // =================================================================================
    // CLASE INTERNA: Celda que confirma cambios al perder el foco (SOLUCIÓN DEFINITIVA)
    // =================================================================================
    class SmartIntegerEditingCell extends TableCell<ProductComposition, Integer> {
        private TextField textField;

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus(); // Pedir foco para activar el listener después
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem().toString());
            setGraphic(null);
        }

        @Override
        public void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

            // 1. Si el usuario presiona ENTER -> Confirmar
            textField.setOnAction(evt -> commitFromString(textField.getText()));

            // 2. Si el usuario hace clic afuera (pierde foco) -> Confirmar también
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) { // Perdió el foco
                    commitFromString(textField.getText());
                }
            });
        }

        private void commitFromString(String text) {
            try {
                int val = Integer.parseInt(text);
                commitEdit(val); // Esto dispara el setOnEditCommit del controlador
            } catch (NumberFormatException e) {
                cancelEdit(); // Si escribe letras, cancelamos
            }
        }

        private String getString() {
            return getItem() == null ? "1" : getItem().toString();
        }
    }
}