package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.MasterProductView; // Necesario para acceder a stock/precio
import com.calmasalud.hubi.core.model.Product;           // Necesario para las piezas
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.MasterProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;

// --- Imports de JavaFX ---
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class InventarioController {

    // --- DEPENDENCIAS ---
    private final IMasterProductRepository masterProductRepository = new MasterProductRepositorySQLite();
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    // ----------------------

    // ¡SINCRONIZACIÓN DE TIPOS A TREE TABLE VIEW!
    @FXML private TreeTableView<MasterProduct> productStockTable;
    @FXML private TreeTableColumn<MasterProduct, String> colProductoNombre;
    @FXML private TreeTableColumn<MasterProduct, String> colProductoCodigo;
    @FXML private TreeTableColumn<MasterProduct, Number> colProductoDisponible;
    @FXML private TreeTableColumn<MasterProduct, Number> colProductoPrecio;

    // ... (El resto de las columnas de Insumos, también ajustadas a TreeTableColumn) ...
    @FXML private TreeTableColumn<?, ?> colInsumoTipo;
    @FXML private TreeTableColumn<?, ?> colInsumoColor;
    @FXML private TreeTableColumn<?, ?> colInsumoCantidad;
    @FXML private TreeTableColumn<?, ?> colInsumoUmbral;

    @FXML
    public void initialize() {
        System.out.println("Controlador de Inventario inicializado.");

        // 1. Configuración de columnas (Usa getValue().getValue() para acceder al objeto)
        colProductoNombre.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item == null) return null;

            // Comprobar si el nodo es una Pieza (longitud > 5, Ej: SOPROJ001)
            if (item.getMasterCode() != null && item.getMasterCode().length() > 5) {
                String originalFileName = item.getProductName();
                int dotIndex = originalFileName.lastIndexOf('.');
                return new SimpleStringProperty(dotIndex != -1 ? originalFileName.substring(0, dotIndex) : originalFileName);
            }
            return new SimpleStringProperty(item.getProductName());
        });

        colProductoCodigo.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item == null) return null;
            return new SimpleStringProperty(item.getMasterCode());
        });

        // Asignación de valores numéricos de stock y precio (SOLO VISIBLE EN NODOS PADRE)
        colProductoDisponible.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            // Solo los nodos padre (MasterProductView) tienen la información de stock/precio
            if (item instanceof MasterProductView) {
                return new SimpleObjectProperty<>(((MasterProductView) item).getQuantityAvailable());
            }
            return new SimpleObjectProperty<>(null);
        });

        colProductoPrecio.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item instanceof MasterProductView) {
                return new SimpleObjectProperty<>(((MasterProductView) item).getPrice());
            }
            return new SimpleObjectProperty<>(null);
        });

        loadProductStockData();
    }

    private void loadProductStockData() {
        try {
            List<MasterProduct> masterProductsWithStock = masterProductRepository.findAll();

            // Creamos el nodo raíz oculto
            TreeItem<MasterProduct> rootItem = new TreeItem<>(null);

            for (MasterProduct masterProduct : masterProductsWithStock) {
                MasterProductView productView = (MasterProductView) masterProduct;

                // 1. Crear el nodo Padre (Producto Maestro)
                TreeItem<MasterProduct> masterNode = new TreeItem<>(productView);

                // 2. Obtener las Piezas asociadas
                List<Product> pieces = productRepository.findPiecesByMasterPrefix(productView.getProductPrefix());

                // 3. Llenar los nodos Hijos (Pieza Lógica)
                for (Product piece : pieces) {
                    // Creamos un MasterProduct STUB para el nodo hijo
                    MasterProduct pieceNodeData =  new MasterProduct(
                            piece.getCode(),
                            productView.getProductPrefix(),
                            piece.getName(), // Nombre original del archivo (Ej: base_llave.gcode)
                            "Archivo: " + piece.getFileExtension()
                    );
                    masterNode.getChildren().add(new TreeItem<>(pieceNodeData));
                }
                rootItem.getChildren().add(masterNode);
            }

            productStockTable.setRoot(rootItem);
            productStockTable.setShowRoot(false);

            // Opcional: Expandir para ver los hijos
            rootItem.setExpanded(true);

        } catch (Exception e) {
            System.err.println("❌ Error al cargar datos del stock y piezas: " + e.getMessage());
            e.printStackTrace();
            // Aseguramos que la vista se limpie si hay un error
            productStockTable.setRoot(new TreeItem<>(null));
            showAlert(AlertType.ERROR, "Error de Carga", "No se pudieron cargar los productos y sus piezas del catálogo.");
        }
    }

    // --- MÉTODOS DE ACCIÓN ---

    /**
     * ACCIÓN 1: Aumentar Stock de Producto Finalizado (RF4).
     * Se dispara al presionar "AGREGAR STOCK FINAL".
     */
    @FXML
    private void handleAddProductStock(ActionEvent event) {
        // ... (Validaciones de selección de nodo padre) ...

        MasterProduct selectedProduct = productStockTable.getSelectionModel().getSelectedItem().getValue();
        String masterCode = selectedProduct.getMasterCode();

        try {
            // 1. Cargar la ventana modal
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddStockModal.fxml"));
            Parent root = loader.load();
            AddStockController controller = loader.getController();

            // 2. Pasar datos al controlador modal y configurarlo
            controller.setProductName(selectedProduct.getProductName());

            Stage modalStage = new Stage();
            modalStage.setTitle("Agregar Stock");
            modalStage.initModality(Modality.APPLICATION_MODAL); // Lo hace modal
            modalStage.setScene(new Scene(root));
            modalStage.showAndWait(); // Espera a que la ventana se cierre

            // 3. Procesar el resultado del modal
            if (controller.isAccepted()) {
                int quantity = controller.getCantidad();

                // 4. LÓGICA RF4: Aumentar stock
                masterProductRepository.increaseStock(masterCode, quantity);

                // 5. Confirmar y Recargar
                showAlert(AlertType.INFORMATION, "Éxito",
                        "Se agregaron +" + quantity + " unidades a la existencia del producto: " + selectedProduct.getProductName());
                loadProductStockData();
            }

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error de Interfaz", "No se pudo cargar la ventana de agregar stock: " + e.getMessage());
            e.printStackTrace();
        } catch (RuntimeException e) {
            System.err.println("Error al aumentar stock: " + e.getMessage());
            showAlert(AlertType.ERROR, "Error de Stock", "No se pudo actualizar el stock del producto en la base de datos.");
        }
    }

    /**
     * ACCIÓN 2: Modificar Stock de Pieza Individual (G-code).
     * Se dispara al presionar "MODIFICAR PIEZA (G-Code)".
     */
    @FXML
    private void handleModifyPieceStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una Pieza (Línea Hijo) para registrar su producción individual.");
            return;
        }

        // Si es un nodo Padre, no permitimos esta acción.
        if (selectedItem.getParent() == null || selectedItem.getParent().getValue() == null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Esta acción es para registrar la producción de una Pieza individual (Línea Hijo).");
            return;
        }

        MasterProduct selectedPiece = selectedItem.getValue();
        // Lógica de Stock de Pieza: Aquí iría la implementación de RF9 (consumo de filamento)
        showAlert(AlertType.INFORMATION, "Acción de Producción",
                "Registrando producción de Pieza: " + selectedPiece.getProductName() +
                        " (Código: " + selectedPiece.getMasterCode() + "). \n\nImplementación futura: Descontar insumos y registrar la fabricación de esta pieza.");
    }

    // --- Manejadores de eventos de Insumos (Stubs) ---
    @FXML
    private void handleAddSupply(ActionEvent event) {
        System.out.println("Acción: Agregar Insumo (Futuro RF5)");
    }

    @FXML
    private void handleRemoveSupply(ActionEvent event) {
        System.out.println("Acción: Eliminar Insumo (Futuro RF5)");
    }

    @FXML
    private void handleModifySupply(ActionEvent event) {
        System.out.println("Acción: Modificar Insumo (Futuro RF5)");
    }

    // Método auxiliar para mostrar alertas
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        // Asegúrate de tener este import: import javafx.scene.control.DialogPane;
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }
}