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

    // --- Inyección de Dependencia ---
    // 1. Crear la instancia de la implementación del repositorio
    private final IProductRepository productSqliteRepository = new ProductRepositorySQLite();
    // 2. Crear el servicio de catálogo, inyectando la implementación
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository);
    // ---------------------------------

    // Campo para mantener el directorio seleccionado
    private File currentSelectedDirectory = null;

    // --- Panel Izquierdo (Explorador) ---
    @FXML private TextField txtBusqueda;
    @FXML private Button btnActualizar;

    // --- Componentes del explorador actualizados ---
    @FXML private TreeView<File> folderTreeView;
    @FXML private TableView<File> fileTableView;
    @FXML private TableColumn<File, String> colNombre;
    @FXML private TableColumn<File, String> colTamaño; // Representa Código Único
    @FXML private TableColumn<File, String> colFechaMod;

    @FXML private Button btnAgregar;
    @FXML private Button btnEliminar;
    @FXML private Button btnExtraer;

    // --- Panel Derecho (Parámetros) ---
    @FXML private Label lblNombreArchivo;
    @FXML private StackPane visor3DPlaceholder;
    @FXML private TextField paramPeso;
    @FXML private TextField paramLargo;
    @FXML private TextField paramTipoFilamento;
    @FXML private TextField paramTemperatura;
    @FXML private TextField paramVelocidad;
    @FXML private TextField paramTiempo;
    @FXML private TextField paramDensidad;
    @FXML private TextField paramAlturaCapa;
    @FXML private TextField paramCosto;
    private static final File REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos").toFile();

    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (v2.1 con Tree/Table) inicializado.");

        // Configuración CellFactory para TreeView (mostrar nombres amigables)
        folderTreeView.setCellFactory(param -> new TreeCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.equals(REPOSITORIO_BASE)) {
                    setText("Repositorio Master"); // Etiqueta raíz
                } else {
                    setText(item.getName()); // Nombre de carpeta/archivo
                }
            }
        });

        // Listener para cambios de selección en TreeView
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            File selectedFile = (newValue != null) ? newValue.getValue() : null;

            if (selectedFile != null && selectedFile.isDirectory() && !selectedFile.equals(REPOSITORIO_BASE)) {
                currentSelectedDirectory = selectedFile; // Directorio de producto seleccionado
                cargarDetallesCarpeta(selectedFile);
            } else {
                currentSelectedDirectory = null; // Limpiar si es la raíz o no es directorio válido
                if (selectedFile != null && selectedFile.isDirectory()) {
                    cargarDetallesCarpeta(selectedFile); // Cargar contenido de la raíz si se selecciona
                } else {
                    fileTableView.getItems().clear(); // Limpiar tabla si no hay selección válida
                }
            }
        });

        // Listener para deseleccionar al hacer clic en área vacía del TreeView
        folderTreeView.setOnMouseClicked(event -> {
            Node node = event.getPickResult().getIntersectedNode();
            // Buscar si el clic fue dentro de una celda
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }
            // Si no fue en una celda o la celda está vacía, deseleccionar
            if (node == null || ((TreeCell<?>) node).isEmpty()) {
                if (folderTreeView.getSelectionModel().getSelectedItem() != null) {
                    // Usar Timeline para deseleccionar en el siguiente pulso de UI
                    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1), e ->
                            folderTreeView.getSelectionModel().clearSelection()));
                    timeline.play();
                }
            }
        });

        // Configuración de las columnas de la TableView
        // Columna Nombre (muestra Product.name si existe, sino nombre de archivo)
        colNombre.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            String fileName = archivo.getName();
            String code = "";
            if (fileName.contains(".")) {
                code = fileName.substring(0, fileName.lastIndexOf('.'));
            } else {
                return new SimpleStringProperty(fileName); // Fallback si no hay extensión
            }

            Product product = catalogoService.getProductDetails(code);
            String nameToShow = (product != null && product.getName() != null) ? product.getName() : fileName;
            return new SimpleStringProperty(nameToShow);
        });

        // Columna Código Único (antes Tamaño)
        colTamaño.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            String fileName = archivo.getName();
            String code = "";
            if (fileName.contains(".")) {
                code = fileName.substring(0, fileName.lastIndexOf('.'));
            } else {
                code = fileName; // Fallback
            }
            return new ReadOnlyObjectWrapper<>(code);
        });

        // Columna Fecha Modificación
        colFechaMod.setCellValueFactory(cellData -> {
            long timestamp = cellData.getValue().lastModified();
            Date fecha = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            return new ReadOnlyObjectWrapper<>(sdf.format(fecha));
        });

        // Carga inicial del TreeView y TableView
        refrescarVistaCatalogo();
    }

    // method auxiliar para cargar archivos de un directorio en la TableView
    private void cargarDetallesCarpeta(File directorio) {
        fileTableView.getItems().clear();
        File[] archivos = directorio.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                String filename = f.getName().toLowerCase();
                // Filtrar solo archivos con extensiones permitidas y que no sean ocultos
                if (f.isFile() && !f.getName().startsWith(".") &&
                        (filename.endsWith(".stl") || filename.endsWith(".3mf") || filename.endsWith(".gcode"))) {
                    fileTableView.getItems().add(f);
                }
            }
        }
    }

    // Manejador del botón AGREGAR
    @FXML
    public void handleAgregarClick() {
        try {
            mostrarModalTipoCarga(currentSelectedDirectory);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "No se pudo abrir la ventana para agregar archivo: " + e.getMessage());
            System.err.println("Error al cargar la ventana de tipo de carga: " + e.getMessage());
        }
    }

    // Muestra la ventana modal para elegir tipo de carga (Producto o Pieza)
    private void mostrarModalTipoCarga(File directorioProductoSeleccionado) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/TipoCargaModal.fxml"));
        Parent root = loader.load();
        TipoCargaController controller = loader.getController();

        // Pasar el directorio seleccionado (puede ser null) al controlador del modal
        controller.setDirectorioProducto(directorioProductoSeleccionado);

        Stage modalStage = new Stage();
        modalStage.setTitle("AGREGAR ARCHIVO AL CATÁLOGO");
        modalStage.initModality(Modality.APPLICATION_MODAL);
        // modalStage.initOwner(btnAgregar.getScene().getWindow()); // Opcional: Centrar relativo a la ventana principal
        modalStage.setScene(new Scene(root));
        modalStage.showAndWait(); // Esperar a que el modal se cierre

        // Si la carga fue exitosa (indicado por el controlador del modal), refrescar la vista principal
        if (controller.isCargaExitosa()) {
            refrescarVistaCatalogo();
        }
    }

    // Refresca el TreeView y TableView leyendo del disco
    @FXML // Hacerlo accesible desde FXML si se vincula a btnActualizar
    public void refrescarVistaCatalogo() {
        System.out.println("Refrescando la vista del catálogo...");

        File previouslySelectedDirectory = null;
        TreeItem<File> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            previouslySelectedDirectory = selectedItem.getValue();
        }

        if (REPOSITORIO_BASE.exists() && REPOSITORIO_BASE.isDirectory()) {
            TreeItem<File> rootItem = createNode(REPOSITORIO_BASE);
            folderTreeView.setRoot(rootItem);
            folderTreeView.setShowRoot(false); // No mostrar "Repositorio Master" como nodo visible

            // Intentar re-seleccionar y expandir
            TreeItem<File> itemToSelect = findTreeItem(rootItem, previouslySelectedDirectory);
            if (itemToSelect != null) {
                folderTreeView.getSelectionModel().select(itemToSelect);
                // Expandir ancestros para asegurar visibilidad
                TreeItem<File> parent = itemToSelect.getParent();
                while(parent != null && !parent.equals(rootItem)){
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
                itemToSelect.setExpanded(true); // Expandir el nodo seleccionado
                if (!itemToSelect.getValue().equals(REPOSITORIO_BASE)) { // Si no es la raíz, cargar tabla
                    cargarDetallesCarpeta(itemToSelect.getValue());
                } else { // Si es la raíz, cargar su contenido
                    cargarDetallesCarpeta(REPOSITORIO_BASE);
                }

            } else { // Si no se encuentra o no había selección, seleccionar y cargar la raíz
                folderTreeView.getSelectionModel().select(rootItem);
                rootItem.setExpanded(true);
                cargarDetallesCarpeta(REPOSITORIO_BASE);
            }

            System.out.println("✅ Vista de Repositorio refrescada.");
        } else {
            showAlert(AlertType.ERROR, "Error de Repositorio", "El directorio base del repositorio no existe o no es accesible en: " + REPOSITORIO_BASE.getAbsolutePath());
            folderTreeView.setRoot(null); // Limpiar vista si hay error
            fileTableView.getItems().clear();
        }
    }

    // method recursivo para construir el TreeView
    private TreeItem<File> createNode(final File f) {
        // Crear nodo solo para el directorio base o subdirectorios
        if (!f.isDirectory() && !f.equals(REPOSITORIO_BASE)) {
            return null;
        }

        TreeItem<File> item = new TreeItem<>(f);
        item.setExpanded(f.equals(REPOSITORIO_BASE)); // Expandir la raíz por defecto

        File[] children = f.listFiles();
        if (children != null) {
            for (File childFile : children) {
                // Agregar solo subdirectorios que no sean ocultos
                if (childFile.isDirectory() && !childFile.getName().startsWith(".")) {
                    item.getChildren().add(createNode(childFile)); // Llamada recursiva
                }
            }
        }
        return item;
    }

    // method auxiliar recursivo para encontrar un TreeItem por su valor File
    private TreeItem<File> findTreeItem(TreeItem<File> root, File valueToFind) {
        if (root == null || valueToFind == null) {
            return null;
        }
        if (root.getValue() != null && root.getValue().equals(valueToFind)) {
            return root;
        }
        for (TreeItem<File> child : root.getChildren()) {
            TreeItem<File> found = findTreeItem(child, valueToFind);
            if (found != null) {
                return found;
            }
        }
        return null;
    }


    // Manejador del botón ELIMINAR
    @FXML
    public void handleEliminarClick() {
        // Prioridad 1: Selección en la Tabla (eliminar pieza)
        File selectedPieceFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedPieceFile != null) {
            showDeletePieceConfirmation(selectedPieceFile);
            return;
        }

        // Prioridad 2: Selección en el Árbol (eliminar producto/carpeta)
        TreeItem<File> selectedTreeItem = folderTreeView.getSelectionModel().getSelectedItem();
        File selectedDirectory = (selectedTreeItem != null) ? selectedTreeItem.getValue() : null;
        if (selectedDirectory != null && selectedDirectory.isDirectory() && !selectedDirectory.equals(REPOSITORIO_BASE)) {
            showDeleteProductConfirmation(selectedDirectory);
        } else {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una pieza en la tabla o una carpeta de producto en el árbol para eliminar.");
        }
    }

    // Muestra confirmación para eliminar Pieza
    private void showDeletePieceConfirmation(File pieceFile) {
        String code = pieceFile.getName();
        if (code.contains(".")) {
            code = code.substring(0, code.lastIndexOf('.'));
        }

        Optional<ButtonType> result = showConfirmationAlert("Confirmar Eliminación de Pieza",
                "¿Está seguro de eliminar la pieza con código '" + code + "'?\nSe eliminará el archivo y su registro en la base de datos.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                File parentDir = pieceFile.getParentFile(); // Guardar directorio padre antes de borrar
                catalogoService.deletePiece(pieceFile);
                refrescarVistaCatalogo(); // Refrescar toda la vista
                // Intentar re-seleccionar el directorio padre si aún existe
                TreeItem<File> parentItem = findTreeItem(folderTreeView.getRoot(), parentDir);
                if (parentItem != null) {
                    folderTreeView.getSelectionModel().select(parentItem);
                    cargarDetallesCarpeta(parentDir); // Recargar tabla del padre
                } else {
                    fileTableView.getItems().clear(); // Limpiar tabla si el padre fue eliminado
                    folderTreeView.getSelectionModel().clearSelection(); // Limpiar selección árbol
                }

            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error al Eliminar", "No se pudo eliminar la pieza: " + e.getMessage());
            }
        }
    }

    // Muestra confirmación para eliminar Producto (carpeta)
    private void showDeleteProductConfirmation(File productDirectory) {
        Optional<ButtonType> result = showConfirmationAlert("Confirmar Eliminación de Producto",
                "¡ATENCIÓN!\n¿Está seguro de eliminar el producto '" + productDirectory.getName() + "'?\nSe eliminará la carpeta completa, todos sus archivos y todos los registros asociados en la base de datos.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                catalogoService.deleteProduct(productDirectory);
                refrescarVistaCatalogo(); // Refrescar
                // Tras borrar un producto, la selección se pierde, la vista se refresca a la raíz por defecto.
            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error al Eliminar", "No se pudo eliminar el producto: " + e.getMessage());
            }
        }
    }

    // method auxiliar genérico para mostrar alertas de confirmación
    private Optional<ButtonType> showConfirmationAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        // --- AÑADIR CLASE CSS ---
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("hubi-dialog");
        // Opcional: Cargar la hoja de estilos completa si no la hereda
        // dialogPane.getStylesheets().add(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css").toExternalForm());
        // -------------------------
        return alert.showAndWait();
    }

    // method auxiliar genérico para mostrar alertas informativas/error
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        // --- AÑADIR CLASE CSS ---
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("hubi-dialog");
        // Opcional: Cargar la hoja de estilos completa si no la hereda
        // dialogPane.getStylesheets().add(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css").toExternalForm());
        // -------------------------
        alert.showAndWait();
    }
}