package com.calmasalud.hubi.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ConfirmacionController {

    @FXML
    private Label lblMensaje;

    @FXML
    private Button btnConfirmar;

    private boolean confirmado = false;


    //Establece el mensaje que verá el usuario en el modal
    public void setMensaje(String mensaje) {
        lblMensaje.setText(mensaje);
    }

    /**
     * Devuelve true si el usuario presionó 'Sí, Eliminar'.
     */
    public boolean isConfirmado() {
        return confirmado;
    }

    @FXML
    private void handleConfirmar() {
        confirmado = true;
        closeStage();
    }

    @FXML
    private void handleCancelar() {
        confirmado = false;
        closeStage();
    }

    private void closeStage() {
        // Obtiene la ventana actual (Stage) y la cierra
        Stage stage = (Stage) btnConfirmar.getScene().getWindow();
        stage.close();
    }
}

