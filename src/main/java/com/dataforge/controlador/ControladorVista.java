package com.dataforge.controlador;

import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.modelo.ProcesoInfo;
import com.dataforge.servicios.ServicioEjecucion;
import com.dataforge.servicios.ServicioComunicacion;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

/**
 * ControladorVista.java
 *
 * Controlador principal de la interfaz gráfica de DataForge.
 * Gestiona los eventos de usuario, la interacción con la tabla de monitorización
 * y la delegación de tareas hacia la capa de servicios (`ServicioEjecucion` y `ServicioComunicacion`).
 *
 * @author Yessenia Martinez / Grupo DataForge
 * @version 2.0
 */
public class ControladorVista {

    @FXML private ComboBox<String> comboTareas;
    @FXML private TextField txtParametro;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;

    @FXML private TableView<ProcesoInfo> tablaProcesos;
    @FXML private TableColumn<ProcesoInfo, Integer> colId;
    @FXML private TableColumn<ProcesoInfo, Long> colPid;
    @FXML private TableColumn<ProcesoInfo, String> colTarea;
    @FXML private TableColumn<ProcesoInfo, String> colEstado;
    @FXML private TableColumn<ProcesoInfo, String> colExitCode;

    @FXML private TextArea txtStdout;
    @FXML private TextArea txtStderr;

    private final ObservableList<ProcesoInfo> listaProcesos = FXCollections.observableArrayList();

    // Instancias de los servicios implementados
    private final ServicioEjecucion servicioEjecucion = new ServicioEjecucion();
    private final ServicioComunicacion servicioComunicacion = new ServicioComunicacion();

    @FXML
    public void initialize() {
        // Configuración inicial del ComboBox de tareas
        comboTareas.getItems().addAll("Text Analyzer", "Prime Benchmark", "Pipeline de Procesos");
        comboTareas.setValue("Text Analyzer");

        // Vincular columnas de la TableView con las propiedades del modelo ProcesoInfo
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPid.setCellValueFactory(new PropertyValueFactory<>("pid"));
        colTarea.setCellValueFactory(new PropertyValueFactory<>("tarea"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colExitCode.setCellValueFactory(new PropertyValueFactory<>("exitCode"));

        tablaProcesos.setItems(listaProcesos);

        // BOTÓN INICIAR: Delega la ejecución en los servicios mediante un hilo secundario
        btnIniciar.setOnAction(event -> {
            String tarea = comboTareas.getValue();
            String parametro = txtParametro.getText();

            if (parametro == null || parametro.isBlank()) {
                txtStderr.appendText("[ERROR] El parámetro o ruta no puede estar vacío.\n");
                return;
            }

            txtStdout.appendText("[INFO] Solicitando inicio de tarea: " + tarea + "\n");

            // Lanzamos en un Thread separado para no congelar la interfaz gráfica de JavaFX
            new Thread(() -> {
                try {
                    if ("Pipeline de Procesos".equals(tarea)) {
                        // 1. Caso especial: Ejecución de Tubería (Pipeline) mediante ServicioComunicacion
                        List<String> comando1 = List.of("java", "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), "com.dataforge.tareas.PrimeBenchmark", parametro);
                        List<String> comando2 = List.of("java", "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), "com.dataforge.tareas.TextAnalyzer", "resultado.txt", "ejemplo");

                        Platform.runLater(() -> txtStdout.appendText("[INFO] Ejecutando Pipeline con ServicioComunicacion...\n"));
                        servicioComunicacion.ejecutarPipeline(comando1, comando2);

                    } else {
                        // 2. Ejecución estándar utilizando ServicioEjecucion
                        ConfiguracionProceso config = new ConfiguracionProceso(parametro, "ejemplo");

                        // Creamos inicialmente la fila en la tabla con estado "Ejecutando" y PID temporal
                        final ProcesoInfo[] registroTabla = new ProcesoInfo[1];

                        Platform.runLater(() -> {
                            registroTabla[0] = new ProcesoInfo(
                                    listaProcesos.size() + 1,
                                    0L, // Se actualizará al recibir el PID real
                                    tarea,
                                    "Ejecutando",
                                    "-"
                            );
                            listaProcesos.add(registroTabla[0]);
                        });

                        // Llamada al servicio pasando callbacks para capturar salida y PID real en tiempo real
                        int exitCode = servicioEjecucion.iniciar(
                                config,
                                // Callback para cada línea de salida que emita el proceso
                                linea -> Platform.runLater(() -> txtStdout.appendText(linea + "\n")),
                                // Callback para capturar y actualizar el PID real del sistema operativo
                                pidReal -> Platform.runLater(() -> {
                                    int index = listaProcesos.indexOf(registroTabla[0]);
                                    if (index >= 0) {
                                        // Actualizamos el objeto con el PID real obtenido del ProcessBuilder
                                        listaProcesos.set(index, new ProcesoInfo(
                                                registroTabla[0].getId(),
                                                pidReal,
                                                registroTabla[0].getTarea(),
                                                "Ejecutando",
                                                "-"
                                        ));
                                        registroTabla[0] = listaProcesos.get(index);
                                    }
                                })
                        );

                        // Cuando el servicio termina, actualizamos la tabla con el estado final y el exit code
                        Platform.runLater(() -> {
                            int index = listaProcesos.indexOf(registroTabla[0]);
                            if (index >= 0) {
                                String estadoFinal = (exitCode == 0) ? "Completado" : "Error";
                                listaProcesos.set(index, new ProcesoInfo(
                                        registroTabla[0].getId(),
                                        registroTabla[0].getPid(),
                                        registroTabla[0].getTarea(),
                                        estadoFinal,
                                        String.valueOf(exitCode)
                                ));
                            }
                        });
                    }

                } catch (Exception e) {
                    Platform.runLater(() -> txtStderr.appendText("[ERROR] " + e.getMessage() + "\n"));
                }
            }).start();
        });

        // BOTÓN DETENER: Llama al método estático de parada de emergencia en ServicioEjecucion
        btnDetener.setOnAction(event -> {
            txtStderr.appendText("[WARN] Ejecutando destrucción de procesos activos (destroy())...\n");
            ServicioEjecucion.detenerProcesosActivos();
            txtStderr.appendText("[WARN] Señal de parada enviada a los procesos activos.\n");
        });
    }
}