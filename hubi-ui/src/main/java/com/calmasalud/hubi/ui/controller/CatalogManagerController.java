package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.core.service.CatalogService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;


public class CatalogManagerController {

    private final CatalogService catalogoService = new CatalogService();
    // Campo para mantener el directorio seleccionado
    private File currentSelectedDirectory = null;

    // 2. Crear el servicio de catálogo, inyectando la implementación
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository);
    // --- Panel Izquierdo (Explorador) ---
    @FXML
    private TextField txtBusqueda;
    @FXML
    private Button btnActualizar;

    // --- Componentes del explorador actualizados ---
    @FXML
    private TreeView<File> folderTreeView;
    @FXML
    private TableView<File> fileTableView;
    @FXML
    private TableColumn<File, String> colNombre;
    @FXML
    private TableColumn<File, String> colTamaño;
    @FXML
    private TableColumn<File, String> colFechaMod;

    @FXML
    private Button btnAgregar;
    @FXML
    private Button btnEliminar;
    @FXML
    private Button btnExtraer;

    // --- Panel Derecho (Parámetros) ---
    @FXML
    private Label lblNombreArchivo;
    @FXML
    private StackPane visor3DPlaceholder;
    @FXML
    private TextField paramPeso;
    @FXML
    private TextField paramLargo;
    @FXML
    private TextField paramTipoFilamento;
    @FXML
    private TextField paramTemperatura;
    @FXML
    private TextField paramVelocidad;
    @FXML
    private TextField paramTiempo;
    @FXML
    private TextField paramDensidad;
    @FXML
    private TextField paramAlturaCapa;
    @FXML
    private TextField paramCosto;
    private static final File REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos").toFile();

    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (v2.1 con Tree/Table) inicializado.");

        // Se ejecuta al cargar la vista
        folderTreeView.setCellFactory(new Callback<TreeView<File>, TreeCell<File>>() {
            @Override
            public TreeCell<File> call(TreeView<File> param) {
                return new TreeCell<File>() {
                    @Override
                    protected void updateItem(File item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                        } else if (item.equals(REPOSITORIO_BASE)) {
                            // Etiqueta para el nodo raíz
                            setText("Repositorio Master");
                        } else {
                            // para mostrar solo el nombre (el archivo o carpeta)
                            setText(item.getName());
                        }
                    }
                };
            }
        });

        // 2. LISTENER DEL TREEVIEW
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                File selectedFile = newValue.getValue();

                // 1. Validar que sea un Directorio y NO la Raíz (REPOSITORIO_BASE)
                if (selectedFile != null && selectedFile.isDirectory() && !selectedFile.equals(REPOSITORIO_BASE)) {
                    currentSelectedDirectory = selectedFile; // <-- Selección de Producto Válida
                    cargarDetallesCarpeta(selectedFile);
                } else {
                    // Caso: Selección de la Raíz o de un Archivo
                    currentSelectedDirectory = null; // <-- Limpia la selección para Piezas
                    if (selectedFile != null && selectedFile.isDirectory()) {
                        cargarDetallesCarpeta(selectedFile); // Si es la Raíz, sigue mostrando su contenido
                    } else {
                        fileTableView.getItems().clear();
                    }
                }
            } else {
                // 2. Manejar la Deselección Completa (newValue == null)
                currentSelectedDirectory = null; // <-- Limpia la selección
                fileTableView.getItems().clear();
            }
        });

        // FORZAR DESELECCIÓN AL HACER CLIC EN ÁREA VACÍA
        folderTreeView.setOnMouseClicked(event -> {
            // 1. Obtener el nodo exacto que fue clicado
            Node node = event.getPickResult().getIntersectedNode();

            // 2. Recorrer la jerarquía hacia arriba para ver si el nodo clicado está dentro de una TreeCell.
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }

            // 3. Si no encontramos una TreeCell (node == null), o la TreeCell está vacía, es un clic en espacio en blanco.
            if (node == null || ((TreeCell<?>) node).isEmpty()) {

                // Forzar deselección si algo estaba seleccionado previamente.
                if (folderTreeView.getSelectionModel().getSelectedItem() != null) {

                    // Usar un micro-retardo para que la deselección ocurra en el siguiente ciclo de eventos,
                    // después de que el sistema haya terminado de procesar el clic.
                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1), e -> {
                        folderTreeView.getSelectionModel().clearSelection();
                    }));
                    timeline.play();
                }
            }
        });



        // --- COLUMNA NOMBRE: Ahora muestra el Name de la entidad Product ---
        colNombre.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            // 1. Obtener el código único del nombre del archivo (sin extensión)
            String fileName = archivo.getName();
            // Evitar error si el nombre de archivo no tiene punto (debería estar cubierto por el filtro de carga)
            if (!fileName.contains(".")) return new SimpleStringProperty(fileName);

            String code = fileName.substring(0, fileName.lastIndexOf('.'));

            // 2. Buscar el producto en la BD por el código único
            Product product = catalogoService.getProductDetails(code);

            // 3. Devolver el Nombre de la entidad o el nombre del archivo como fallback
            String nameToShow = (product != null && product.getName() != null) ? product.getName() : fileName;

            return new SimpleStringProperty(nameToShow);
        });

        // Columna CÓDIGO ÚNICO (RF8)
        colTamaño.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            String fileName = archivo.getName();
            if (!fileName.contains(".")) return new ReadOnlyObjectWrapper<>(fileName);

            // El código único es el nombre del archivo menos la extensión
            String code = fileName.substring(0, fileName.lastIndexOf('.'));
            return new ReadOnlyObjectWrapper<>(code);
        });

        // COLUMNA FECHA MODIFICACIÓN
        colFechaMod.setCellValueFactory(cellData -> {
            long timestamp = cellData.getValue().lastModified();
            Date fecha = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            return new ReadOnlyObjectWrapper<>(sdf.format(fecha));
        });

        // 4. CARGA INICIAL DE DATOS
        refrescarVistaCatalogo();
    }

    // El metodo formatSize ya no se usa para colTamaño, pero se mantiene por si se usa en otro lugar.
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void cargarDetallesCarpeta(File directorio) {
        fileTableView.getItems().clear();
        File[] archivos = directorio.listFiles();

        if (archivos != null) {
            for (File f : archivos) {

                String filename = f.getName().toLowerCase();
                if (f.isFile() && (filename.endsWith(".stl") || filename.endsWith(".3mf") || filename.endsWith(".gcode"))) {
                    fileTableView.getItems().add(f);
                }
            }
        }
    }

    /**
     * Implementa HU1 y RF1: Carga de archivos .stl y .3mf
     *
     */
    @FXML
    // Este es el metodo está vinculado al botón AGREGAR
    public void handleAgregarClick(){
        try {
            mostrarModalTipoCarga(currentSelectedDirectory);

        } catch (IOException e) {
            System.err.println("Error al cargar la ventana de tipo de carga: " + e.getMessage());
        }
    }

    // MODIFICADO: Método para lanzar la ventana modal
    private void mostrarModalTipoCarga(File directorioProducto) throws IOException {
        // Carga el FXML de la ventana modal
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/TipoCargaModal.fxml"));
        Parent root = loader.load();

        TipoCargaController controller = loader.getController();

        // Pasa el directorio seleccionado (NULL si no hay selección válida)
        controller.setDirectorioProducto(directorioProducto);

        // Configura y muestra la ventana modal
        Stage modalStage = new Stage();
        modalStage.setTitle("AGREGAR ARCHIVO");
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.setScene(new Scene(root));
        modalStage.showAndWait();

        if (controller.isCargaExitosa()) {
            refrescarVistaCatalogo(); // Llama al método de actualización inicial (lectura de disco)

        }
    }
    public void refrescarVistaCatalogo() {
        System.out.println("✅ El Repositorio Master ha cambiado. Refrescando la vista del catálogo...");

        //Guardar la carpeta previamente seleccionada.
        File previouslySelectedDirectory = null;
        TreeItem<File> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            previouslySelectedDirectory = selectedItem.getValue();
        }

        if (REPOSITORIO_BASE.exists()) {
            // 2. Reconstruir todo el TreeView.
            TreeItem<File> rootItem = createNode(REPOSITORIO_BASE);
            folderTreeView.setShowRoot(false);
            folderTreeView.setRoot(rootItem);

            // 3. CLAVE: Intentar restablecer la selección.
            if (previouslySelectedDirectory != null && previouslySelectedDirectory.exists()) {

                if (previouslySelectedDirectory.equals(REPOSITORIO_BASE)) {
                    folderTreeView.getSelectionModel().select(rootItem);
                    rootItem.setExpanded(true);
                }
                // Si el directorio previo era un subdirectorio (Producto):
                else {
                    // Seleccionamos la raíz y forzamos la recarga de la carpeta.
                    // Esto es un compromiso: puede que no seleccione el nodo exacto,
                    // pero dispara la actualización de la tabla.
                    folderTreeView.getSelectionModel().select(rootItem);
                    rootItem.setExpanded(true);

                    // Forzar la carga de la tabla con la ruta de la carpeta del producto (la más reciente).
                    cargarDetallesCarpeta(previouslySelectedDirectory);
                }
            }

            // 4. Si no se pudo restablecer la selección o no había nada seleccionado,
            // simplemente seleccionamos la raíz y recargamos la tabla.
            if (folderTreeView.getSelectionModel().getSelectedItem() == null) {
                folderTreeView.getSelectionModel().select(rootItem);
                rootItem.setExpanded(true);
                // Si no hay selección, aseguramos que la tabla cargue la raíz
                cargarDetallesCarpeta(REPOSITORIO_BASE);
            }

            System.out.println("✅ Vista de Repositorio refrescada.");
        } else {
            // ... (Lógica de error) ...
        }
    }
    private TreeItem<File> createNode(final File f) {
        if (!f.isDirectory() && !f.equals(REPOSITORIO_BASE)) {
            return null; // No crea un nodo para archivos
        }
        // El TreeItem contiene el objeto File ORIGINAL (f), la CellFactory se encarga de la visualización.
        TreeItem<File> root = new TreeItem<>(f);

        if (f.isDirectory()) {
            File[] children = f.listFiles();

            if (children != null && children.length > 0) {
                // Revisa si hay subdirectorios o archivos válidos dentro.
                boolean hasVisibleChildren = false;
                for (File childFile : children) {
                    // Si hay al menos un directorio hijo, o un archivo que queremos ver en la tabla

                    if (!childFile.getName().startsWith(".")) {
                        hasVisibleChildren = true;
                        break;
                    }
                }

                if (hasVisibleChildren) {
                    // Nodo 'dummy' para indicar que se puede expandir
                    root.getChildren().add(new TreeItem<>(null));
                }
            }

            root.setExpanded(false);
            root.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (isExpanded && root.getChildren().get(0) != null && root.getChildren().get(0).getValue() == null) {

                    root.getChildren().clear(); // Quitar el nodo dummy

                    // Iterar sobre los archivos reales (usando la ruta completa 'f')
                    for (File childFile : f.listFiles()) {
                        if (!childFile.getName().startsWith(".")) {

                            // Si el createNode() devuelve null, no se agrega.
                            TreeItem<File> childNode = createNode(childFile);
                            if (childNode != null) {
                                root.getChildren().add(childNode);
                            }
                        }
                    }
                }
            });
        }
        return root;
    }


    /**
     * Muestra un diálogo de advertencia o error simple.
     */
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void handleEliminarClick() {

        // 1. PRIORIDAD ALTA: Verificar la selección en la TableView (Pieza individual)
        File selectedPieceFile = fileTableView.getSelectionModel().getSelectedItem();

        if (selectedPieceFile != null) {
            // ELIMINAR PIEZA INDIVIDUAL
            showDeletePieceConfirmation(selectedPieceFile);
            return; // Detiene la ejecución si se eliminó la pieza
        }

        // 2. PRIORIDAD BAJA: Si no se seleccionó ninguna pieza, verificar el TreeView (Producto completo)
        TreeItem<File> selectedTreeItem = folderTreeView.getSelectionModel().getSelectedItem();
        File selectedFile = selectedTreeItem != null ? selectedTreeItem.getValue() : null;

        if (selectedFile != null && selectedFile.isDirectory() && !selectedFile.equals(REPOSITORIO_BASE)) {
            // ELIMINAR PRODUCTO COMPLETO (Solo si es un directorio y NO es la raíz)
            showDeleteProductConfirmation(selectedFile);
        } else {
            // 3. Ninguna selección válida encontrada.
            showAlert(AlertType.WARNING, "Selección Requerida", "Debe seleccionar una pieza individual en la tabla o una carpeta de Producto en el árbol para eliminar.");
        }
    }

    private Optional<ButtonType> showConfirmationAlert(String title, String content) {
        // ... (código sin cambios)
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        return alert.showAndWait();
    }

    private void showDeletePieceConfirmation(File pieceFile) {
        // Aseguramos que el archivo tenga extensión antes de intentar el substring
        int lastDotIndex = pieceFile.getName().lastIndexOf('.');
        String code = (lastDotIndex != -1) ? pieceFile.getName().substring(0, lastDotIndex) : pieceFile.getName();

        Optional<ButtonType> result = showConfirmationAlert("Confirmar Eliminación de Pieza",
                "¿Está seguro de eliminar la PIEZA con Código: " + code + "?\nEsta acción eliminará el registro de la BD y el archivo físico.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                catalogoService.deletePiece(pieceFile);
                refrescarVistaCatalogo();
                fileTableView.getSelectionModel().clearSelection();
            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error de Eliminación", "Fallo al eliminar el archivo/registro: " + e.getMessage());
            }
        }
    }

    private void showDeleteProductConfirmation(File productDirectory) {
        Optional<ButtonType> result = showConfirmationAlert("CONFIRMAR ELIMINACIÓN DE PRODUCTO",
                "ADVERTENCIA: ¿Está seguro de eliminar el Producto '" + productDirectory.getName() + "'?\nEsto eliminará TODAS las piezas asociadas (física y lógicamente), independientemente de su color.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                catalogoService.deleteProduct(productDirectory);
                refrescarVistaCatalogo();
                folderTreeView.getSelectionModel().clearSelection();
            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error de Eliminación", "Fallo al eliminar el directorio o sus archivos: " + e.getMessage());
            }
        }
    }
}