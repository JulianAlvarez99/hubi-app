package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.ui.util.UISettings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
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

            // 1. Aplicar inmediatamente
            mainStage.setWidth(width);
            mainStage.setHeight(height);
            mainStage.centerOnScreen(); // Centrar la ventana tras el re-dimensionamiento

            // 2. Guardar la configuración
            settings.saveWindowSize(width, height);
            settings.saveResolutionPreset(selectedPreset);

        } catch (Exception e) {
            System.err.println("Error al aplicar la resolución: " + e.getMessage());
            // Opcional: Mostrar una alerta al usuario
        }
    }
}