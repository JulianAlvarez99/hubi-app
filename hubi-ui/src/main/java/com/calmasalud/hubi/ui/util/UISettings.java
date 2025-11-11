package com.calmasalud.hubi.ui.util;

import javafx.scene.control.Alert;

import java.util.prefs.Preferences;

public class UISettings {

    // Nodo de preferencias único para nuestra aplicación
    private static final String PREF_NODE_NAME = "com/calmasalud/hubi";

    // Claves para las propiedades
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_RESOLUTION_PRESET = "resolutionPreset";
    private static final String KEY_BASE_FONT_SIZE = "baseFontSize";
    // Valores por defecto
    private static final double DEFAULT_WIDTH = 1024;
    private static final double DEFAULT_HEIGHT = 768;
    private static final String DEFAULT_PRESET = "1024 x 768";
    private static final double DEFAULT_BASE_FONT_SIZE = 14.0;

    private Preferences prefs;

    public UISettings() {
        // Obtiene el nodo de preferencias para el usuario actual
        this.prefs = Preferences.userRoot().node(PREF_NODE_NAME);
    }

    /**
     * Guarda la última dimensión de la ventana seleccionada.
     */
    public void saveWindowSize(double width, double height) {
        prefs.putDouble(KEY_WINDOW_WIDTH, width);
        prefs.putDouble(KEY_WINDOW_HEIGHT, height);
    }

    /**
     * Carga la última dimensión guardada.
     * Retorna un array [ancho, alto].
     */
    public double[] loadWindowSize() {
        double width = prefs.getDouble(KEY_WINDOW_WIDTH, DEFAULT_WIDTH);
        double height = prefs.getDouble(KEY_WINDOW_HEIGHT, DEFAULT_HEIGHT);
        return new double[]{width, height};
    }

    /**
     * Guarda el nombre del preset seleccionado en el ComboBox.
     */
    public void saveResolutionPreset(String preset) {
        prefs.put(KEY_RESOLUTION_PRESET, preset);
    }

    /**
     * Carga el nombre del último preset seleccionado.
     */
    public String loadResolutionPreset() {
        return prefs.get(KEY_RESOLUTION_PRESET, DEFAULT_PRESET);
    }

    /**
     * Guarda el tamaño de fuente base (en px) asociado al preset seleccionado.
     */
    public void saveBaseFontSize(double size) {
        prefs.putDouble(KEY_BASE_FONT_SIZE, size);
    }

    /**
     * Carga el tamaño de fuente base guardado (en px).
     */
    public double loadBaseFontSize() {
        return prefs.getDouble(KEY_BASE_FONT_SIZE, DEFAULT_BASE_FONT_SIZE);
    }
    /**
     * Muestra una ventana de alerta de forma estática.
     */
    /**
     * Versión principal de 4 argumentos. Llamada por métodos estáticos.
     */
    public static void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }

    /**
     * Versión de 3 argumentos (La que probablemente fallaba en CompositionController).
     * Delega a la versión de 4 argumentos con un header nulo.
     */
    public static void showAlert(Alert.AlertType type, String title, String content) {
        // Delega la llamada, pasando null como header
        showAlert(type, title, null, content);
    }

}