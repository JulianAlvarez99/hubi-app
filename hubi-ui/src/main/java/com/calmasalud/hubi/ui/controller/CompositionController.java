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

        // Configuración de la columna de Cantidad para leer el valor del POJO
        colCantidadRequerida.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getRequiredQuantity()));

        // 2. Configurar la columna de cantidad como EDITABLE
        colCantidadRequerida.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));

        colCantidadRequerida.setOnEditCommit(event -> {
            ProductComposition comp = event.getRowValue();
            Integer newValue = event.getNewValue();

            if (newValue != null && newValue > 0) {
                // 1. Escribir el valor en el modelo (POJO)
                comp.setRequiredQuantity(newValue);

                // 2. CORRECCIÓN ROBUSTA: Forzar la actualización inmediata de la vista.
                // Esto asegura que la vista sincronice el valor nuevo.
                pieceCompositionTable.refresh();

            } else {
                // 3. Manejo de Error: Revertir la edición y notificar.
                showAlert(AlertType.ERROR, "Error de Cantidad", "La cantidad debe ser mayor a 0.");

                // Revertir la edición de la celda al valor anterior sin modificar el modelo:
                pieceCompositionTable.edit(-1, null);
            }
        });
        pieceCompositionTable.setEditable(true);
    }

    @FXML
    private void handleSave() {
        try {
            // 1. FORZAR LA FINALIZACIÓN DE LA EDICIÓN PENDIENTE (SOLUCIÓN)
            // Esto asegura que el setOnEditCommit se ejecute para la última celda.

            // Obtener la posición de la celda actualmente editada
            TablePosition pos = pieceCompositionTable.getEditingCell();

            if (pos != null) {
                // Si hay una celda activa, primero cancelamos la edición
                // para asegurar que el TextField suelte el valor, y luego forzamos el commit.
                // Aunque la forma más robusta es el commit global:
                pieceCompositionTable.edit(-1, null);
            }

            // 2. Obtener los datos de la tabla (el BOM)
            List<ProductComposition> composition = pieceCompositionTable.getItems().stream()
                    .filter(row -> row.getRequiredQuantity() > 0)
                    .toList();

            // 3. Verificación (si el DEBUG le muestra 3, el problema está resuelto)
            // System.out.println("VALOR FINAL A GUARDAR: Cantidad: " + composition.get(1).getRequiredQuantity());

            if (composition.isEmpty()) {
                showAlert(AlertType.ERROR, "Validación", "Debe especificar al menos una pieza con cantidad requerida mayor a cero.");
                return;
            }

            // 4. Guardar la composición
            compositionRepository.saveComposition(this.masterCode, composition);

            this.compositionSaved = true;
            showAlert(AlertType.INFORMATION, "Éxito", "Composición guardada exitosamente para el producto " + this.masterCode);
            closeStage();

        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Error de Persistencia", "No se pudo guardar la composición: " + e.getMessage());
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
        // Delegamos la llamada al Method estático de la clase de utilidad,
        // pasando null como header, que es el patrón más común.
        UISettings.showAlert(type, title, null, content);
    }
}
