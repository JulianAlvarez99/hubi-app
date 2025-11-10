package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.MasterProductView; // Necesario para acceder a stock/precio
import com.calmasalud.hubi.core.model.PieceStockColorView;
import com.calmasalud.hubi.core.model.Product;           // Necesario para las piezas
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.service.CatalogService;
import com.calmasalud.hubi.persistence.repository.MasterProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductCompositionRepositorySQLite;
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
    private final IProductCompositionRepository productCompositionRepository = new ProductCompositionRepositorySQLite();
    private final CatalogService catalogService = new CatalogService(productRepository, masterProductRepository, productCompositionRepository);
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

        // 1. Configuración de columnas
        colProductoNombre.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item == null) return null;

            // Comprobar si el nodo es una Pieza (usamos la lógica de longitud del código)
            // La Pieza (Hijo) tiene un código de 9 dígitos (LLAROJ001), el Padre tiene 5 (LLA01).
            if (item.getMasterCode() != null && item.getMasterCode().length() > 5) {
                // Si es un nodo hijo, el nombre original del archivo está guardado en MasterProduct.productName.
                String originalFileName = item.getProductName();
                int dotIndex = originalFileName.lastIndexOf('.');
                // Devolver solo el Nombre Base (Ej: Llavero Olla x 8 V3_PLA_1h59m)
                return new SimpleStringProperty(dotIndex != -1 ? originalFileName.substring(0, dotIndex) : originalFileName);
            }
            // Si es el nodo padre, devolver el nombre del producto (Ej: Llavero)
            return new SimpleStringProperty(item.getProductName());
        });

        colProductoCodigo.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item == null) return null;
            return new SimpleStringProperty(item.getMasterCode());
        });

        colProductoDisponible.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item instanceof MasterProductView) {
                // Muestra la cantidad real del MasterProductView (que puede ser el stock final o el stock de pieza)
                return new SimpleObjectProperty<>(((MasterProductView) item).getQuantityAvailable());
            }
            return new SimpleObjectProperty<>(0); // Fallback
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

            TreeItem<MasterProduct> rootItem = new TreeItem<>(null);

            for (MasterProduct masterProduct : masterProductsWithStock) {
                MasterProductView productView = (MasterProductView) masterProduct;
                TreeItem<MasterProduct> masterNode = new TreeItem<>(productView);

                List<Product> pieces = productRepository.findPiecesByMasterPrefix(productView.getProductPrefix());

                // --- BUCLE NIVEL 2: Pieza Lógica (Sumatoria de Stock de todos los colores) ---
                for (Product piece : pieces) {
                    String pieceNameBase = piece.getName().substring(0, piece.getName().lastIndexOf('.'));
                    int totalPieceStock = productRepository.getPieceStockQuantity(pieceNameBase);

                    // Creamos el NODO HIJO (MasterProductView para tener stock y ser expandible)
                    MasterProductView pieceNodeData = new MasterProductView(
                            piece.getCode(),
                            productView.getProductPrefix(),
                            piece.getName(), // Nombre completo (Ej: Llave Olla x 8 V3_PLA_1h59m.gcode)
                            "Archivo: " + piece.getFileExtension(),
                            totalPieceStock, // <--- STOCK TOTAL de la pieza (suma de todos los colores)
                            0.0
                    );
                    TreeItem<MasterProduct> pieceNode = new TreeItem<>(pieceNodeData);

                    // --- BUCLE NIVEL 3: Desglose por Color ---
                    List<PieceStockColorView> colorStocks = productRepository.getStockByPieceNameBase(pieceNameBase);

                    for (PieceStockColorView colorStock : colorStocks) {

                        // Creamos un MasterProductView STUB para el NODO NIETO (Color y Cantidad)
                        MasterProductView colorNodeData = new MasterProductView(
                                piece.getCode() + "-" + colorStock.getColorName(), // Código único para este color
                                productView.getProductPrefix(),
                                colorStock.getColorName(), // Nombre del color (Ej: ROJO PLA)
                                "Stock por Color",
                                colorStock.getQuantityAvailable(), // <-- STOCK REAL DEL COLOR
                                0.0
                        );
                        // Añadir el nodo de color al nodo de pieza
                        pieceNode.getChildren().add(new TreeItem<>(colorNodeData));
                    }

                    masterNode.getChildren().add(pieceNode);
                }
                rootItem.getChildren().add(masterNode);
            }

            productStockTable.setRoot(rootItem);
            productStockTable.setShowRoot(false);
            rootItem.setExpanded(true);

        } catch (Exception e) {
            System.err.println("❌ Error al cargar datos del stock y piezas: " + e.getMessage());
            e.printStackTrace();
            productStockTable.setRoot(new TreeItem<>(null));
            showAlert(AlertType.ERROR, "Error de Carga", "No se pudieron cargar los productos y sus piezas del catálogo. Razón: " + e.getMessage());
        }
    }

    // --- MÉTODOS DE ACCIÓN ---

    /**
     * ACCIÓN 1: Aumentar Stock de Producto Finalizado (RF4).
     * Se dispara al presionar "AGREGAR STOCK FINAL".
     */
    @FXML
    private void handleAddProductStock(ActionEvent event) {
        // 1. Obtener y validar selección
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione un Producto Maestro (Línea Padre) de la lista para agregar stock finalizado.");
            return;
        }

        // Validación jerárquica: Debe ser el nodo Padre (su padre es la raíz invisible, cuyo .getValue() es null).
        if (selectedItem.getParent() == null || selectedItem.getParent().getValue() != null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo puede agregar stock finalizado seleccionando la línea principal (Producto Maestro).");
            return;
        }

        MasterProduct selectedProduct = selectedItem.getValue();
        String masterCode = selectedProduct.getMasterCode();

        try {
            // 2. VERIFICACIÓN CLAVE: ¿Existe la composición (BOM)? (Punto 1 del requisito)
            boolean compositionExists = productCompositionRepository.compositionExists(masterCode);

            if (!compositionExists) {
                System.out.println("⚠️ Composición no definida. Iniciando definición de BOM.");

                // Obtener las piezas/archivos cargados para este producto (para llenar el modal BOM)
                String productPrefix = selectedProduct.getProductPrefix();
                List<Product> rawPieces = productRepository.findPiecesByMasterPrefix(productPrefix);

                if (rawPieces.isEmpty()) {
                    showAlert(AlertType.ERROR, "Error de Catálogo", "No hay archivos (.stl, .3mf, .gcode) cargados para este producto. Agregue piezas primero.");
                    return;
                }

                // --- Cargar y mostrar modal de COMPOSICIÓN (CompositionModal) ---
                FXMLLoader loaderComp = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/CompositionModal.fxml"));
                Parent rootComp = loaderComp.load();
                CompositionController compController = loaderComp.getController();

                compController.setCompositionData(masterCode, selectedProduct.getProductName(), rawPieces);

                Stage modalStageComp = new Stage();
                modalStageComp.setTitle("Definir Composición (BOM) - " + selectedProduct.getProductName());
                modalStageComp.initModality(Modality.APPLICATION_MODAL);
                modalStageComp.setScene(new Scene(rootComp));
                modalStageComp.showAndWait();

                // Si el usuario canceló la composición, salimos.
                if (!compController.isCompositionSaved()) {
                    showAlert(AlertType.INFORMATION, "Cancelado", "La adición de stock fue cancelada. Debe definir la composición para continuar.");
                    return;
                }
            }

            // 3. SOLICITAR LA CANTIDAD A AGREGAR (AddStockModal)

            FXMLLoader loaderStock = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddStockModal.fxml"));
            Parent rootStock = loaderStock.load();
            AddStockController stockController = loaderStock.getController();

            stockController.setProductName(selectedProduct.getProductName());

            Stage modalStageStock = new Stage();
            modalStageStock.setTitle("Agregar Stock - Cantidad");
            modalStageStock.initModality(Modality.APPLICATION_MODAL);
            modalStageStock.setScene(new Scene(rootStock));
            modalStageStock.showAndWait();

            // 4. Procesar resultado de cantidad de stock
            if (stockController.isAccepted()) {
                int quantity = stockController.getCantidad();

                // --- INICIO DEL BLOQUE CRÍTICO DE VERIFICACIÓN (Punto 2) ---

                // 5. VERIFICAR DISPONIBILIDAD DE PIEZAS (Lanza IOException si faltan)
                // Usamos el servicio de catálogo para verificar el stock
                catalogService.verifyPieceAvailability(masterCode, quantity);

                // 6. AUMENTAR STOCK FINAL (SOLO se ejecuta si la verificación NO lanza excepción)
                masterProductRepository.increaseStock(masterCode, quantity);

                // Confirmar y Recargar
                showAlert(AlertType.INFORMATION, "Éxito",
                        "Se agregaron +" + quantity + " unidades a la existencia de " + selectedProduct.getProductName());
                loadProductStockData(); // Recargar datos
            }

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error de Sistema/Stock", "Error: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            showAlert(AlertType.ERROR, "Error de Entrada", "La entrada no es un número válido.");
        } catch (RuntimeException e) {
            showAlert(AlertType.ERROR, "Error de Base de Datos", "No se pudo actualizar el stock: " + e.getMessage());
        }
    }

    /**
     * ACCIÓN 2: Modificar Stock de Pieza Individual (G-code).
     * Se dispara al presionar "MODIFICAR PIEZA (G-Code)".
     */
    @FXML
    private void handleModifyPieceStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getParent() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una Pieza (Línea Hijo) para registrar su producción individual.");
            return;
        }

        MasterProduct selectedPiece = selectedItem.getValue();

        try {
            // Cargar la ventana modal de Carga de Pieza
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddPieceStockModal.fxml"));
            Parent root = loader.load();
            AddPieceStockController controller = loader.getController();

            // Pasar datos de la pieza al controlador
            controller.setPieceData(selectedPiece.getMasterCode(), selectedPiece.getProductName());

            Stage modalStage = new Stage();
            modalStage.setTitle("Registro de Producción");
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setScene(new Scene(root));
            modalStage.showAndWait();

            // Lógica de Stock de Pieza: Aquí iría la implementación de RF9 (consumo de filamento)
            if (controller.isProductionRegistered()) {
                // Future: Llamar a logicService.registerPiece(pieceCode, controller.getSelectedColors());
                loadProductStockData(); // Recargar para ver los cambios de stock si se implementan
            }

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error de Interfaz", "No se pudo cargar la ventana de registro de pieza: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private Optional<ButtonType> showAlertConfirmation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("hubi-dialog"); // Asumimos que este estilo existe en styles.css
        return alert.showAndWait();
    }
    @FXML
    private void handleDeletePiece(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getParent() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una Pieza (Línea Hijo) para eliminarla.");
            return;
        }

        // Validación jerárquica: Debe ser un nodo hijo (su padre NO es la raíz invisible)
        if (selectedItem.getParent().getValue() == null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo puede eliminar piezas individuales.");
            return;
        }

        MasterProduct pieceToDelete = selectedItem.getValue();
        String pieceCode = pieceToDelete.getMasterCode();

        // 2. Confirmación (Usa el método auxiliar que acabamos de implementar)
        Optional<ButtonType> result = showAlertConfirmation("Confirmar Eliminación de Pieza",
                "¿Está seguro de eliminar la pieza con código '" + pieceCode + "'?\nSe eliminará el archivo del catálogo y su registro en la BD.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // 3. Llamar al servicio para eliminar por código
                // ESTO RESUELVE EL ERROR DE COMPILACIÓN EN LA LLAMADA AL SERVICIO
                catalogService.deletePieceByCode(pieceCode);

                showAlert(AlertType.INFORMATION, "Éxito", "Pieza '" + pieceCode + "' eliminada correctamente.");
                loadProductStockData(); // Recargar el TreeTableView para reflejar el cambio

            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error de Eliminación", "No se pudo eliminar la pieza: " + e.getMessage());
            } catch (Exception e) {
                showAlert(AlertType.ERROR, "Error Inesperado", "Ocurrió un error al eliminar la pieza: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    /**
     * ACCIÓN: Elimina una unidad del stock disponible (Decrease Stock).
     * Nota: Esto no elimina la PIEZA ni el PRODUCTO MAESTRO, solo el contador de unidades.
     */
    @FXML
    private void handleRemoveStockUnit(ActionEvent event) {
        // 1. Obtener y validar selección de nodo Padre
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getParent() != null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione un Producto Maestro (Línea Padre) para eliminar stock.");
            return;
        }

        MasterProduct selectedProduct = selectedItem.getValue();
        String masterCode = selectedProduct.getMasterCode();

        // Asumimos que es MasterProductView para obtener el stock actual
        int currentAvailable = ((MasterProductView)selectedProduct).getQuantityAvailable();


        // 2. Solicitar la cantidad a eliminar (usamos TextInputDialog, como en handleAddProductStock)
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Eliminar Stock");
        dialog.setHeaderText("Producto: " + selectedProduct.getProductName());
        dialog.setContentText("Ingrese la cantidad de unidades a eliminar (Disponibles: " + currentAvailable + "):");
        dialog.getDialogPane().getStyleClass().add("hubi-dialog");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            int quantityToRemove = 0;
            try {
                // 3. Validación y Conversión
                quantityToRemove = Integer.parseInt(result.get());
                if (quantityToRemove <= 0 || quantityToRemove > currentAvailable) {
                    showAlert(AlertType.ERROR, "Error de Entrada",
                            "La cantidad a eliminar debe ser mayor a 0 y no puede exceder las unidades disponibles (" + currentAvailable + ").");
                    return;
                }

                // 4. LÓGICA RF4: Disminuir stock
                // Llama al método que verifica el stock antes de restar
                masterProductRepository.decreaseStock(masterCode, quantityToRemove);

                showAlert(AlertType.INFORMATION, "Éxito", "Se eliminaron " + quantityToRemove + " unidades del stock.");
                loadProductStockData(); // Recargar datos

            } catch (NumberFormatException e) {
                showAlert(AlertType.ERROR, "Error de Entrada", "La entrada no es un número válido.");
            } catch (RuntimeException e) {
                // Captura el error de 'Stock insuficiente' de la capa de persistencia
                showAlert(AlertType.ERROR, "Error de Eliminación",
                        "No se pudo eliminar el stock. Razón: " + e.getMessage());
            }
        }
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
    @FXML
    private void handleDeletePieceStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una Pieza (Línea Hijo) para eliminar stock.");
            return;
        }

        // La acción debe operar en el nivel de PIEZA (Nodo Hijo)
        if (selectedItem.getParent() == null || selectedItem.getParent().getValue() == null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo puede eliminar stock individual de las Piezas (Nivel Hijo).");
            return;
        }

        MasterProduct selectedPiece = selectedItem.getValue();
        // Asumimos que la pieza seleccionada en la UI es una MasterProductView o un nodo hijo que queremos borrar.

        // 1. Cargar el modal de eliminación de stock de pieza (RemovePieceStockController)
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/RemovePieceStockModal.fxml"));
            Parent root = loader.load();
            RemovePieceStockController controller = loader.getController();

            // 2. Pasar el nombre de la pieza (sin extensión) y su código
            String pieceNameBase = selectedPiece.getProductName().substring(0, selectedPiece.getProductName().lastIndexOf('.'));

            // NOTA: Para eliminar, necesitas saber qué COLORES tiene stock (usar getStockByPieceNameBase para llenar el ComboBox)
            // Asumo que el RemovePieceStockController manejará esta lógica.

            controller.setPieceData(selectedPiece.getMasterCode(), pieceNameBase);

            Stage modalStage = new Stage();
            modalStage.setTitle("Eliminar Stock de Pieza");
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setScene(new Scene(root));
            modalStage.showAndWait();

            // 3. Procesar la eliminación
            if (controller.isRemovalConfirmed()) {
                String color = controller.getSelectedColor();
                int quantity = controller.getQuantityToRemove();

                // Llama al método que acabamos de implementar
                productRepository.decreasePieceStockQuantity(pieceNameBase, color, quantity);

                showAlert(AlertType.INFORMATION, "Éxito",
                        quantity + " unidades de " + pieceNameBase + " (" + color + ") eliminadas.");
                loadProductStockData();
            }

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error de Interfaz", "No se pudo cargar la ventana de eliminación de stock: " + e.getMessage());
        } catch (RuntimeException e) {
            showAlert(AlertType.ERROR, "Error de Stock", "Fallo: " + e.getMessage());
        }
    }
    @FXML
    private void handleDeleteStockColorUnit(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        // Validaciones iniciales de selección
        if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getParent() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una fila de Color (Nivel 3) para descontar.");
            return;
        }

        MasterProduct item = selectedItem.getValue();

        // 1. Validar que es un NODO NIETO (Nivel 3: El color) y extraer el stock actual
        if (!(item instanceof MasterProductView)) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo se puede descontar de una línea de Color.");
            return;
        }

        MasterProductView colorNode = (MasterProductView) item;
        int currentAvailable = colorNode.getQuantityAvailable(); // Stock actual del color

        if (currentAvailable <= 0) {
            showAlert(AlertType.WARNING, "Stock Insuficiente", "El color seleccionado no tiene stock disponible para descontar.");
            return;
        }

        // 2. Extracción de datos CRÍTICOS
        // --- CORRECCIÓN DEL NPE ---
        TreeItem<MasterProduct> pieceNodeItem = selectedItem.getParent();

        if (pieceNodeItem == null || pieceNodeItem.getValue() == null) {
            showAlert(AlertType.ERROR, "Error de Datos", "No se pudo identificar la pieza base. Seleccione un Color válido.");
            return;
        }
        // --------------------------

        MasterProduct pieceNode = pieceNodeItem.getValue(); // Nodo Padre: La pieza genérica
        String pieceNameOriginal = pieceNode.getProductName();
        String pieceNameBase = pieceNameOriginal.substring(0, pieceNameOriginal.lastIndexOf('.'));
        String colorName = colorNode.getProductName().replaceFirst("\\s*\\(\\d+ uds\\)", ""); // Limpiar el nombre del nodo para obtener solo el color

        // 3. SOLICITAR LA CANTIDAD A ELIMINAR (usando TextInputDialog)
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Eliminar Stock de Pieza");
        dialog.setHeaderText("Pieza: " + pieceNameBase + " | Color: " + colorName);
        dialog.setContentText("Ingrese la cantidad de unidades a eliminar (Disponibles: " + currentAvailable + "):");
        dialog.getDialogPane().getStyleClass().add("hubi-dialog");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            int quantityToRemove;
            try {
                // Validación y Conversión
                quantityToRemove = Integer.parseInt(result.get());

                if (quantityToRemove <= 0) {
                    showAlert(AlertType.ERROR, "Error de Entrada", "La cantidad debe ser mayor a 0.");
                    return;
                }

                if (quantityToRemove > currentAvailable) {
                    showAlert(AlertType.ERROR, "Error de Stock",
                            "La cantidad a eliminar (" + quantityToRemove + ") no puede exceder las unidades disponibles (" + currentAvailable + ").");
                    return;
                }

                // 4. Lógica de Persistencia: Llamar al método de disminución de stock por color
                productRepository.decreasePieceStockQuantity(pieceNameBase, colorName, quantityToRemove);

                showAlert(AlertType.INFORMATION, "Éxito", quantityToRemove + " unidades de " + pieceNameBase + " (" + colorName + ") descontadas.");
                loadProductStockData(); // Recargar datos para actualizar la vista

            } catch (NumberFormatException e) {
                showAlert(AlertType.ERROR, "Error de Entrada", "Por favor, ingrese un número entero válido.");
            } catch (RuntimeException e) {
                // Captura el error de 'Stock insuficiente' o la falla SQL
                showAlert(AlertType.ERROR, "Error de Stock", "Fallo al eliminar stock: " + e.getMessage());
            }
        }
    }
    /**
     * ACCIÓN: Abre el modal para eliminar stock de Producto Maestro,
     * descontando piezas por color según la composición.
     */
    @FXML
    private void handleDeleteProductStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione un Producto Maestro (Línea Padre) para eliminar stock por composición.");
            return;
        }

        // Validación jerárquica: Debe ser el nodo Padre (su padre es la raíz invisible, cuyo .getValue() es null)
        if (selectedItem.getParent() == null || selectedItem.getParent().getValue() != null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo puede eliminar stock por composición seleccionando la línea principal (Producto Maestro).");
            return;
        }

        MasterProduct selectedProduct = selectedItem.getValue();

        try {
            // 1. Cargar el FXML y obtener el controlador del modal
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/RemoveProductStockModal.fxml"));
            Parent root = loader.load();
            RemoveProductStockController controller = loader.getController();

            // 2. Inyectar dependencias (usando las ya definidas en InventarioController)
            controller.setDependencies(catalogService, productCompositionRepository, productRepository);

            // 3. Inicializar datos del modal
            // Nota: MasterProductView hereda de MasterProduct, por lo que selectedProduct funciona.
            controller.initData(selectedProduct);

            // 4. Mostrar la ventana emergente (modal)
            Stage modalStage = new Stage();
            modalStage.setTitle("Eliminar Stock de Producto - " + selectedProduct.getProductName());
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setScene(new Scene(root));
            modalStage.showAndWait();

            // 5. Recargar datos después de cerrar el modal para reflejar los cambios
            loadProductStockData();

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error de Interfaz", "No se pudo cargar la ventana de deducción de stock: " + e.getMessage());
            e.printStackTrace();
        }
    }
}