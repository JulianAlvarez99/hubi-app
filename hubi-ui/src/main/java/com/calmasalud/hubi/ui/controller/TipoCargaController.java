package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.core.service.CatalogService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView; // Importar
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane; // Importar
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files; // Importar
import java.nio.file.Path; // Importar
import java.util.ArrayList; // Importar
import java.util.Arrays; // Importar
import java.util.List;
import java.util.stream.Collectors; // Importar

public class TipoCargaController {

    // --- Inyección de Dependencia ---
    private final IProductRepository productSqliteRepository = new ProductRepositorySQLite();
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository);
    // ---------------------------------

    // MODIFICADO: De File a List<File>
    private List<File> archivosSeleccionados = new ArrayList<>();

    // CAMPOS FXML
    @FXML private RadioButton radioProducto;
    @FXML private RadioButton radioPieza;
    @FXML private TextField txtNombre;
    @FXML private Button btnBuscarDirectorio;
    @FXML private Button btnSeleccionarArchivo;

    // NUEVOS/MODIFICADOS FXML IDs
    @FXML private Label lblStatus; // Reemplaza a lblArchivoSeleccionado
    @FXML private Button btnSeleccionarDirectorio;
    @FXML private StackPane dropZone; // Cambiado de VBox a StackPane
    @FXML private Label lblDropZone;
    @FXML private ListView<String> fileListView;

    @FXML private Button btnCargar;
    @FXML private Button btnCancelar;
    @FXML private VBox rootVBox;

    private File directorioProducto;
    private boolean cargaExitosa = false;

    private static final String REPOSITORIO_BASE_PATH =
            System.getProperty("user.home") + File.separator + "SistemaHUBI" + File.separator + "RepositorioArchivos";


    // --- Métodos ---

    // ELIMINADO: setArchivoSeleccionado(File archivo)

    public void setDirectorioProducto(File directorioProducto) {
        // ... (sin cambios) ...
        this.directorioProducto = directorioProducto;
        javafx.application.Platform.runLater(() -> {
            if (this.directorioProducto != null) {
                radioPieza.setSelected(true);
            } else {
                radioProducto.setSelected(true);
            }
        });
    }

    public boolean isCargaExitosa() {
        return cargaExitosa;
    }

    @FXML
    public void initialize() {
        // --- Configuración Drag and Drop ---
        dropZone.setOnDragOver(this::handleDragOver);
        dropZone.setOnDragExited(this::handleDragExited);
        dropZone.setOnDragDropped(this::handleDragDropped);
        // --- FIN Configuración Drag and Drop ---

        actualizarEstadoCampos(radioProducto.isSelected());
        updateFileDisplay(); // Limpia la vista al inicio

        radioProducto.selectedProperty().addListener((obs, oldValue, isProducto) -> {
            if (isProducto) {
                actualizarEstadoCampos(true);
            }
        });

        radioPieza.selectedProperty().addListener((obs, oldValue, isPieza) -> {
            if (isPieza) {
                actualizarEstadoCampos(false);
            }
        });
    }

    /**
     * Valida la extensión de un archivo.
     */
    private boolean esArchivoValido(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".stl") || name.endsWith(".3mf") || name.endsWith(".gcode");
    }

    /**
     * NUEVO: Actualiza la UI (ListView/Labels) basado en la lista de archivos.
     */
    private void updateFileDisplay() {
        if (archivosSeleccionados.isEmpty()) {
            lblStatus.setText("Ningún archivo seleccionado.");
            fileListView.getItems().clear();
            fileListView.setVisible(false);
            fileListView.setManaged(false);
            lblDropZone.setVisible(true);
            lblDropZone.setManaged(true);
        } else {
            List<String> fileNames = archivosSeleccionados.stream()
                    .map(File::getName)
                    .collect(Collectors.toList());
            fileListView.setItems(FXCollections.observableArrayList(fileNames));

            lblStatus.setText(archivosSeleccionados.size() + " archivo(s) seleccionado(s).");
            fileListView.setVisible(true);
            fileListView.setManaged(true);
            lblDropZone.setVisible(false);
            lblDropZone.setManaged(false);
        }

        // --- REGLA DE NEGOCIO ---
        // Si hay más de 1 archivo, forzar modo "Pieza".
        // Modo "Producto" solo disponible si hay exactamente 1 archivo.
        if (archivosSeleccionados.size() > 1) {
            radioProducto.setDisable(true);
            radioPieza.setSelected(true);
        } else {
            // Habilitar solo si el directorio de producto NO está pre-seleccionado
            radioProducto.setDisable(this.directorioProducto != null);
        }

        // Actualizar el estado del campo de texto
        actualizarEstadoCampos(radioProducto.isSelected() && !radioProducto.isDisabled());
    }

    // MODIFICADO: Para selección múltiple
    @FXML
    private void handleSeleccionarArchivos() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivo(s) de Diseño/Impresión (.stl, .3mf, .gcode)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modelos 3D y GCode", "*.stl", "*.3mf", "*.gcode"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );
        // MODIFICADO: showOpenMultipleDialog
        List<File> selected = fileChooser.showOpenMultipleDialog(rootVBox.getScene().getWindow());

        if (selected != null && !selected.isEmpty()) {
            // Sobrescribir la lista con los archivos seleccionados válidos
            archivosSeleccionados = selected.stream()
                    .filter(this::esArchivoValido)
                    .collect(Collectors.toList());
            updateFileDisplay();
        }
    }

    // NUEVO: Para seleccionar directorio
    @FXML
    private void handleSeleccionarDirectorio() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Seleccionar Directorio con Archivos de Impresión");
        File selectedDir = directoryChooser.showDialog(rootVBox.getScene().getWindow());

        if (selectedDir != null && selectedDir.isDirectory()) {
            try {
                // Sobrescribir la lista con los archivos válidos del directorio
                archivosSeleccionados = Files.list(selectedDir.toPath())
                        .map(Path::toFile)
                        .filter(this::esArchivoValido)
                        .collect(Collectors.toList());
                updateFileDisplay();
            } catch (IOException e) {
                mostrarAlerta(AlertType.ERROR, "Error de Lectura", "No se pudo leer el contenido del directorio.");
                archivosSeleccionados.clear();
                updateFileDisplay();
            }
        }
    }

    // --- Handlers de Drag & Drop ---

    @FXML
    private void handleDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            dropZone.getStyleClass().add("drop-zone-drag-over");
        } else {
            event.consume();
        }
    }

    @FXML
    private void handleDragExited(DragEvent event) {
        dropZone.getStyleClass().remove("drop-zone-drag-over");
        event.consume();
    }

    // MODIFICADO: Para aceptar múltiples archivos Y directorios
    @FXML
    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            archivosSeleccionados.clear(); // Limpiar lista
            List<File> files = db.getFiles();

            for (File file : files) {
                if (file.isDirectory()) {
                    // Si es un directorio, añadir sus archivos válidos (no recursivo)
                    try {
                        List<File> filesInDir = Files.list(file.toPath())
                                .map(Path::toFile)
                                .filter(this::esArchivoValido)
                                .collect(Collectors.toList());
                        archivosSeleccionados.addAll(filesInDir);
                    } catch (IOException e) {
                        System.err.println("No se pudo leer el directorio arrastrado: " + file.getName());
                    }
                } else {
                    // Si es un archivo, validarlo
                    if (esArchivoValido(file)) {
                        archivosSeleccionados.add(file);
                    }
                }
            }
            success = true;
            updateFileDisplay();
        }
        event.setDropCompleted(success);
        event.consume();
        dropZone.getStyleClass().remove("drop-zone-drag-over");
    }
    // --- FIN: Handlers de Drag & Drop ---


    private void actualizarEstadoCampos(boolean esProducto) {
        // ... (sin cambios) ...
        txtNombre.clear();
        btnBuscarDirectorio.setVisible(false);

        if (esProducto) {
            txtNombre.setEditable(true);
            txtNombre.setDisable(false);
            txtNombre.setPromptText("Ingrese nombre del NUEVO producto (Ej: SOPORTE)");
        } else {
            txtNombre.setEditable(false);
            txtNombre.setDisable(true);
            if (directorioProducto != null && directorioProducto.isDirectory()) {
                txtNombre.setText(directorioProducto.getAbsolutePath());
                txtNombre.setPromptText("Pieza asociada a: " + directorioProducto.getName());
            } else {
                txtNombre.setText("");
                txtNombre.setPromptText("PIEZA: SELECCIONE Producto en Catálogo");
            }
        }
    }

    // MODIFICADO: Para manejar la lista
    @FXML
    public void onCargarClicked(ActionEvent event) {
        String valorIngresado = txtNombre.getText();
        boolean esPieza = radioPieza.isSelected();

        // Validación 1: Archivo(s) seleccionado(s)
        if (this.archivosSeleccionados.isEmpty()) {
            mostrarAlerta(AlertType.ERROR, "Archivos Requeridos", "Debe seleccionar o soltar al menos un archivo (.stl, .3mf, .gcode) para cargar.");
            return;
        }

        // Validación 2: Específica por modo
        if (esPieza) {
            if (directorioProducto == null || !directorioProducto.isDirectory()) {
                mostrarAlerta(AlertType.ERROR, "Producto Requerido (Pieza)",
                        "Para cargar PIEZAS, primero seleccione la carpeta del PRODUCTO correspondiente en el Gestor de Catálogo.");
                return;
            }
            valorIngresado = directorioProducto.getAbsolutePath(); // Ruta del producto existente

        } else { // Modo Producto
            if (archivosSeleccionados.size() > 1) {
                // Esta validación es por si acaso, la UI ya debería haberlo forzado a "Pieza"
                mostrarAlerta(AlertType.ERROR, "Error de Lógica", "No se pueden crear múltiples productos a la vez. Seleccione el modo 'PIEZA'.");
                return;
            }
            if (valorIngresado == null || valorIngresado.trim().isEmpty()) {
                mostrarAlerta(AlertType.ERROR, "Nombre Requerido (Producto)",
                        "Debe ingresar un NOMBRE para el nuevo producto.");
                return;
            }
            if (valorIngresado.trim().length() < 3) {
                mostrarAlerta(AlertType.WARNING, "Nombre Inválido", "El nombre del producto debe tener al menos 3 caracteres para generar el código.");
                return;
            }
        }

        // Lógica de carga (MODIFICADA CON BUCLE)
        try {
            int archivosCargados = 0;
            int archivosFallidos = 0;
            StringBuilder errores = new StringBuilder();

            for (File archivo : this.archivosSeleccionados) {
                try {
                    if (esPieza) {
                        catalogoService.procesarCargaPieza(archivo, valorIngresado); // valorIngresado es la ruta del producto
                    } else {
                        // Solo entra aquí si es 1 archivo y modo Producto
                        catalogoService.procesarCargaProducto(archivo, valorIngresado.trim()); // valorIngresado es el nombre del nuevo producto
                    }
                    archivosCargados++;
                } catch (IllegalArgumentException | IOException e) {
                    // Captura errores de lógica de negocio (ej. nombre < 3) o I/O (ej. no se pudo copiar)
                    archivosFallidos++;
                    errores.append(archivo.getName()).append(": ").append(e.getMessage()).append("\n");
                } catch (Exception e) {
                    // Captura errores inesperados (ej. base de datos)
                    archivosFallidos++;
                    errores.append(archivo.getName()).append(": Error inesperado (").append(e.getClass().getSimpleName()).append(")\n");
                    e.printStackTrace(); // Loguear el stack trace
                }
            }

            // Reportar resultado
            if (archivosFallidos == 0) {
                String tipo = (archivosCargados > 1) ? "archivos" : (esPieza ? "pieza" : "producto");
                mostrarAlerta(AlertType.INFORMATION, "Éxito", archivosCargados + " " + tipo + " cargado(s) correctamente.");
                this.cargaExitosa = true;
                closeWindow(event);
            } else {
                mostrarAlerta(AlertType.WARNING, "Carga Parcial",
                        "Cargados: " + archivosCargados + "\nFallidos: " + archivosFallidos + "\n\nErrores:\n" + errores.toString());
                this.cargaExitosa = (archivosCargados > 0); // Fue exitosa si al menos uno se cargó
                if (this.cargaExitosa) { // Si algo se cargó, cerrar. Si  falló, quedarse.
                    closeWindow(event);
                }
            }

        } catch (Exception e) { // Error general (improbable que se alcance)
            mostrarAlerta(AlertType.ERROR, "Error Inesperado", "Ocurrió un error no previsto: " + e.getMessage());
            this.cargaExitosa = false;
            e.printStackTrace();
        }
    }

    @FXML
    public void onCancelarClicked(ActionEvent event) {
        // ... (sin cambios) ...
        this.cargaExitosa = false;
        closeWindow(event);
    }

    private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        // ... (sin cambios) ...
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }

    private void closeWindow(ActionEvent event) {
        ((Node) (event.getSource())).getScene().getWindow().hide();
    }

    @FXML
    public void onBuscarProductoClicked(ActionEvent event) {
        System.out.println("onBuscarProductoClicked llamado, pero el botón debería estar oculto.");
    }
}