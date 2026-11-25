package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.dataforge.modelo.ProcesoInfo;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Servicio encargado de iniciar y gestionar procesos externos.
 * Utiliza ProcessBuilder para ejecutar tareas Java en procesos independientes del sistema operativo.
 * Responsabilidades:
 * - Construir el comando de ejecución.
 * - Iniciar un proceso hijo.
 * - Leer su salida estándar y de errores.
 * - Esperar su finalización.
 * - Comprobar el código de salida.
 * Pertenece a la capa Servicios.
 */
//Ojo, incorporo prints de control
public class ServicioEjecucion {

    // Lista para almacenar los procesos activos (necesaria para el método destroy())
    private static final List<Process> procesosActivos = new ArrayList<>();

    /**
     * Ejecuta un proceso individual notificando el PID y las líneas de salida en tiempo real
     * hacia la interfaz gráfica mediante funciones de retrollamada (Consumer).
     *
     * @param configuracion    Configuración con la ruta y parámetros de la tarea.
     * @param consumidorSalida Callback para enviar cada línea de texto leída al TextArea de la UI.
     * @param consumidorPid    Callback para notificar el PID real del proceso al iniciarse.
     * @return El código de salida (Exit Code) del proceso, o -1 en caso de error.
     */
    public int iniciar(ConfiguracionProceso configuracion, Consumer<String> consumidorSalida, Consumer<Long> consumidorPid) {
        List<String> comando = List.of(
                "java",
                "-Dfile.encoding=UTF-8", // Forzar codificación UTF-8 para evitar caracteres extraños
                "-cp",
                System.getProperty("java.class.path"),
                "com.dataforge.tareas.TextAnalyzer",
                configuracion.getRuta(),
                configuracion.getPalabra()
        );
        return ejecutarIndividualProceso(comando, consumidorSalida, consumidorPid);
    }

    /**
     * Ejecución individual interna que gestiona ProcessBuilder, PID, lectura de flujos y waitFor().
     */
    private int ejecutarIndividualProceso(List<String> comando, Consumer<String> consumidorSalida, Consumer<Long> consumidorPid) {
        Process procesoActual = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            // Redirigir la salida de errores junto con la estándar para capturarlo todo unificado
            pb.redirectErrorStream(true);

            procesoActual = pb.start();

            // Sincronizar y registrar el proceso activo para la parada de emergencia (destroy)
            synchronized (procesosActivos) {
                procesosActivos.add(procesoActual);
            }

            long pidReal = procesoActual.pid();
            if (consumidorPid != null) {
                consumidorPid.accept(pidReal); // Notificamos el PID real a la UI
            }
            consumidorSalida.accept("[INFO] Proceso iniciado con PID: " + pidReal);

            // Lectura en tiempo real de la salida estándar del subproceso con codificación UTF-8
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(procesoActual.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    consumidorSalida.accept(linea); // Enviamos cada línea al TextArea de la interfaz
                }
            }

            // Esperar a que el proceso concluya y capturar su código de salida real
            int codigoSalida = procesoActual.waitFor();
            consumidorSalida.accept("[INFO] Proceso finalizado con Exit Code: " + codigoSalida);
            return codigoSalida;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            consumidorSalida.accept("[ERROR] Error en la ejecución del proceso: " + e.getMessage());
            return -1;
        } finally {
            // Asegurar la retirada del proceso de la lista de activos al terminar
            if (procesoActual != null) {
                synchronized (procesosActivos) {
                    procesosActivos.remove(procesoActual);
                }
            }
        }
    }

    /**
     * MODO SECUENCIAL: Los procesos se lanzan uno detrás de otro de forma síncrona.
     */
    public long ejecutarSecuencial(List<List<String>> listaComandos, Consumer<String> consumidorSalida) {
        long tiempoInicioTotal = System.currentTimeMillis();
        consumidorSalida.accept("--- INICIANDO EJECUCIÓN SECUENCIAL ---");

        for (List<String> comando : listaComandos) {
            int codigo = ejecutarIndividualProceso(comando, consumidorSalida, null);
            consumidorSalida.accept("Proceso secuencial finalizado con código: " + codigo);
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        consumidorSalida.accept("Tiempo total secuencial: " + duracionTotal + " ms");
        return duracionTotal;
    }

    /**
     * MODO PARALELO (SIN HILOS MANUALES): Lanza todos los procesos del S.O. de golpe y espera.
     */
    public long ejecutarParalelo(List<List<String>> listaComandos, Consumer<String> consumidorSalida) {
        long tiempoInicioTotal = System.currentTimeMillis();
        consumidorSalida.accept("--- INICIANDO EJECUCIÓN PARALELA ---");

        List<Process> procesosLanzados = new ArrayList<>();

        for (List<String> comando : listaComandos) {
            try {
                ProcessBuilder pb = new ProcessBuilder(comando);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                procesosLanzados.add(p);

                synchronized (procesosActivos) {
                    procesosActivos.add(p);
                }
                consumidorSalida.accept("Proceso paralelo lanzado con PID: " + p.pid());
            } catch (IOException e) {
                consumidorSalida.accept("[ERROR] Error al lanzar proceso en paralelo: " + e.getMessage());
            }
        }

        for (Process p : procesosLanzados) {
            try {
                int codigo = p.waitFor();
                consumidorSalida.accept("Proceso PID " + p.pid() + " terminó con código: " + codigo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                consumidorSalida.accept("[ERROR] Espera interrumpida para el proceso PID " + p.pid());
            } finally {
                synchronized (procesosActivos) {
                    procesosActivos.remove(p);
                }
            }
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        consumidorSalida.accept("Tiempo total paralelo: " + duracionTotal + " ms");
        return duracionTotal;
    }

    /**
     * Método para detener todos los procesos activos mediante destroy() (Requisito del RA1).
     */
    public static void detenerProcesosActivos() {
        synchronized (procesosActivos) {
            for (Process p : procesosActivos) {
                if (p.isAlive()) {
                    p.destroy();
                    System.out.println("Proceso con PID " + p.pid() + " detenido por el usuario.");
                }
            }
        }
    }
    // Estructuras para vigilar procesos activos mediante Timeline de JavaFX (sin Thread)
    public static class ProcesoEnCurso {
        public Process proceso;
        public ProcesoInfo info;
        public long tiempoInicioMs;
        public boolean canceladoPorUsuario = false;
        public File tempStdout;
        public File tempStderr;
        public String modoStdout;
        public String modoStderr;
    }

    private final List<ProcesoEnCurso> procesosVigilados = new ArrayList<>();
    private final List<List<String>> colaPendienteSecuencial = new ArrayList<>();
    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final ServicioComunicacion servicioComunicacion = new ServicioComunicacion();

    /**
     * Construye la lista de comandos para Individual, Secuencial o Paralelo.
     */
    public List<List<String>> construirComandos(String tarea, int numProcesos, String limite,
                                                String palabra, String modoStdin, String rutaFichero) {
        List<List<String>> comandos = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");

        for (int i = 0; i < numProcesos; i++) {
            if ("Prime Benchmark".equals(tarea)) {
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.PrimeBenchmark", limite
                ));
            } else {
                String origen = "Fichero (argumento)".equals(modoStdin) ? rutaFichero : "stdin";
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.TextAnalyzer", origen, palabra
                ));
            }
        }
        return comandos;
    }

    /**
     * Prepara la cola secuencial y lanza el primer proceso.
     */
    public void iniciarLoteSecuencial(List<List<String>> comandos, String tarea, String modo,
                                      int siguienteId, String modoOut, File archivoOut,
                                      String modoErr, File archivoErr, String modoIn,
                                      File archivoIn, String contenidoStdin,
                                      Consumer<ProcesoInfo> alCrearProceso,
                                      Consumer<String> alError) {
        colaPendienteSecuencial.clear();
        colaPendienteSecuencial.addAll(comandos);
        List<String> primero = colaPendienteSecuencial.remove(0);
        lanzarUnProcesoAsincrono(primero, tarea, modo, siguienteId, modoOut, archivoOut,
                modoErr, archivoErr, modoIn, archivoIn, contenidoStdin, alCrearProceso, alError);
    }

    /**
     * Inicia un proceso individual sin bloquear JavaFX y lo deja registrado para su vigilancia.
     */
    public void lanzarUnProcesoAsincrono(List<String> comando, String tarea, String modo,
                                         int idProceso, String modoOut, File archivoOut,
                                         String modoErr, File archivoErr, String modoIn,
                                         File archivoIn, String contenidoStdin,
                                         Consumer<ProcesoInfo> alCrearProceso,
                                         Consumer<String> alError) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(false);

            File[] temporales = servicioComunicacion.configurarRedirecciones(
                    pb, modoOut, archivoOut, modoErr, archivoErr, tarea, modoIn, archivoIn
            );

            long ahora = System.currentTimeMillis();
            Process proceso = pb.start();

            // Si stdin es "Desde DataForge", escribir por getOutputStream() y cerrar para EOF
            if ("Text Analyzer".equals(tarea) && "Desde DataForge".equals(modoIn)) {
                try (BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(proceso.getOutputStream(), StandardCharsets.UTF_8))) {
                    bw.write(contenidoStdin);
                    bw.newLine();
                    bw.flush();
                }
            }

            String resumenParams = comando.get(comando.size() - 2) + " | " + comando.get(comando.size() - 1);
            if ("Prime Benchmark".equals(tarea)) {
                resumenParams = "N=" + comando.get(comando.size() - 1);
            }

            ProcesoInfo info = new ProcesoInfo(
                    idProceso,
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

            ProcesoEnCurso pc = new ProcesoEnCurso();
            pc.proceso = proceso;
            pc.info = info;
            pc.tiempoInicioMs = ahora;
            pc.tempStdout = temporales[0];
            pc.tempStderr = temporales[1];
            pc.modoStdout = modoOut;
            pc.modoStderr = modoErr;
            procesosVigilados.add(pc);

            alCrearProceso.accept(info);

        } catch (IOException ex) {
            alError.accept("[ERROR] No se pudo iniciar el proceso: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Comprueba los procesos activos sin bloquear. Devuelve true si terminó alguno en esta pasada.
     */
    public boolean vigilarProcesosActivos(File archivoDestinoStdout, File archivoDestinoStderr,
                                          Consumer<String> alActualizarStdout,
                                          Consumer<String> alActualizarStderr) {
        if (procesosVigilados.isEmpty()) return false;

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
                        outTexto = servicioComunicacion.leerFicheroTemporal(pc.tempStdout);
                    } else if ("Fichero".equals(pc.modoStdout)) {
                        outTexto = "stdout → FICHERO: " + archivoDestinoStdout.getAbsolutePath();
                    } else {
                        outTexto = "stdout → DISCARD: salida descartada.";
                    }
                    pc.info.setSalidaStdout(outTexto);

                    // Recoger stderr según redirección elegida
                    String errTexto;
                    if ("Capturar en DataForge".equals(pc.modoStderr)) {
                        errTexto = servicioComunicacion.leerFicheroTemporal(pc.tempStderr);
                    } else if ("Fichero".equals(pc.modoStderr)) {
                        errTexto = "stderr → FICHERO: " + archivoDestinoStderr.getAbsolutePath();
                    } else {
                        errTexto = "stderr → DISCARD: salida de error descartada.";
                    }
                    pc.info.setSalidaStderr(errTexto);

                    alActualizarStdout.accept(outTexto);
                    alActualizarStderr.accept(errTexto);

                    terminados.add(pc);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        if (!terminados.isEmpty()) {
            procesosVigilados.removeAll(terminados);
            return true;
        }
        return false;
    }

    public boolean hayProcesosVigilados() {
        return !procesosVigilados.isEmpty();
    }

    public boolean hayColaSecuencial() {
        return !colaPendienteSecuencial.isEmpty();
    }

    public List<String> extraerSiguienteSecuencial() {
        return colaPendienteSecuencial.remove(0);
    }

    public int getPendientesTotales() {
        return procesosVigilados.size() + colaPendienteSecuencial.size();
    }

    /**
     * Detiene el proceso seleccionado o todos los activos mediante Process.destroy().
     */
    public String detenerProcesos(ProcesoInfo seleccionado) {
        if (seleccionado != null) {
            for (ProcesoEnCurso pc : procesosVigilados) {
                if (pc.info == seleccionado && pc.proceso.isAlive()) {
                    pc.canceladoPorUsuario = true;
                    pc.proceso.destroy();
                    return "[CANCELADO] Proceso PID " + pc.proceso.pid() + " detenido con Process.destroy().\n";
                }
            }
        }

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
            return "[CANCELADO] Se han detenido " + detenidos + " proceso(s) activo(s) mediante Process.destroy().\n";
        }
        return "[INFO] No hay ningún proceso en ejecución para detener.\n";
    }
}