package com.calmasalud.hubi.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font; // Importar Font
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects; // Importar Objects

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 1. Cargar la fuente personalizada
        try {
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-SemiBold.ttf")).toExternalForm(),
                    10
            );
        } catch (Exception e) {
            System.err.println("No se pudo cargar la fuente Maven Pro. Usando fuente por defecto.");
            e.printStackTrace();
        }


        // 2. Carga el archivo FXML de la vista principal
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/MainView.fxml"));
        Parent root = loader.load();

        // 3. Configura la escena
        Scene scene = new Scene(root, 1280, 800); // Tamaño inicial más grande

        // 4. Aplicar la hoja de estilos CSS
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("No se pudo cargar la hoja de estilos styles.css.");
            e.printStackTrace();
        }

        // 5. Configura el escenario (la ventana)
        primaryStage.setTitle("Sistema HUBI v1.0"); // Título en la barra de la ventana
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1024); // Definir tamaños mínimos
        primaryStage.setMinHeight(900);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}