package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.service.CatalogService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
// --- Imports actualizados ---
import javafx.scene.control.TreeView;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
// --- Fin de imports actualizados ---
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;

public class CatalogManagerController {

    // --- Inyección de Servicios ---
    private final CatalogService catalogoService = new CatalogService();

    // --- Panel Izquierdo (Explorador) ---
    @FXML
    private TextField txtBusqueda;
    @FXML
    private Button btnActualizar;

    // --- Componentes del explorador actualizados ---
    @FXML
    private TreeView<?> folderTreeView; // Antes era TreeTableView
    @FXML
    private TableView<?> fileTableView;  // Nuevo
    @FXML
    private TableColumn<?, ?> colNombre;
    @FXML
    private TableColumn<?, ?> colTamaño;
    @FXML
    private TableColumn<?, ?> colFechaMod;
    // --- Fin de componentes actualizados ---

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


    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (v2.1 con Tree/Table) inicializado.");

        // (Lógica futura):
        // 1. Cargar el árbol de carpetas en 'folderTreeView'
        // 2. Añadir un listener a 'folderTreeView.getSelectionModel()'
        // 3. Cuando se seleccione una carpeta, poblar 'fileTableView'
        //    con los archivos de esa carpeta.
    }

    /**
     * Implementa HU1 y RF1: Carga de archivos .stl y .3mf
     *
     */
    @FXML
    private void handleAgregarClick() {
        System.out.println("Botón AGREGAR presionado.");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivos de impresión");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos 3D", "*.stl", "*.3mf"),
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File archivoSeleccionado = fileChooser.showOpenDialog(btnAgregar.getScene().getWindow());

        if (archivoSeleccionado != null) {
            String nombreArchivo = archivoSeleccionado.getName();
            System.out.println("Archivo seleccionado: " + nombreArchivo);

            // TODO: Esta lógica debe moverse.
            String codigo = catalogoService.generateProductCode("Soporte", "Rojo", true);
            System.out.println("Código generado: " + codigo);

            // 3. Actualizar la UI
            System.out.println("Actualizando TreeView y TableView (lógica pendiente)...");
            // (La lógica aquí refrescaría el 'folderTreeView' y 'fileTableView')

        } else {
            System.out.println("Selección de archivo cancelada.");
        }
    }

    // (Aquí irán los handlers para btnEliminar y btnExtraer)
}