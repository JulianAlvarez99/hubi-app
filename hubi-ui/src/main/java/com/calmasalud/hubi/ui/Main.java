package com.calmasalud.hubi.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 1. Carga el archivo FXML de la vista principal
        // OJO: La ruta empieza con "/" porque está en 'resources'
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/MainView.fxml"));
        Parent root = loader.load();

        // 2. Configura la escena y el escenario (la ventana)
        primaryStage.setTitle("Sistema HUBI v1.0");
        primaryStage.setScene(new Scene(root, 1024, 768)); // Define un tamaño inicial
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}