package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.service.CatalogService; // Importa tu servicio del core
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.FileChooser; // Importa el selector de archivos

import java.io.File;
import java.util.List;

public class GestorCatalogoController {

    // 1. Inyecta los componentes del FXML
    @FXML
    private ListView<String> listaArchivos; // Mostrará los nombres de los archivos

    @FXML
    private Button btnAgregar;

    // (Aquí irán los otros botones y labels cuando los necesites)

    // 2. Instancia los servicios del módulo 'core'
    private final CatalogService catalogoService = new CatalogService();

    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (real) inicializado.");
        // Aquí podrías cargar los archivos ya existentes
    }

    /**
     * Implementa HU1 y RF1: Carga de archivos .stl y .3mf
     * [cite: 82, 87]
     */
    @FXML
    private void handleAgregarClick() {
        System.out.println("Botón AGREGAR presionado.");

        // 1. Configurar el FileChooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar archivos de impresión");

        // 2. Definir filtros para .stl y .3mf (Cumple RF1)
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos 3D", "*.stl", "*.3mf"),
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        // 3. Mostrar el diálogo y obtener el archivo
        // (Para múltiples archivos, usa 'showOpenMultipleDialog')
        File archivoSeleccionado = fileChooser.showOpenDialog(btnAgregar.getScene().getWindow());

        if (archivoSeleccionado != null) {
            String nombreArchivo = archivoSeleccionado.getName();
            System.out.println("Archivo seleccionado: " + nombreArchivo);

            // 4. Implementar HU2/RF8 (Generar código)
            // (Los datos "Soporte" y "Rojo" son de ejemplo,
            // deberías pedirlos en un pop-up)
            String codigo = catalogoService.generateProductCode("Soporte", "Rojo", true);
            System.out.println("Código generado: " + codigo);

            // 5. Actualizar la UI (RNF1: Interfaz intuitiva)
            // Añade el nuevo archivo a la lista
            listaArchivos.getItems().add(nombreArchivo + " (Código: " + codigo + ")");

            // (Aquí iría la lógica para copiar el archivo
            // al repositorio local )

        } else {
            System.out.println("Selección de archivo cancelada.");
        }
    }
}