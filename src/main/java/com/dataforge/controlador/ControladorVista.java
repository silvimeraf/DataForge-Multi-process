package com.dataforge.controlador;

import com.dataforge.modelo.ProcesoInfo;
import com.dataforge.servicios.ServicioRendimiento;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.*;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controlador principal de la interfaz gráfica de DataForge (RA1).
 * Orquesta los procesos externos mediante ProcessBuilder y Process sin utilizar hilos manuales,
 * cumpliendo estrictamente las restricciones técnicas del módulo.
 */
public class ControladorVista {

    // Zona 1: Selección de tarea
    @FXML private ComboBox<String> comboTareas;
    @FXML private Label lblDescripcionTarea;
    @FXML private Button btnLimpiarTabla;

    // Zona 2: Configuración dinámica
    @FXML private VBox boxFicheroEntrada;
    @FXML private TextField txtRutaFichero;
    @FXML private Button btnExaminarEntrada;
    @FXML private VBox boxTextoStdin;
    @FXML private TextArea txtContenidoStdin;
    @FXML private VBox boxLimiteN;
    @FXML private TextField txtLimiteN;
    @FXML private VBox boxPalabraBuscar;
    @FXML private TextField txtPalabra;

    // Zona 3: Modo de ejecución
    @FXML private ComboBox<String> comboModo;
    @FXML private Spinner<Integer> spinnerProcesos;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;
    @FXML private Button btnBenchmark;

    // Zona 4: Redirecciones
    @FXML private ComboBox<String> comboStdout;
    @FXML private Button btnFicheroStdout;
    @FXML private Label lblRutaStdout;
    @FXML private ComboBox<String> comboStderr;
    @FXML private Button btnFicheroStderr;
    @FXML private Label lblRutaStderr;
    @FXML private ComboBox<String> comboStdin;
    @FXML private Button btnFicheroStdin;
    @FXML private Label lblRutaStdin;

    // Zona 5: Monitor de procesos
    @FXML private TableView<ProcesoInfo> tablaProcesos;
    @FXML private TableColumn<ProcesoInfo, Integer> colId;
    @FXML private TableColumn<ProcesoInfo, Long> colPid;
    @FXML private TableColumn<ProcesoInfo, String> colTarea;
    @FXML private TableColumn<ProcesoInfo, String> colModo;
    @FXML private TableColumn<ProcesoInfo, String> colParametros;
    @FXML private TableColumn<ProcesoInfo, String> colInicio;
    @FXML private TableColumn<ProcesoInfo, String> colFin;
    @FXML private TableColumn<ProcesoInfo, String> colDuracion;
    @FXML private TableColumn<ProcesoInfo, String> colExitCode;
    @FXML private TableColumn<ProcesoInfo, String> colEstado;

    // Zonas 6 y 7: stdout y stderr
    @FXML private TextArea txtStdout;
    @FXML private Button btnLimpiarStdout;
    @FXML private TextArea txtStderr;
    @FXML private Button btnLimpiarStderr;

    // Zona 8: Información general
    @FXML private Label lblTotalEjecutados;
    @FXML private Label lblFinalizados;
    @FXML private Label lblEnEjecucion;
    @FXML private Label lblConError;
    @FXML private Label lblTiempoLote;
    @FXML private ProgressBar barraProgreso;
    @FXML private Label lblResumenCabecera;
    @FXML private Label lblEstadoBarra;

    private final ObservableList<ProcesoInfo> listaProcesos = FXCollections.observableArrayList();
    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private File archivoDestinoStdout;
    private File archivoDestinoStderr;
    private File archivoOrigenStdin;

    // Estructuras para vigilar procesos activos mediante Timeline de JavaFX (sin Thread)
    private static class ProcesoEnCurso {
        Process proceso;
        ProcesoInfo info;
        long tiempoInicioMs;
        boolean canceladoPorUsuario = false;
        File tempStdout;
        File tempStderr;
        String modoStdout;
        String modoStderr;
    }

    private final List<ProcesoEnCurso> procesosVigilados = new ArrayList<>();
    private final List<List<String>> colaPendienteSecuencial = new ArrayList<>();
    private Timeline vigilanteProcesos;
    private long inicioLoteMs = 0L;
    private int totalLoteActual = 0;
    private final ServicioRendimiento servicioRendimiento = new ServicioRendimiento();

    @FXML
    public void initialize() {
        // 1. Inicializar combos
        comboTareas.getItems().addAll(
                "Text Analyzer",
                "Prime Benchmark",
                "Pipeline: Prime Benchmark -> Text Analyzer"
        );
        comboTareas.setValue("Text Analyzer");

        comboModo.getItems().addAll("Individual", "Secuencial", "Paralelo");
        comboModo.setValue("Individual");

        spinnerProcesos.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        spinnerProcesos.setDisable(true);

        comboStdout.getItems().addAll("Capturar en DataForge", "Fichero", "DISCARD");
        comboStdout.setValue("Capturar en DataForge");

        comboStderr.getItems().addAll("Capturar en DataForge", "Fichero", "DISCARD");
        comboStderr.setValue("Capturar en DataForge");

        comboStdin.getItems().addAll("Fichero (argumento)", "Desde DataForge", "Desde fichero (stdin)");
        comboStdin.setValue("Fichero (argumento)");

        // 2. Configurar columnas de la tabla
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPid.setCellValueFactory(new PropertyValueFactory<>("pid"));
        colTarea.setCellValueFactory(new PropertyValueFactory<>("tarea"));
        colModo.setCellValueFactory(new PropertyValueFactory<>("modo"));
        colParametros.setCellValueFactory(new PropertyValueFactory<>("parametros"));
        colInicio.setCellValueFactory(new PropertyValueFactory<>("inicio"));
        colFin.setCellValueFactory(new PropertyValueFactory<>("fin"));
        colDuracion.setCellValueFactory(new PropertyValueFactory<>("duracion"));
        colExitCode.setCellValueFactory(new PropertyValueFactory<>("exitCode"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        tablaProcesos.setItems(listaProcesos);

        // Al seleccionar una fila de la tabla, mostrar su stdout y stderr guardados
        tablaProcesos.getSelectionModel().selectedItemProperty().addListener((obs, ant, sel) -> {
            if (sel != null) {
                txtStdout.setText(sel.getSalidaStdout());
                txtStderr.setText(sel.getSalidaStderr());
            }
        });

        // 3. Listeners de configuración dinámica
        comboTareas.setOnAction(e -> actualizarFormularioDinamico());
        comboStdin.setOnAction(e -> actualizarRedireccionesUI());
        comboStdout.setOnAction(e -> actualizarRedireccionesUI());
        comboStderr.setOnAction(e -> actualizarRedireccionesUI());

        comboModo.setOnAction(e -> {
            boolean esIndividual = "Individual".equals(comboModo.getValue());
            spinnerProcesos.setDisable(esIndividual);
            if (esIndividual) {
                spinnerProcesos.getValueFactory().setValue(1);
            } else if (spinnerProcesos.getValue() == 1) {
                spinnerProcesos.getValueFactory().setValue(5);
            }
            actualizarBarraEstado();
        });
        spinnerProcesos.valueProperty().addListener((o, a, b) -> actualizarBarraEstado());

        // 4. Selectores de archivo
        btnExaminarEntrada.setOnAction(e -> {
            File f = elegirArchivoAbrir("Seleccionar fichero de texto a analizar");
            if (f != null) txtRutaFichero.setText(f.getAbsolutePath());
        });
        btnFicheroStdout.setOnAction(e -> {
            File f = elegirArchivoGuardar("Guardar salida estándar (stdout) en...");
            if (f != null) {
                archivoDestinoStdout = f;
                lblRutaStdout.setText("stdout → FICHERO: " + f.getAbsolutePath());
            }
        });
        btnFicheroStderr.setOnAction(e -> {
            File f = elegirArchivoGuardar("Guardar salida de error (stderr) en...");
            if (f != null) {
                archivoDestinoStderr = f;
                lblRutaStderr.setText("stderr → FICHERO: " + f.getAbsolutePath());
            }
        });
        btnFicheroStdin.setOnAction(e -> {
            File f = elegirArchivoAbrir("Seleccionar fichero para redirección stdin");
            if (f != null) {
                archivoOrigenStdin = f;
                lblRutaStdin.setText("stdin ← FICHERO: " + f.getAbsolutePath());
            }
        });

        // 5. Botones de acción
        btnIniciar.setOnAction(e -> ejecutarAccionPrincipal());
        btnDetener.setOnAction(e -> detenerProcesoSeleccionado());
        btnBenchmark.setOnAction(e -> ejecutarExperimentoRendimiento());

        btnLimpiarStdout.setOnAction(e -> txtStdout.clear());
        btnLimpiarStderr.setOnAction(e -> txtStderr.clear());
        btnLimpiarTabla.setOnAction(e -> {
            listaProcesos.clear();
            txtStdout.clear();
            txtStderr.clear();
            lblTiempoLote.setText("Tiempo total (lote): 0 ms");
            barraProgreso.setProgress(0);
            actualizarEstadisticas();
        });

        // 6. Temporizador vigilante de procesos (sin hilos manuales)
        vigilanteProcesos = new Timeline(new KeyFrame(Duration.millis(80), e -> vigilarProcesosActivos()));
        vigilanteProcesos.setCycleCount(Timeline.INDEFINITE);
        vigilanteProcesos.play();

        actualizarFormularioDinamico();
        actualizarRedireccionesUI();
    }

    private void actualizarFormularioDinamico() {
        String tarea = comboTareas.getValue();
        boolean esText = "Text Analyzer".equals(tarea);
        boolean esPrime = "Prime Benchmark".equals(tarea);
        boolean esPipeline = tarea != null && tarea.startsWith("Pipeline");

        String modoStdin = comboStdin.getValue();
        boolean stdinDesdeDataForge = "Desde DataForge".equals(modoStdin);
        boolean stdinDesdeFichero = "Desde fichero (stdin)".equals(modoStdin);

        boxFicheroEntrada.setVisible(esText && !stdinDesdeDataForge && !stdinDesdeFichero);
        boxFicheroEntrada.setManaged(esText && !stdinDesdeDataForge && !stdinDesdeFichero);

        boxTextoStdin.setVisible(esText && stdinDesdeDataForge);
        boxTextoStdin.setManaged(esText && stdinDesdeDataForge);

        boxLimiteN.setVisible(esPrime || esPipeline);
        boxLimiteN.setManaged(esPrime || esPipeline);

        boxPalabraBuscar.setVisible(esText || esPipeline);
        boxPalabraBuscar.setManaged(esText || esPipeline);

        if (esText) {
            lblDescripcionTarea.setText("Analiza un fichero o texto por stdin: cuenta líneas, palabras, caracteres, palabra más larga y apariciones.");
        } else if (esPrime) {
            lblDescripcionTarea.setText("Calcula cuántos números primos existen entre 2 y N mediante cómputo real intensivo de CPU.");
        } else {
            lblDescripcionTarea.setText("Conecta stdout de Prime Benchmark con stdin de Text Analyzer mediante ProcessBuilder.startPipeline().");
        }
    }

    private void actualizarRedireccionesUI() {
        String out = comboStdout.getValue();
        btnFicheroStdout.setDisable(!"Fichero".equals(out));
        if ("Capturar en DataForge".equals(out)) lblRutaStdout.setText("stdout → PIPE: Capturado en panel stdout");
        else if ("DISCARD".equals(out)) lblRutaStdout.setText("stdout → DISCARD: Salida descartada");
        else if (archivoDestinoStdout == null) lblRutaStdout.setText("stdout → FICHERO: Pulsa '...' para elegir archivo");

        String err = comboStderr.getValue();
        btnFicheroStderr.setDisable(!"Fichero".equals(err));
        if ("Capturar en DataForge".equals(err)) lblRutaStderr.setText("stderr → PIPE: Capturado en panel stderr");
        else if ("DISCARD".equals(err)) lblRutaStderr.setText("stderr → DISCARD: Errores descartados");
        else if (archivoDestinoStderr == null) lblRutaStderr.setText("stderr → FICHERO: Pulsa '...' para elegir archivo");

        String in = comboStdin.getValue();
        btnFicheroStdin.setDisable(!"Desde fichero (stdin)".equals(in));
        if ("Fichero (argumento)".equals(in)) lblRutaStdin.setText("stdin: No aplica (ruta pasada como argumento)");
        else if ("Desde DataForge".equals(in)) lblRutaStdin.setText("stdin ← getOutputStream() desde cuadro de texto");
        else if (archivoOrigenStdin == null) lblRutaStdin.setText("stdin ← FICHERO: Pulsa '...' para elegir origen");

        actualizarFormularioDinamico();
    }

    private void ejecutarAccionPrincipal() {
        String tarea = comboTareas.getValue();
        String modo = comboModo.getValue();
        int numProcesos = "Individual".equals(modo) ? 1 : spinnerProcesos.getValue();

        // Validaciones previas
        if ("Fichero".equals(comboStdout.getValue()) && archivoDestinoStdout == null) {
            txtStderr.setText("[ERROR] Has elegido redirigir stdout a Fichero pero no has seleccionado archivo destino.\n");
            return;
        }
        if ("Fichero".equals(comboStderr.getValue()) && archivoDestinoStderr == null) {
            txtStderr.setText("[ERROR] Has elegido redirigir stderr a Fichero pero no has seleccionado archivo destino.\n");
            return;
        }
        if ("Text Analyzer".equals(tarea) && "Desde fichero (stdin)".equals(comboStdin.getValue()) && archivoOrigenStdin == null) {
            txtStderr.setText("[ERROR] Has elegido stdin desde fichero pero no has seleccionado el archivo de entrada.\n");
            return;
        }

        // Caso especial: Pipeline con ProcessBuilder.startPipeline()
        if (tarea.startsWith("Pipeline")) {
            ejecutarTareaPipeline();
            return;
        }

        // Construir los comandos para Individual, Secuencial o Paralelo
        List<List<String>> comandos = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");

        for (int i = 0; i < numProcesos; i++) {
            if ("Prime Benchmark".equals(tarea)) {
                String limite = txtLimiteN.getText().trim();
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.PrimeBenchmark", limite
                ));
            } else {
                String palabra = txtPalabra.getText().trim();
                String modoStdin = comboStdin.getValue();
                String origen = "Fichero (argumento)".equals(modoStdin) ? txtRutaFichero.getText().trim() : "stdin";
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.TextAnalyzer", origen, palabra
                ));
            }
        }

        inicioLoteMs = System.currentTimeMillis();
        totalLoteActual = comandos.size();
        barraProgreso.setProgress(0.0);
        lblResumenCabecera.setText("Ejecutando " + totalLoteActual + " proceso(s) en modo " + modo + "...");

        if ("Paralelo".equals(modo)) {
            // MODO PARALELO: Se inician todos los procesos del S.O. de golpe sin esperar
            for (List<String> cmd : comandos) {
                lanzarUnProcesoAsincrono(cmd, tarea, modo);
            }
        } else {
            // MODO INDIVIDUAL O SECUENCIAL: Se lanza el primero y el resto queda en cola hasta que termine
            colaPendienteSecuencial.clear();
            colaPendienteSecuencial.addAll(comandos);
            List<String> primero = colaPendienteSecuencial.remove(0);
            lanzarUnProcesoAsincrono(primero, tarea, modo);
        }
    }
    private void ejecutarExperimentoRendimiento() {
        int limiteN;
        try {
            limiteN = Integer.parseInt(txtLimiteN.getText().trim());
            if (limiteN < 2) {
                txtStderr.setText("[ERROR] El límite N para Prime Benchmark debe ser mayor o igual que 2.\n");
                return;
            }
        } catch (NumberFormatException ex) {
            txtStderr.setText("[ERROR] Introduce un número entero válido en Límite N antes de lanzar el experimento.\n");
            return;
        }

        btnIniciar.setDisable(true);
        btnBenchmark.setDisable(true);
        txtStdout.setText("Ejecutando experimento de escalabilidad con N=" + limiteN + "...\n");

        int[] procesosAcumulados = {0};
        lblTotalEjecutados.setText("0");
        lblFinalizados.setText("0");
        lblEnEjecucion.setText("0");
        lblConError.setText("0");
        barraProgreso.setProgress(0.0);

        List<Integer> lotes = new ArrayList<>(ServicioRendimiento.CANTIDADES_EXPERIMENTO_POR_DEFECTO);
        List<ServicioRendimiento.ResultadoComparativa> resultados = new ArrayList<>();
        long inicioGlobal = System.currentTimeMillis();

        Timeline ejecutorLotes = new Timeline();
        ejecutorLotes.setCycleCount(lotes.size());

        final int[] indice = {0};
        ejecutorLotes.getKeyFrames().add(new KeyFrame(Duration.millis(50), ev -> {
            int cantidad = lotes.get(indice[0]++);
            lblResumenCabecera.setText("Benchmark N=" + limiteN + " | Lote de " + cantidad + " proceso(s)...");
            ServicioRendimiento.ResultadoComparativa res = servicioRendimiento.compararModoPrimeBenchmark(
                    cantidad,
                    limiteN,
                    linea -> {}
            );
            resultados.add(res);
            txtStdout.setText("Lote completado: " + cantidad + " procesos (Sec: "
                    + res.getTiempoSecuencialMs() + " ms | Par: "
                    + res.getTiempoParaleloMs() + " ms)\n\n"
                    + servicioRendimiento.formatearTablaResultados(resultados));

            procesosAcumulados[0] += cantidad * 2;
            lblTotalEjecutados.setText(String.valueOf(procesosAcumulados[0]));
            lblFinalizados.setText(String.valueOf(procesosAcumulados[0]));
            lblTiempoLote.setText("Tiempo acumulado: " + (System.currentTimeMillis() - inicioGlobal) + " ms");
            barraProgreso.setProgress((double) indice[0] / lotes.size());
        }));

        ejecutorLotes.setOnFinished(ev -> {
            long totalMs = System.currentTimeMillis() - inicioGlobal;
            lblTotalEjecutados.setText(procesosAcumulados[0] + " (6 lotes)");
            lblFinalizados.setText(String.valueOf(procesosAcumulados[0]));
            lblEnEjecucion.setText("0");
            lblTiempoLote.setText("Tiempo total (experimento): " + totalMs + " ms");
            barraProgreso.setProgress(1.0);
            lblResumenCabecera.setText("Experimento finalizado en " + totalMs + " ms");
            txtStdout.setText(servicioRendimiento.formatearTablaResultados(resultados));
            btnIniciar.setDisable(false);
            btnBenchmark.setDisable(false);
        });

        ejecutorLotes.play();
    }

    private void lanzarUnProcesoAsincrono(List<String> comando, String tarea, String modo) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(false);

            String modoOut = comboStdout.getValue();
            String modoErr = comboStderr.getValue();
            String modoIn = comboStdin.getValue();

            File tempOut = null;
            File tempErr = null;

            // Configurar stdout según ProcessBuilder.Redirect
            if ("DISCARD".equals(modoOut)) {
                pb.redirectOutput(Redirect.DISCARD);
            } else if ("Fichero".equals(modoOut)) {
                pb.redirectOutput(Redirect.to(archivoDestinoStdout));
            } else {
                tempOut = File.createTempFile("df_out_", ".tmp");
                tempOut.deleteOnExit();
                pb.redirectOutput(Redirect.to(tempOut));
            }

            // Configurar stderr según ProcessBuilder.Redirect
            if ("DISCARD".equals(modoErr)) {
                pb.redirectError(Redirect.DISCARD);
            } else if ("Fichero".equals(modoErr)) {
                pb.redirectError(Redirect.to(archivoDestinoStderr));
            } else {
                tempErr = File.createTempFile("df_err_", ".tmp");
                tempErr.deleteOnExit();
                pb.redirectError(Redirect.to(tempErr));
            }

            // Configurar stdin desde fichero si corresponde
            if ("Text Analyzer".equals(tarea) && "Desde fichero (stdin)".equals(modoIn)) {
                pb.redirectInput(Redirect.from(archivoOrigenStdin));
            }

            long ahora = System.currentTimeMillis();
            Process proceso = pb.start();

            // Si stdin es "Desde DataForge", escribir por getOutputStream() y cerrar para EOF
            if ("Text Analyzer".equals(tarea) && "Desde DataForge".equals(modoIn)) {
                try (BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(proceso.getOutputStream(), StandardCharsets.UTF_8))) {
                    bw.write(txtContenidoStdin.getText());
                    bw.newLine();
                    bw.flush();
                }
            }

            String resumenParams = comando.get(comando.size() - 2) + " | " + comando.get(comando.size() - 1);
            if ("Prime Benchmark".equals(tarea)) {
                resumenParams = "N=" + comando.get(comando.size() - 1);
            }

            ProcesoInfo info = new ProcesoInfo(
                    listaProcesos.size() + 1,
                    proceso.pid(),
                    tarea,
                    modo,
                    resumenParams,
                    LocalTime.ofInstant(Instant.ofEpochMilli(ahora), ZoneId.systemDefault()).format(formatoHora),
                    "-",
                    "-",
                    "-",
                    "Ejecutando"
            );
            listaProcesos.add(info);

            ProcesoEnCurso pc = new ProcesoEnCurso();
            pc.proceso = proceso;
            pc.info = info;
            pc.tiempoInicioMs = ahora;
            pc.tempStdout = tempOut;
            pc.tempStderr = tempErr;
            pc.modoStdout = modoOut;
            pc.modoStderr = modoErr;
            procesosVigilados.add(pc);

            actualizarEstadisticas();

        } catch (IOException ex) {
            txtStderr.appendText("[ERROR] No se pudo iniciar el proceso: " + ex.getMessage() + "\n");
        }
    }

    private void vigilarProcesosActivos() {
        if (procesosVigilados.isEmpty()) return;

        List<ProcesoEnCurso> terminados = new ArrayList<>();

        for (ProcesoEnCurso pc : procesosVigilados) {
            try {
                if (!pc.proceso.isAlive() || pc.proceso.waitFor(2, TimeUnit.MILLISECONDS)) {
                    int exitCode = pc.proceso.waitFor();
                    long finMs = System.currentTimeMillis();
                    long duracion = finMs - pc.tiempoInicioMs;

                    pc.info.setFin(LocalTime.ofInstant(Instant.ofEpochMilli(finMs), ZoneId.systemDefault()).format(formatoHora));
                    pc.info.setDuracion(String.valueOf(duracion));
                    pc.info.setExitCode(String.valueOf(exitCode));

                    if (pc.canceladoPorUsuario) {
                        pc.info.setEstado("Cancelado (destroy)");
                    } else if (exitCode == 0) {
                        pc.info.setEstado("Finalizado");
                    } else {
                        pc.info.setEstado("Error");
                    }

                    // Recoger stdout según redirección elegida
                    String outTexto;
                    if ("Capturar en DataForge".equals(pc.modoStdout)) {
                        outTexto = leerFicheroTemporal(pc.tempStdout);
                    } else if ("Fichero".equals(pc.modoStdout)) {
                        outTexto = "stdout → FICHERO: " + archivoDestinoStdout.getAbsolutePath();
                    } else {
                        outTexto = "stdout → DISCARD: salida descartada.";
                    }
                    pc.info.setSalidaStdout(outTexto);

                    // Recoger stderr según redirección elegida
                    String errTexto;
                    if ("Capturar en DataForge".equals(pc.modoStderr)) {
                        errTexto = leerFicheroTemporal(pc.tempStderr);
                    } else if ("Fichero".equals(pc.modoStderr)) {
                        errTexto = "stderr → FICHERO: " + archivoDestinoStderr.getAbsolutePath();
                    } else {
                        errTexto = "stderr → DISCARD: salida de error descartada.";
                    }
                    pc.info.setSalidaStderr(errTexto);

                    // Mostrar salida en los paneles inferiores
                    txtStdout.setText(outTexto);
                    txtStderr.setText(errTexto);

                    terminados.add(pc);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        if (!terminados.isEmpty()) {
            procesosVigilados.removeAll(terminados);
            tablaProcesos.refresh();
            actualizarEstadisticas();

            // Si estamos en modo Secuencial y quedan procesos en cola, lanzar el siguiente ahora
            if (!colaPendienteSecuencial.isEmpty() && procesosVigilados.isEmpty()) {
                List<String> siguiente = colaPendienteSecuencial.remove(0);
                lanzarUnProcesoAsincrono(siguiente, comboTareas.getValue(), "Secuencial");
            } else if (procesosVigilados.isEmpty() && colaPendienteSecuencial.isEmpty()) {
                long tiempoTotalLote = System.currentTimeMillis() - inicioLoteMs;
                lblTiempoLote.setText("Tiempo total (lote): " + tiempoTotalLote + " ms");
                barraProgreso.setProgress(1.0);
                lblResumenCabecera.setText("Lote completado en " + tiempoTotalLote + " ms");
            }
        }
    }

    /**
     * Ejecuta la tarea "Pipeline: Prime Benchmark -> Text Analyzer" usando ProcessBuilder.startPipeline().
     */
    private void ejecutarTareaPipeline() {
        String limite = txtLimiteN.getText().trim();
        String palabra = txtPalabra.getText().trim();
        String classpath = System.getProperty("java.class.path");

        if (limite.isBlank() || palabra.isBlank()) {
            txtStderr.setText("[ERROR] Para el Pipeline debes indicar el límite N y la palabra a buscar.\n");
            return;
        }

        try {
            long inicio = System.currentTimeMillis();
            ProcessBuilder pb1 = new ProcessBuilder(
                    "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", classpath, "com.dataforge.tareas.PrimeBenchmark", limite
            );
            ProcessBuilder pb2 = new ProcessBuilder(
                    "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", classpath, "com.dataforge.tareas.TextAnalyzer", "stdin", palabra
            );

            // Conectar stdout de PrimeBenchmark -> stdin de TextAnalyzer mediante startPipeline()
            List<Process> procesos = ProcessBuilder.startPipeline(List.of(pb1, pb2));
            Process pPrimos = procesos.get(0);
            Process pAnalizador = procesos.get(1);

            int cod1 = pPrimos.waitFor();
            String salidaFinal = new String(pAnalizador.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errorFinal = new String(pAnalizador.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int cod2 = pAnalizador.waitFor();

            long fin = System.currentTimeMillis();
            long duracion = fin - inicio;

            String horaIni = LocalTime.ofInstant(Instant.ofEpochMilli(inicio), ZoneId.systemDefault()).format(formatoHora);
            String horaFin = LocalTime.ofInstant(Instant.ofEpochMilli(fin), ZoneId.systemDefault()).format(formatoHora);

            ProcesoInfo info1 = new ProcesoInfo(listaProcesos.size() + 1, pPrimos.pid(),
                    "Pipeline [1/2] PrimeBenchmark", "Pipeline", "N=" + limite,
                    horaIni, horaFin, String.valueOf(duracion), String.valueOf(cod1), cod1 == 0 ? "Finalizado" : "Error");
            info1.setSalidaStdout("Salida enviada directamente por PIPE a TextAnalyzer (PID " + pAnalizador.pid() + ")");

            ProcesoInfo info2 = new ProcesoInfo(listaProcesos.size() + 2, pAnalizador.pid(),
                    "Pipeline [2/2] TextAnalyzer", "Pipeline", "stdin | palabra: " + palabra,
                    horaIni, horaFin, String.valueOf(duracion), String.valueOf(cod2), cod2 == 0 ? "Finalizado" : "Error");
            info2.setSalidaStdout(salidaFinal);
            info2.setSalidaStderr(errorFinal);

            listaProcesos.addAll(info1, info2);
            txtStdout.setText(salidaFinal);
            txtStderr.setText(errorFinal);
            lblTiempoLote.setText("Tiempo total (lote): " + duracion + " ms");
            barraProgreso.setProgress(1.0);
            actualizarEstadisticas();

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            txtStderr.setText("[ERROR] Fallo en Pipeline: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Detiene el proceso seleccionado en la tabla (o todos los activos si no hay selección)
     * invocando Process.destroy().
     */
    private void detenerProcesoSeleccionado() {
        ProcesoInfo seleccionado = tablaProcesos.getSelectionModel().getSelectedItem();

        if (seleccionado != null) {
            for (ProcesoEnCurso pc : procesosVigilados) {
                if (pc.info == seleccionado && pc.proceso.isAlive()) {
                    pc.canceladoPorUsuario = true;
                    pc.proceso.destroy();
                    txtStderr.appendText("[CANCELADO] Proceso PID " + pc.proceso.pid() + " detenido con Process.destroy().\n");
                    return;
                }
            }
        }

        // Si no había fila seleccionada en ejecución, detener los que estén activos
        int detenidos = 0;
        for (ProcesoEnCurso pc : procesosVigilados) {
            if (pc.proceso.isAlive()) {
                pc.canceladoPorUsuario = true;
                pc.proceso.destroy();
                detenidos++;
            }
        }
        colaPendienteSecuencial.clear();
        if (detenidos > 0) {
            txtStderr.appendText("[CANCELADO] Se han detenido " + detenidos + " proceso(s) activo(s) mediante Process.destroy().\n");
        } else {
            txtStderr.appendText("[INFO] No hay ningún proceso en ejecución para detener.\n");
        }
    }

    private void actualizarEstadisticas() {
        int total = listaProcesos.size();
        int finalizados = 0;
        int ejecutando = 0;
        int errores = 0;

        for (ProcesoInfo p : listaProcesos) {
            switch (p.getEstado()) {
                case "Finalizado" -> finalizados++;
                case "Ejecutando" -> ejecutando++;
                default -> errores++;
            }
        }

        lblTotalEjecutados.setText(String.valueOf(total));
        lblFinalizados.setText(String.valueOf(finalizados));
        lblEnEjecucion.setText(String.valueOf(ejecutando));
        lblConError.setText(String.valueOf(errores));

        if (totalLoteActual > 0) {
            double completados = (double) (totalLoteActual - procesosVigilados.size() - colaPendienteSecuencial.size()) / totalLoteActual;
            barraProgreso.setProgress(Math.max(0.0, Math.min(1.0, completados)));
        }
    }

    private void actualizarBarraEstado() {
        lblEstadoBarra.setText("Modo: " + comboModo.getValue() + " | Procesos: " + spinnerProcesos.getValue());
    }

    private String leerFicheroTemporal(File f) {
        if (f == null || !f.exists()) return "";
        try {
            String contenido = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            f.delete();
            return contenido;
        } catch (IOException e) {
            return "";
        }
    }

    private File elegirArchivoAbrir(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.setInitialDirectory(new File("."));
        return fc.showOpenDialog(tablaProcesos.getScene().getWindow());
    }

    private File elegirArchivoGuardar(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.setInitialDirectory(new File("."));
        return fc.showSaveDialog(tablaProcesos.getScene().getWindow());
    }
}