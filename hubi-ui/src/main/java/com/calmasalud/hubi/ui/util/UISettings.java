package com.calmasalud.hubi.ui.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.stage.Screen;

import java.util.prefs.Preferences;

public class UISettings {

    // Nodo de preferencias único para nuestra aplicación
    private static final String PREF_NODE_NAME = "com/calmasalud/hubi";

    // Claves para las propiedades
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_RESOLUTION_PRESET = "resolutionPreset";
    private static final String KEY_BASE_FONT_SIZE = "baseFontSize";

    // Valores por defecto (mínimos seguros)
    private static final double DEFAULT_WIDTH = 1024;
    private static final double DEFAULT_HEIGHT = 768;
    private static final String DEFAULT_PRESET = "1024 x 768";
    private static final double DEFAULT_BASE_FONT_SIZE = 14.0;

    // Porcentaje de pantalla a usar cuando se detecta automáticamente
    private static final double SCREEN_USAGE_PERCENTAGE = 0.80; // 80% de la pantalla

    private final Preferences prefs;

    public UISettings() {
        // Obtiene el nodo de preferencias para el usuario actual
        this.prefs = Preferences.userRoot().node(PREF_NODE_NAME);
    }

    /**
     * Obtiene la resolución de la pantalla principal del sistema.
     */
    public static Rectangle2D getScreenBounds() {
        return Screen.getPrimary().getVisualBounds();
    }

    /**
     * Calcula un tamaño de ventana recomendado basado en la resolución de la pantalla.
     * Retorna [ancho, alto] que es el 80% de la pantalla disponible.
     */
    public static double[] getRecommendedWindowSize() {
        Rectangle2D screenBounds = getScreenBounds();
        double width = screenBounds.getWidth() * SCREEN_USAGE_PERCENTAGE;
        double height = screenBounds.getHeight() * SCREEN_USAGE_PERCENTAGE;

        // Asegurar un mínimo razonable
        width = Math.max(width, 800);
        height = Math.max(height, 600);

        return new double[]{width, height};
    }

    /**
     * Valida que el tamaño guardado no exceda la pantalla actual.
     */
    private double[] validateWindowSize(double width, double height) {
        Rectangle2D screenBounds = getScreenBounds();

        // Si el tamaño guardado es mayor que la pantalla, usar tamaño recomendado
        if (width > screenBounds.getWidth() || height > screenBounds.getHeight()) {
            System.out.println("Advertencia: Resolución guardada (" + width + "x" + height +
                             ") excede la pantalla actual (" + screenBounds.getWidth() + "x" +
                             screenBounds.getHeight() + "). Ajustando automáticamente.");
            return getRecommendedWindowSize();
        }

        return new double[]{width, height};
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
     * Si no hay configuración guardada o es inválida, usa la resolución recomendada del sistema.
     */
    public double[] loadWindowSize() {
        double width = prefs.getDouble(KEY_WINDOW_WIDTH, -1);
        double height = prefs.getDouble(KEY_WINDOW_HEIGHT, -1);

        // Si no hay configuración guardada, usar tamaño recomendado basado en pantalla
        if (width <= 0 || height <= 0) {
            System.out.println("No hay configuración guardada. Detectando resolución de pantalla...");
            double[] recommended = getRecommendedWindowSize();
            // Guardar la nueva configuración
            saveWindowSize(recommended[0], recommended[1]);
            return recommended;
        }

        // Validar que el tamaño guardado sea apropiado para la pantalla actual
        return validateWindowSize(width, height);
    }

    /**
     * Guarda el nombre del preset seleccionado en el ComboBox.
     */
    public void saveResolutionPreset(String preset) {
        prefs.put(KEY_RESOLUTION_PRESET, preset);
    }

    /**
     * Carga el nombre del último preset seleccionado.
     * Si no existe, calcula el preset más cercano a la resolución de la pantalla.
     */
    public String loadResolutionPreset() {
        String saved = prefs.get(KEY_RESOLUTION_PRESET, null);

        // Si no hay preset guardado, calcular el más apropiado
        if (saved == null || saved.isEmpty()) {
            double[] recommended = getRecommendedWindowSize();
            saved = String.format("%.0f x %.0f", recommended[0], recommended[1]);
            saveResolutionPreset(saved);
        }

        return saved;
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
     * Versión principal de 4 argumentos. Llamada por Methods estáticos.
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