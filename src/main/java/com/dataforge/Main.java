package com.dataforge;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Aquí cargaremos el archivo de diseño FXML que vamos a crear
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/vista/Vista.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 650);

        stage.setTitle("DataForge - Gestor de Proceso");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(); // Lanza el ciclo de vida de JavaFX
    }
}