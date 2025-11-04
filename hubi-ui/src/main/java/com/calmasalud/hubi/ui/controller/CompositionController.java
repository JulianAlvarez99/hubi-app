package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.persistence.repository.ProductCompositionRepositorySQLite;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class CompositionController {

    // Asumimos que inyectar el repositorio aquí es temporal, para la prueba.
    private final IProductCompositionRepository compositionRepository = new ProductCompositionRepositorySQLite();

    @FXML private Label lblProductoNombre;
    @FXML private TableView<ProductComposition> pieceCompositionTable;
    @FXML private TableColumn<ProductComposition, String> colNombrePieza;
    @FXML private TableColumn<ProductComposition, Integer> colCantidadRequerida;

    private String masterCode;
    private List<Product> rawPieces; // Archivos cargados originalmente
    private boolean compositionSaved = false;

    public void setCompositionData(String masterCode, String productName, List<Product> rawPieces) {
        this.masterCode = masterCode;
        this.rawPieces = rawPieces;
        lblProductoNombre.setText("Producto: " + productName);

        // 1. Crear el modelo de composición a partir de los archivos únicos
        ObservableList<ProductComposition> initialComposition = rawPieces.stream()
                .map(p -> {
                    String baseName = p.getName().substring(0, p.getName().lastIndexOf('.'));
                    // Valor por defecto: 1 unidad de esta pieza
                    return new ProductComposition(masterCode, baseName, 1);
                })
                .distinct() // Solo una entrada por pieza única
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        pieceCompositionTable.setItems(initialComposition);
    }

    public boolean isCompositionSaved() {
        return compositionSaved;
    }

    @FXML
    public void initialize() {
        // 1. Configuración de columnas
        colNombrePieza.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getPieceNameBase()));
        colNombrePieza.setMaxWidth(Double.MAX_VALUE);
        colCantidadRequerida.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getRequiredQuantity()));

        // 2. Configurar la columna de cantidad como EDITABLE
        colCantidadRequerida.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colCantidadRequerida.setOnEditCommit(event -> {
            // Actualiza el modelo cuando el usuario edita la celda
            ProductComposition comp = event.getRowValue();
            Integer newValue = event.getNewValue();
            if (newValue != null && newValue > 0) {
                comp.setRequiredQuantity(newValue);
            } else {
                showAlert(AlertType.ERROR, "Error de Cantidad", "La cantidad debe ser mayor a 0.");
                // Revertir el valor al antiguo
                pieceCompositionTable.refresh();
            }
        });
        pieceCompositionTable.setEditable(true);
    }

    @FXML
    private void handleSave() {
        try {
            // 1. Obtener los datos de la tabla (el BOM)
            List<ProductComposition> composition = pieceCompositionTable.getItems().stream().toList();

            // 2. Guardar la composición
            compositionRepository.saveComposition(this.masterCode, composition);

            this.compositionSaved = true;
            showAlert(AlertType.INFORMATION, "Éxito", "Composición guardada exitosamente para el producto " + this.masterCode);
            closeStage();

        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Error de Persistencia", "No se pudo guardar la composición.");
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

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }
}
