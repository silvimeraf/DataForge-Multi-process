package com.dataforge.controlador;

import com.dataforge.modelo.ModoEjecucion;
import com.dataforge.modelo.ProcesoInfo;
import com.dataforge.servicios.ServicioComunicacion;
import com.dataforge.servicios.ServicioEjecucion;
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

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador principal de la interfaz gráfica de DataForge (RA1).
 * Se encarga de conectar los elementos visuales definidos en el archivo FXML con los
 * servicios de negocio. Recoge las acciones del usuario, valida los datos básicos del
 * formulario y actualiza la tabla y los paneles de salida sin utilizar hilos manuales,
 * apoyándose en un Timeline de JavaFX para no bloquear la pantalla mientras corren los procesos.
 */
public class ControladorVista {

    // Controles de la zona superior donde se elige qué tarea ejecutar y se puede vaciar la tabla
    @FXML private ComboBox<String> comboTareas;
    @FXML private Label lblDescripcionTarea;
    @FXML private Button btnLimpiarTabla;

    // Contenedores y campos de parámetros que se muestran u ocultan según la tarea elegida
    @FXML private VBox boxFicheroEntrada;
    @FXML private TextField txtRutaFichero;
    @FXML private Button btnExaminarEntrada;
    @FXML private VBox boxTextoStdin;
    @FXML private TextArea txtContenidoStdin;
    @FXML private VBox boxLimiteN;
    @FXML private TextField txtLimiteN;
    @FXML private VBox boxPalabraBuscar;
    @FXML private TextField txtPalabra;

    // Controles para configurar el modo de lanzamiento, el número de copias y los botones principales
    @FXML private ComboBox<String> comboModo;
    @FXML private Spinner<Integer> spinnerProcesos;
    @FXML private Button btnIniciar;
    @FXML private Button btnDetener;
    @FXML private Button btnBenchmark;

    // Desplegables, botones y etiquetas informativas para redirigir los flujos estándar (stdout, stderr y stdin)
    @FXML private ComboBox<String> comboStdout;
    @FXML private Button btnFicheroStdout;
    @FXML private Label lblRutaStdout;
    @FXML private ComboBox<String> comboStderr;
    @FXML private Button btnFicheroStderr;
    @FXML private Label lblRutaStderr;
    @FXML private ComboBox<String> comboStdin;
    @FXML private Button btnFicheroStdin;
    @FXML private Label lblRutaStdin;

    // Tabla central que actúa como monitor en vivo de todos los procesos lanzados
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

    // Áreas de texto inferiores para leer por separado la salida normal y los errores de cada proceso
    @FXML private TextArea txtStdout;
    @FXML private Button btnLimpiarStdout;
    @FXML private TextArea txtStderr;
    @FXML private Button btnLimpiarStderr;

    // Etiquetas y barra de progreso con el resumen estadístico global de las ejecuciones
    @FXML private Label lblTotalEjecutados;
    @FXML private Label lblFinalizados;
    @FXML private Label lblEnEjecucion;
    @FXML private Label lblConError;
    @FXML private Label lblTiempoLote;
    @FXML private ProgressBar barraProgreso;
    @FXML private Label lblResumenCabecera;
    @FXML private Label lblEstadoBarra;

    // Lista observable enlazada a la tabla: cualquier cambio aquí se refleja automáticamente en pantalla
    private final ObservableList<ProcesoInfo> listaProcesos = FXCollections.observableArrayList();
    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Referencias a los ficheros seleccionados por el usuario cuando elige redirigir flujos a disco
    private File archivoDestinoStdout;
    private File archivoDestinoStderr;
    private File archivoOrigenStdin;

    // Temporizador de JavaFX que revisa periódicamente si los procesos activos han acabado
    private Timeline vigilanteProcesos;
    private long inicioLoteMs = 0L;
    private int totalLoteActual = 0;

    // Instancias de los servicios que contienen la lógica real de procesos, tuberías y rendimiento
    private final ServicioEjecucion servicioEjecucion = new ServicioEjecucion();
    private final ServicioComunicacion servicioComunicacion = new ServicioComunicacion();
    private final ServicioRendimiento servicioRendimiento = new ServicioRendimiento();

    /**
     * Método que JavaFX ejecuta automáticamente al cargar la vista FXML.
     * Deja preparados los valores por defecto de los desplegables, enlaza las columnas
     * de la tabla con los atributos de ProcesoInfo, asigna las acciones a todos los botones
     * y arranca el temporizador que vigila los procesos en segundo plano sin usar hilos.
     */
    @FXML
    public void initialize() {
        // Rellenamos los desplegables con las opciones disponibles y fijamos la selección inicial
        comboTareas.getItems().addAll(
                "Text Analyzer",
                "Prime Benchmark",
                "Pipeline: Prime Benchmark -> Text Analyzer"
        );
        comboTareas.setValue("Text Analyzer");

        comboModo.getItems().addAll("Individual", "Secuencial", "Paralelo");
        comboModo.setValue("Individual");

        // El selector numérico permite lanzar hasta 100 procesos, pero arranca desactivado en modo Individual
        spinnerProcesos.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        spinnerProcesos.setDisable(true);

        comboStdout.getItems().addAll("Capturar en DataForge", "Fichero", "DISCARD");
        comboStdout.setValue("Capturar en DataForge");

        comboStderr.getItems().addAll("Capturar en DataForge", "Fichero", "DISCARD");
        comboStderr.setValue("Capturar en DataForge");

        comboStdin.getItems().addAll("Fichero (argumento)", "Desde DataForge", "Desde fichero (stdin)");
        comboStdin.setValue("Fichero (argumento)");

        // Vinculamos cada columna de la tabla con el nombre exacto del atributo en la clase ProcesoInfo
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

        // Cuando el usuario hace clic en una fila de la tabla, cargamos abajo lo que escribió ese proceso concreto
        tablaProcesos.getSelectionModel().selectedItemProperty().addListener((obs, ant, sel) -> {
            if (sel != null) {
                txtStdout.setText(sel.getSalidaStdout());
                txtStderr.setText(sel.getSalidaStderr());
            }
        });

        // Hacemos que el formulario se adapte al instante cada vez que cambia la tarea o alguna redirección
        comboTareas.setOnAction(e -> actualizarFormularioDinamico());
        comboStdin.setOnAction(e -> actualizarRedireccionesUI());
        comboStdout.setOnAction(e -> actualizarRedireccionesUI());
        comboStderr.setOnAction(e -> actualizarRedireccionesUI());

        // Si el modo es Individual fijamos 1 proceso; si pasa a Secuencial o Paralelo habilitamos el selector
        comboModo.setOnAction(e -> {
            ModoEjecucion modoSeleccionado = resolverModoEjecucion(comboModo.getValue());
            boolean esIndividual = modoSeleccionado == ModoEjecucion.INDIVIDUAL;
            spinnerProcesos.setDisable(esIndividual);
            if (esIndividual) {
                spinnerProcesos.getValueFactory().setValue(1);
            } else if (spinnerProcesos.getValue() == 1) {
                spinnerProcesos.getValueFactory().setValue(5);
            }
            actualizarBarraEstado();
        });
        spinnerProcesos.valueProperty().addListener((o, a, b) -> actualizarBarraEstado());

        // Configuramos los botones que abren el explorador de archivos del sistema operativo
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

        // Conectamos los botones principales de ejecución, parada, experimento y limpieza
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

        // Arrancamos un temporizador indefinido que revisa cada 80 milisegundos si los procesos hijos han terminado.
        // Esto permite mantener la ventana fluida y reactiva sin incumplir la prohibición de usar Thread.
        vigilanteProcesos = new Timeline(new KeyFrame(Duration.millis(80), e -> vigilarProcesosActivos()));
        vigilanteProcesos.setCycleCount(Timeline.INDEFINITE);
        vigilanteProcesos.play();

        actualizarFormularioDinamico();
        actualizarRedireccionesUI();
    }

    /**
     * Muestra u oculta las cajas de texto de parámetros según la tarea seleccionada y el origen
     * de datos elegido para stdin. De este modo el usuario solo ve los campos que necesita rellenar
     * y no quedan huecos vacíos en la interfaz gracias a setManaged.
     */
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

    /**
     * Habilita o deshabilita los botones de selección de fichero en la zona de redirecciones
     * y actualiza las etiquetas descriptivas para que siempre se sepa hacia dónde va cada flujo.
     */
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

    /**
     * Se ejecuta al pulsar el botón Iniciar. Valida que no falten rutas obligatorias cuando se ha
     * elegido redirigir a fichero y delega en ServicioEjecucion (o ServicioComunicacion si es un
     * pipeline) el lanzamiento de los procesos en modo Individual, Secuencial o Paralelo.
     */
    private void ejecutarAccionPrincipal() {
        String tarea = comboTareas.getValue();
        String modo = comboModo.getValue();
        ModoEjecucion modoEjecucion = resolverModoEjecucion(modo);
        int numProcesos = modoEjecucion == ModoEjecucion.INDIVIDUAL ? 1 : spinnerProcesos.getValue();

        // Comprobamos antes de lanzar nada que el usuario haya elegido archivo si marcó la opción Fichero
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

        // Si la tarea elegida es el encadenamiento con tubería, seguimos su flujo específico
        if (tarea != null && tarea.startsWith("Pipeline")) {
            ejecutarTareaPipeline();
            return;
        }

        if ("Prime Benchmark".equals(tarea)) {
            try {
                int limiteN = Integer.parseInt(txtLimiteN.getText().trim());
                if (limiteN < 2) {
                    txtStderr.setText("[ERROR] El límite N para Prime Benchmark debe ser mayor o igual que 2.\n");
                    return;
                }
            } catch (NumberFormatException ex) {
                txtStderr.setText("[ERROR] Introduce un número entero válido en Límite N.\n");
                return;
            }
        }

        // Pedimos al servicio de ejecución que prepare la lista de comandos Java con sus argumentos
        List<List<String>> comandos = servicioEjecucion.construirComandos(
                tarea, numProcesos, txtLimiteN.getText().trim(),
                txtPalabra.getText().trim(), comboStdin.getValue(), txtRutaFichero.getText().trim()
        );

        inicioLoteMs = System.currentTimeMillis();
        totalLoteActual = comandos.size();
        barraProgreso.setProgress(0.0);
        lblResumenCabecera.setText("Ejecutando " + totalLoteActual + " proceso(s) en modo " + modo + "...");

        if (modoEjecucion == ModoEjecucion.PARALELO) {
            // En paralelo lanzamos todos los comandos seguidos sin esperar a que termine el anterior
            for (List<String> cmd : comandos) {
                servicioEjecucion.lanzarUnProcesoAsincrono(
                        cmd, tarea, modo, listaProcesos.size() + 1,
                        comboStdout.getValue(), archivoDestinoStdout,
                        comboStderr.getValue(), archivoDestinoStderr,
                        comboStdin.getValue(), archivoOrigenStdin, txtContenidoStdin.getText(),
                        info -> { listaProcesos.add(info); actualizarEstadisticas(); },
                        err -> txtStderr.appendText(err)
                );
            }
        } else {
            // En individual o secuencial lanzamos solo el primero; los demás quedan en cola hasta que este acabe
            servicioEjecucion.iniciarLoteSecuencial(
                    comandos, tarea, modo, listaProcesos.size() + 1,
                    comboStdout.getValue(), archivoDestinoStdout,
                    comboStderr.getValue(), archivoDestinoStderr,
                    comboStdin.getValue(), archivoOrigenStdin, txtContenidoStdin.getText(),
                    info -> { listaProcesos.add(info); actualizarEstadisticas(); },
                    err -> txtStderr.appendText(err)
            );
        }
    }

    /**
     * Convierte el texto del modo seleccionado en la interfaz a su enumeración
     * {@link ModoEjecucion} correspondiente, usando {@code INDIVIDUAL} como valor por defecto.
     */
    private ModoEjecucion resolverModoEjecucion(String modo) {
        if ("Paralelo".equals(modo)) {
            return ModoEjecucion.PARALELO;
        }
        if ("Secuencial".equals(modo)) {
            return ModoEjecucion.SECUENCIAL;
        }
        return ModoEjecucion.INDIVIDUAL;
    }

    /**
     * Lanza la batería automática de pruebas de rendimiento (1, 5, 10, 20, 50 y 100 procesos)
     * comparando ejecución secuencial frente a paralela sobre PrimeBenchmark.
     * Utiliza un Timeline para ir ejecutando un tamaño de lote en cada paso y poder refrescar
     * la tabla comparativa y la barra de progreso en pantalla entre lote y lote.
     */
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

        List<Integer> lotes = new ArrayList<>(ServicioRendimiento.CANTIDADES_EXPERIMENTO_POR_DEFECTO);
        List<ServicioRendimiento.ResultadoComparativa> resultados = new ArrayList<>();
        long inicioGlobal = System.currentTimeMillis();

        final int[] indiceLote = {0};
        final int[] fase = {0}; // 0 = iniciar secuencial, 1 = esperar secuencial, 2 = iniciar paralelo, 3 = esperar paralelo
        final ServicioRendimiento.LoteNoBloqueante[] loteActual = {null};
        final long[] tiempoSecActual = {0L};
        final int[] procesosAcumulados = {0};
        final int totalLotes = lotes.size();

        lblTotalEjecutados.setText("0");
        lblFinalizados.setText("0");
        lblEnEjecucion.setText("0");
        lblConError.setText("0");
        barraProgreso.setProgress(0.0);

        Timeline ejecutorEscalado = new Timeline();
        ejecutorEscalado.setCycleCount(Timeline.INDEFINITE);

        ejecutorEscalado.getKeyFrames().add(new KeyFrame(Duration.millis(40), ev -> {
            if (indiceLote[0] >= totalLotes) {
                ejecutorEscalado.stop();
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
                return;
            }

            int cantidad = lotes.get(indiceLote[0]);

            if (fase[0] == 0) {
                lblResumenCabecera.setText("Benchmark N=" + limiteN + " | Lote " + cantidad + " (Secuencial)...");
                loteActual[0] = servicioRendimiento.iniciarLoteSecuencialNoBloqueante(cantidad, limiteN);
                fase[0] = 1;
            } else if (fase[0] == 1) {
                if (servicioRendimiento.avanzarLoteNoBloqueante(loteActual[0], true)) {
                    tiempoSecActual[0] = System.currentTimeMillis() - loteActual[0].inicioMs;
                    fase[0] = 2;
                }
            } else if (fase[0] == 2) {
                lblResumenCabecera.setText("Benchmark N=" + limiteN + " | Lote " + cantidad + " (Paralelo)...");
                loteActual[0] = servicioRendimiento.iniciarLoteParaleloNoBloqueante(cantidad, limiteN);
                fase[0] = 3;
            } else if (fase[0] == 3) {
                if (servicioRendimiento.avanzarLoteNoBloqueante(loteActual[0], false)) {
                    long tiempoPar = System.currentTimeMillis() - loteActual[0].inicioMs;
                    ServicioRendimiento.ResultadoComparativa res = new ServicioRendimiento.ResultadoComparativa(
                            cantidad, limiteN, tiempoSecActual[0], tiempoPar
                    );
                    resultados.add(res);

                    procesosAcumulados[0] += cantidad * 2;
                    lblTotalEjecutados.setText(String.valueOf(procesosAcumulados[0]));
                    lblFinalizados.setText(String.valueOf(procesosAcumulados[0]));
                    txtStdout.setText("Lote completado: " + cantidad + " procesos (Sec: "
                            + res.getTiempoSecuencialMs() + " ms | Par: "
                            + res.getTiempoParaleloMs() + " ms)\n\n"
                            + servicioRendimiento.formatearTablaResultados(resultados));

                    indiceLote[0]++;
                    fase[0] = 0;
                    barraProgreso.setProgress((double) indiceLote[0] / totalLotes);
                }
            }

            lblTiempoLote.setText("Tiempo acumulado: " + (System.currentTimeMillis() - inicioGlobal) + " ms");
        }));

        ejecutorEscalado.play();
    }

    /**
     * Método invocado cada 80 ms por el Timeline vigilante.
     * Consulta a ServicioEjecucion si alguno de los procesos activos ya ha finalizado. Cuando ocurre,
     * refresca la tabla, actualiza los contadores y, si estamos en modo Secuencial con tareas pendientes
     * en la cola, lanza el siguiente proceso; si ya no queda ninguno, calcula el tiempo total del lote.
     */
    private void vigilarProcesosActivos() {
        boolean huboTerminados = servicioEjecucion.vigilarProcesosActivos(
                archivoDestinoStdout,
                archivoDestinoStderr,
                out -> {
                    if (out != null && !out.isBlank()) {
                        txtStdout.appendText(out.endsWith("\n") ? out : out + "\n");
                    }
                },
                err -> {
                    if (err != null && !err.isBlank()) {
                        txtStderr.appendText(err.endsWith("\n") ? err : err + "\n");
                    }
                }
        );

        if (huboTerminados) {
            tablaProcesos.refresh();
            actualizarEstadisticas();

            if (servicioEjecucion.hayColaSecuencial() && !servicioEjecucion.hayProcesosVigilados()) {
                List<String> siguiente = servicioEjecucion.extraerSiguienteSecuencial();
                servicioEjecucion.lanzarUnProcesoAsincrono(
                        siguiente, comboTareas.getValue(), "Secuencial", listaProcesos.size() + 1,
                        comboStdout.getValue(), archivoDestinoStdout,
                        comboStderr.getValue(), archivoDestinoStderr,
                        comboStdin.getValue(), archivoOrigenStdin, txtContenidoStdin.getText(),
                        info -> { listaProcesos.add(info); actualizarEstadisticas(); },
                        err -> txtStderr.appendText(err)
                );
            } else if (!servicioEjecucion.hayProcesosVigilados() && !servicioEjecucion.hayColaSecuencial()) {
                long tiempoTotalLote = System.currentTimeMillis() - inicioLoteMs;
                lblTiempoLote.setText("Tiempo total (lote): " + tiempoTotalLote + " ms");
                barraProgreso.setProgress(1.0);
                lblResumenCabecera.setText("Lote completado en " + tiempoTotalLote + " ms");
            }
        }
    }

    /**
     * Ejecuta la tarea encadenada donde la salida estándar de PrimeBenchmark alimenta
     * directamente la entrada estándar de TextAnalyzer mediante ProcessBuilder.startPipeline(),
     * registrando ambos procesos en la tabla y mostrando el resultado final en pantalla.
     */
    private void ejecutarTareaPipeline() {
        String limite = txtLimiteN.getText().trim();
        String palabra = txtPalabra.getText().trim();

        if (limite.isBlank() || palabra.isBlank()) {
            txtStderr.setText("[ERROR] Para el Pipeline debes indicar el límite N y la palabra a buscar.\n");
            return;
        }

        try {
            List<ProcesoInfo> resultados = servicioComunicacion.ejecutarPipelineUI(
                    listaProcesos.size() + 1, limite, palabra, formatoHora
            );
            ProcesoInfo info2 = resultados.get(1);

            listaProcesos.addAll(resultados);
            txtStdout.setText(info2.getSalidaStdout());
            txtStderr.setText(info2.getSalidaStderr());
            lblTiempoLote.setText("Tiempo total (lote): " + info2.getDuracion() + " ms");
            barraProgreso.setProgress(1.0);
            actualizarEstadisticas();

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            txtStderr.setText("[ERROR] Fallo en Pipeline: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Cancela el proceso seleccionado en la tabla (o todos los que sigan activos si no hay ninguno
     * marcado) llamando a Process.destroy() a través de ServicioEjecucion.
     */
    private void detenerProcesoSeleccionado() {
        ProcesoInfo seleccionado = tablaProcesos.getSelectionModel().getSelectedItem();
        String mensaje = servicioEjecucion.detenerProcesos(seleccionado);
        txtStderr.appendText(mensaje);
    }

    /**
     * Recorre la lista de procesos de la tabla para recalcular los contadores inferiores
     * (total ejecutados, finalizados con éxito, en ejecución y con error o cancelados)
     * y actualizar el porcentaje de la barra de progreso del lote en curso.
     */
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
            double completados = (double) (totalLoteActual - servicioEjecucion.getPendientesTotales()) / totalLoteActual;
            barraProgreso.setProgress(Math.max(0.0, Math.min(1.0, completados)));
        }
    }

    /**
     * Actualiza el texto de la barra inferior indicando el modo y la cantidad de procesos configurados.
     */
    private void actualizarBarraEstado() {
        lblEstadoBarra.setText("Modo: " + comboModo.getValue() + " | Procesos: " + spinnerProcesos.getValue());
    }

    /**
     * Abre el diálogo nativo del sistema operativo para seleccionar un fichero existente.
     */
    private File elegirArchivoAbrir(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.setInitialDirectory(new File("."));
        return fc.showOpenDialog(tablaProcesos.getScene().getWindow());
    }

    /**
     * Abre el diálogo nativo del sistema operativo para elegir dónde guardar un fichero de salida.
     */
    private File elegirArchivoGuardar(String titulo) {
        FileChooser fc = new FileChooser();
        fc.setTitle(titulo);
        fc.setInitialDirectory(new File("."));
        return fc.showSaveDialog(tablaProcesos.getScene().getWindow());
    }
}