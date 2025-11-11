package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.ui.util.UISettings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import java.util.Map; // Importar Map
import java.util.stream.Collectors; // Importar Collectors

public class ConfiguracionController {

    @FXML
    private ComboBox<String> cmbResoluciones;
    @FXML
    private ComboBox<String> cmbFuenteTamanos;
    @FXML
    private Button btnAplicar;

    // Referencia al Stage principal (ventana)
    private Stage mainStage;

    // Instancia del servicio de configuración
    private final UISettings settings = new UISettings();

    private final Map<String, Double> PRESETS_FUENTE = Map.of(
            "Pequeño (11px)", 11.0,
            "Mediano (14px)", 14.0,
            "Recomendado (16px)", 16.0,
            "Grande (19px)", 19.0,
            "Extra Grande (25px)", 25.0
    );

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

        // Poblar el ComboBox de fuentes con las claves del Mapa
        cmbFuenteTamanos.setItems(FXCollections.observableArrayList(
                PRESETS_FUENTE.keySet().stream().sorted().collect(Collectors.toList())
        ));

        // Cargar y seleccionar la preferencia de fuente guardada
        double savedFontSize = settings.loadBaseFontSize();
        // Buscar el preset que coincida con el valor guardado
        String savedFontPreset = fontSizeToPreset(savedFontSize);
        cmbFuenteTamanos.setValue(savedFontPreset);
    }

    @FXML
    private void handleAplicar() {
        if (mainStage == null) {
            System.err.println("Error: MainStage no fue inyectado en ConfiguracionController.");
            return;
        }

        // --- 1. Obtener y aplicar Resolución (Lógica existente) ---
        String selectedPreset = cmbResoluciones.getValue();
        if (selectedPreset == null || selectedPreset.isEmpty()) {
            return;
        }

        double width = 0, height = 0;
        try {
            String[] parts = selectedPreset.split(" x ");
            width = Double.parseDouble(parts[0]);
            height = Double.parseDouble(parts[1]);
        } catch (Exception e) {
            System.err.println("Error al parsear la resolución: " + e.getMessage());
            return;
        }

        // Aplicar tamaño de ventana
        mainStage.setWidth(width);
        mainStage.setHeight(height);
        mainStage.centerOnScreen();

        // Guardar configuración de ventana
        settings.saveWindowSize(width, height);
        settings.saveResolutionPreset(selectedPreset);


        // --- 2. [CAMBIO] Obtener y aplicar Tamaño de Fuente ---
        String selectedFontPreset = cmbFuenteTamanos.getValue();
        if (selectedFontPreset == null || selectedFontPreset.isEmpty()) {
            selectedFontPreset = "Recomendado (14px)"; // Fallback
        }

        // Convertir el preset (ej: "Mediano (14px)") al valor numérico (ej: 14.0)
        double baseFontSize = presetToFontSize(selectedFontPreset);

        // Aplicar tamaño de fuente base DINÁMICAMENTE al root de la escena
        Scene scene = mainStage.getScene();
        if (scene != null && scene.getRoot() != null) {
            String style = String.format(".root { -fx-font-size: %.2fpx; }", baseFontSize);
            scene.getRoot().setStyle(style);
        } else {
            System.err.println("Advertencia: No se pudo obtener la escena o el nodo raíz para aplicar el tamaño de fuente.");
        }

        // Guardar configuración de fuente
        settings.saveBaseFontSize(baseFontSize);
    }

    /**
     * Convierte un valor de fuente (ej: 14.0) al preset (ej: "Recomendado (14px)").
     */
    private String fontSizeToPreset(double fontSize) {
        return PRESETS_FUENTE.entrySet().stream()
                .filter(entry -> entry.getValue() == fontSize)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("Recomendado (16px)"); // Valor por defecto si no se encuentra
    }

    /**
     * Convierte un preset (ej: "Recomendado (14px)") al valor (ej: 14.0).
     */
    private double presetToFontSize(String preset) {
        return PRESETS_FUENTE.getOrDefault(preset, 14.0); // 14.0 como valor por defecto
    }

}