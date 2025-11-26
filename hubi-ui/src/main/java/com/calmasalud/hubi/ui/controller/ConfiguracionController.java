package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.ui.util.UISettings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfiguracionController {

    @FXML
    private ComboBox<String> cmbResoluciones;
    @FXML
    private ComboBox<String> cmbFuenteTamanos;
    @FXML
    private Button btnAplicar;
    @FXML
    private Button btnDetectar;
    @FXML
    private Label lblInfoPantalla;

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
        // Obtener información de la pantalla
        Rectangle2D screenBounds = UISettings.getScreenBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();

        // Mostrar información de la pantalla detectada
        lblInfoPantalla.setText(String.format("Resolución de pantalla detectada: %.0f x %.0f px",
                                             screenWidth, screenHeight));

        // Generar lista de resoluciones válidas basadas en la pantalla actual
        java.util.List<String> resolutions = new java.util.ArrayList<>();

        // Resoluciones comunes que caben en la pantalla
        String[][] commonResolutions = {
            {"800", "600"},
            {"1024", "768"},
            {"1280", "720"},
            {"1280", "800"},
            {"1366", "768"},
            {"1440", "900"},
            {"1600", "900"},
            {"1680", "1050"},
            {"1920", "1080"},
            {"1920", "1200"},
            {"2560", "1440"}
        };

        for (String[] res : commonResolutions) {
            double width = Double.parseDouble(res[0]);
            double height = Double.parseDouble(res[1]);

            // Solo agregar si cabe en la pantalla (con margen de 50px)
            if (width <= screenWidth - 50 && height <= screenHeight - 50) {
                resolutions.add(res[0] + " x " + res[1]);
            }
        }

        // Agregar resolución recomendada (80% de la pantalla)
        double[] recommended = UISettings.getRecommendedWindowSize();
        String recommendedStr = String.format("%.0f x %.0f (Recomendada)",
                                             recommended[0], recommended[1]);
        resolutions.add(0, recommendedStr); // Agregar al inicio

        // Poblar el ComboBox con las resoluciones válidas
        cmbResoluciones.setItems(FXCollections.observableArrayList(resolutions));

        // Cargar y seleccionar la preferencia guardada
        String savedPreset = settings.loadResolutionPreset();

        // Intentar seleccionar el preset guardado, o seleccionar la recomendada
        if (resolutions.contains(savedPreset)) {
            cmbResoluciones.setValue(savedPreset);
        } else {
            cmbResoluciones.setValue(recommendedStr);
        }

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

        // --- 1. Obtener y aplicar Resolución ---
        String selectedPreset = cmbResoluciones.getValue();
        if (selectedPreset == null || selectedPreset.isEmpty()) {
            return;
        }

        double width, height;
        try {
            // Remover texto adicional como "(Recomendada)"
            String cleanPreset = selectedPreset.replaceAll("\\s*\\(.*?\\)", "").trim();
            String[] parts = cleanPreset.split(" x ");
            width = Double.parseDouble(parts[0].trim());
            height = Double.parseDouble(parts[1].trim());
        } catch (Exception e) {
            System.err.println("Error al parsear la resolución: " + e.getMessage());
            return;
        }

        // Aplicar tamaño de ventana
        mainStage.setWidth(width);
        mainStage.setHeight(height);
        mainStage.centerOnScreen();

        // Guardar configuración de ventana (guardar sin el texto adicional)
        String cleanPreset = selectedPreset.replaceAll("\\s*\\(.*?\\)", "").trim();
        settings.saveWindowSize(width, height);
        settings.saveResolutionPreset(cleanPreset);


        // --- 2. Obtener y aplicar Tamaño de Fuente ---
        String selectedFontPreset = cmbFuenteTamanos.getValue();
        if (selectedFontPreset == null || selectedFontPreset.isEmpty()) {
            selectedFontPreset = "Recomendado (16px)"; // Fallback
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

        System.out.println("Configuración aplicada: " + width + "x" + height + " - Fuente: " + baseFontSize + "px");
    }

    /**
     * Manejador para el botón "Detectar Auto".
     * Selecciona automáticamente la resolución recomendada basada en la pantalla.
     */
    @FXML
    private void handleDetectarAuto() {
        double[] recommended = UISettings.getRecommendedWindowSize();
        String recommendedStr = String.format("%.0f x %.0f (Recomendada)",
                                             recommended[0], recommended[1]);

        // Seleccionar en el combo
        cmbResoluciones.setValue(recommendedStr);

        // Aplicar inmediatamente
        handleAplicar();
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