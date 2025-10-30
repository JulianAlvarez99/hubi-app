package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.ui.util.UISettings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

public class ConfiguracionController {

    @FXML
    private ComboBox<String> cmbResoluciones;
    @FXML
    private Button btnAplicar;

    // Referencia al Stage principal (ventana)
    private Stage mainStage;

    // Instancia del servicio de configuración
    private final UISettings settings = new UISettings();

    /**
     * Inyecta el Stage principal desde el MainController.
     */
    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    @FXML
    public void initialize() {
        // Poblar el ComboBox con las resoluciones
        cmbResoluciones.setItems(FXCollections.observableArrayList(
                "800 x 600",
                "1024 x 768",
                "1280 x 720",
                "1366 x 768",
                "1600 x 900",
                "1920 x 1080"
        ));

        // Cargar y seleccionar la preferencia guardada
        String savedPreset = settings.loadResolutionPreset();
        cmbResoluciones.setValue(savedPreset);
    }

    @FXML
    private void handleAplicar() {
        if (mainStage == null) {
            System.err.println("Error: MainStage no fue inyectado en ConfiguracionController.");
            return;
        }

        String selectedPreset = cmbResoluciones.getValue();
        if (selectedPreset == null || selectedPreset.isEmpty()) {
            return;
        }

        try {
            // Parsear el string "1024 x 768"
            String[] parts = selectedPreset.split(" x ");
            double width = Double.parseDouble(parts[0]);
            double height = Double.parseDouble(parts[1]);

            // 1. Determinar el tamaño de fuente base según el preset
            double baseFontSize = calculateBaseFontSize(width); // Usamos el ancho como referencia

            // 2. Aplicar inmediatamente
            mainStage.setWidth(width);
            mainStage.setHeight(height);
            mainStage.centerOnScreen(); // Centrar la ventana tras el re-dimensionamiento

            // 3. Aplicar tamaño de fuente base DINÁMICAMENTE al root de la escena
            Scene scene = mainStage.getScene();
            if (scene != null && scene.getRoot() != null) {
                // Usamos style para setear la variable CSS -fx-base-font-size
                scene.getRoot().setStyle(String.format("-fx-base-font-size: %.2fpx;", baseFontSize));
            } else {
                System.err.println("Advertencia: No se pudo obtener la escena o el nodo raíz para aplicar el tamaño de fuente.");
            }

            // 4. Guardar la configuración
            settings.saveWindowSize(width, height);
            settings.saveResolutionPreset(selectedPreset);
            settings.saveBaseFontSize(baseFontSize);

        } catch (Exception e) {
            System.err.println("Error al aplicar la resolución: " + e.getMessage());
            // Opcional: Mostrar una alerta al usuario
        }
    }

    private double calculateBaseFontSize(double windowWidth) {
        if (windowWidth <= 800) {
            return 12.0; // Tamaño para 800x600
        } else if (windowWidth <= 1024) {
            return 13.0; // Tamaño para 1024x768
        } else if (windowWidth <= 1366) {
            return 14.0; // Tamaño para 1280x720, 1366x768 (Nuestro default)
        } else if (windowWidth <= 1600) {
            return 15.0; // Tamaño para 1600x900
        } else {
            return 16.0; // Tamaño para 1920x1080 y superiores
        }
        // Considera usar una fórmula de interpolación si quieres más granularidad.
        // Ejemplo simple: return 12.0 + (windowWidth - 800) / (1920 - 800) * (16.0 - 12.0);
    }
}