package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.core.service.CatalogService; // Asegúrate que esté importado
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent; // Importar
import javafx.scene.input.Dragboard; // Importar
import javafx.scene.input.TransferMode; // Importar
import javafx.scene.layout.VBox; // Importar VBox
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;


import java.io.File;
import java.io.IOException;
import java.util.List; // Importar List

public class TipoCargaController {

    // --- Inyección de Dependencia ---
    private final IProductRepository productSqliteRepository = new ProductRepositorySQLite();
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository);
    // ---------------------------------

    private File archivoSeleccionado;

    // CAMPOS FXML
    @FXML private RadioButton radioProducto;
    @FXML private RadioButton radioPieza;
    @FXML private TextField txtNombre;
    @FXML private Button btnBuscarDirectorio;
    @FXML private Button btnSeleccionarArchivo;
    @FXML private Label lblArchivoSeleccionado;
    @FXML private Button btnCargar;
    @FXML private Button btnCancelar;
    @FXML private VBox rootVBox; // Referencia al VBox raíz del FXML
    @FXML private VBox dropZone; // *** NUEVO ID PARA LA ZONA DE DROP ***

    private File directorioProducto;
    private boolean cargaExitosa = false;

    private static final String REPOSITORIO_BASE_PATH =
            System.getProperty("user.home") + File.separator + "SistemaHUBI" + File.separator + "RepositorioArchivos";


    // --- Métodos ---

    public void setArchivoSeleccionado(File archivo) {
        this.archivoSeleccionado = archivo;
        if (archivo != null) {
            lblArchivoSeleccionado.setText("Archivo: " + archivo.getName());
            // Opcional: Cambiar estilo para indicar éxito
            lblArchivoSeleccionado.setStyle("-fx-text-fill: green;");
        } else {
            lblArchivoSeleccionado.setText("Ningún archivo seleccionado.");
            lblArchivoSeleccionado.setStyle(""); // Resetear estilo
        }
    }

    public void setDirectorioProducto(File directorioProducto) {
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

        // --- INICIO: Configuración Drag and Drop (AHORA EN dropZone) ---
        dropZone.setOnDragOver(event -> { // <-- CAMBIADO a dropZone
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                boolean hasValidExtension = db.getFiles().stream()
                        .anyMatch(file -> {
                            String name = file.getName().toLowerCase();
                            return name.endsWith(".stl") || name.endsWith(".3mf") || name.endsWith(".gcode");
                        });

                if (hasValidExtension) {
                    event.acceptTransferModes(TransferMode.COPY);
                    // Aplicar estilo de feedback visual a dropZone
                    dropZone.getStyleClass().add("drop-zone-drag-over"); // Usa clase CSS
                } else {
                    event.consume();
                    dropZone.getStyleClass().remove("drop-zone-drag-over"); // Quitar clase si no es válido
                }
            } else {
                event.consume();
            }
        });

        dropZone.setOnDragExited(event -> { // <-- CAMBIADO a dropZone
            // Quitar estilo de feedback visual
            dropZone.getStyleClass().remove("drop-zone-drag-over");
        });

        dropZone.setOnDragDropped(event -> { // <-- CAMBIADO a dropZone
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                // Tomar solo el PRIMER archivo válido
                File droppedFile = db.getFiles().stream()
                        .filter(file -> {
                            String name = file.getName().toLowerCase();
                            return name.endsWith(".stl") || name.endsWith(".3mf") || name.endsWith(".gcode");
                        })
                        .findFirst() // Obtiene el primero que cumple
                        .orElse(null); // O null si ninguno cumple

                if (droppedFile != null) {
                    setArchivoSeleccionado(droppedFile);
                    success = true;
                } else {
                    mostrarAlerta(AlertType.WARNING, "Archivo Inválido", "El archivo soltado no tiene una extensión válida (.stl, .3mf, .gcode).");
                }
            }
            event.setDropCompleted(success);
            event.consume();
            dropZone.getStyleClass().remove("drop-zone-drag-over"); // Limpiar estilo
        });
        // --- FIN: Configuración Drag and Drop ---


        // ... (resto del method initialize sin cambios) ...
        actualizarEstadoCampos(radioProducto.isSelected());
        resetearSeleccionArchivo();

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

        // radioProducto.setSelected(true); // El FXML ya lo establece por defecto
    }

    private void resetearSeleccionArchivo() {
        setArchivoSeleccionado(null); // Usar el setter para actualizar label y estilo
    }

    @FXML
    private void handleSeleccionarArchivo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivo de Diseño/Impresión (.stl, .3mf, .gcode)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modelos 3D y GCode", "*.stl", "*.3mf", "*.gcode"),
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf"),
                new FileChooser.ExtensionFilter("Archivos GCode", "*.gcode")
        );
        File selected = fileChooser.showOpenDialog(rootVBox.getScene().getWindow()); // Anclar al modal
        setArchivoSeleccionado(selected);
    }

    private void actualizarEstadoCampos(boolean esProducto) {
        // ... (resto del method sin cambios) ...
        txtNombre.clear();
        btnBuscarDirectorio.setVisible(false); // Oculto

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


    @FXML
    public void onCargarClicked(ActionEvent event) {
        // ... (resto del method sin cambios) ...
        String valorIngresado = txtNombre.getText(); // Puede ser nombre de producto o ruta de directorio
        boolean esPieza = radioPieza.isSelected();

        // Validación 1: Archivo seleccionado
        if (this.archivoSeleccionado == null) {
            mostrarAlerta(AlertType.ERROR, "Archivo Requerido", "Debe seleccionar o soltar un archivo (.stl, .3mf, .gcode) para cargar.");
            return;
        }

        // Validación 2: Específica por modo
        if (esPieza) {
            if (directorioProducto == null || !directorioProducto.isDirectory()) {
                mostrarAlerta(AlertType.ERROR, "Producto Requerido (Pieza)",
                        "Para cargar una PIEZA, primero seleccione la carpeta del PRODUCTO correspondiente en el Gestor de Catálogo.");
                return;
            }
            valorIngresado = directorioProducto.getAbsolutePath();

        } else {
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

        // Lógica de carga
        try {
            if (esPieza) {
                catalogoService.procesarCargaPieza(this.archivoSeleccionado, valorIngresado);
                mostrarAlerta(AlertType.INFORMATION, "Éxito", "Pieza cargada y asociada correctamente al producto.");
            } else {
                catalogoService.procesarCargaProducto(this.archivoSeleccionado, valorIngresado.trim());
                mostrarAlerta(AlertType.INFORMATION, "Éxito", "Nuevo producto creado y archivo cargado correctamente.");
            }
            this.cargaExitosa = true;
            closeWindow(event);

        } catch (IllegalArgumentException | IOException e) {
            mostrarAlerta(AlertType.ERROR, "Error al Cargar", e.getMessage());
            this.cargaExitosa = false;
        } catch (Exception e) {
            mostrarAlerta(AlertType.ERROR, "Error Inesperado", "Ocurrió un error no previsto: " + e.getMessage());
            this.cargaExitosa = false;
            e.printStackTrace();
        }
    }

    @FXML
    public void onCancelarClicked(ActionEvent event) {
        this.cargaExitosa = false;
        closeWindow(event);
    }

    private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        // Aplicar estilo al DialogPane
        alert.getDialogPane().getStyleClass().add("hubi-dialog"); // IMPORTANTE
        // Opcional: Aplicar estilo a la ventana/Stage de la alerta
        // Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        // stage.getScene().getStylesheets().add(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css").toExternalForm());
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