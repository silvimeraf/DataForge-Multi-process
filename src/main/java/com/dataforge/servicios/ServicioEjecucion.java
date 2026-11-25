package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.modelo.EjecucionProceso;
import com.dataforge.modelo.ModoEjecucion;
import com.dataforge.modelo.ProcesoInfo;
import com.dataforge.modelo.TipoTarea;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 - Servicio encargado de iniciar y gestionar procesos externos.
 - Utiliza ProcessBuilder para ejecutar tareas Java en procesos independientes del sistema operativo.
 - Responsabilidades:
 - - Construir el comando de ejecución.
 - - Iniciar un proceso hijo.
 - - Leer su salida estándar y de errores.
 - - Esperar su finalización.
 - - Comprobar el código de salida.
 - Pertenece a la capa Servicios.
 */
//Ojo, incorporo prints de control
public class ServicioEjecucion {

    /**
     - MODO SECUENCIAL: Los procesos se lanzan uno detrás de otro de forma síncrona.
     */
    public long ejecutarSecuencial(List<List<String>> listaComandos, Consumer<String> consumidorSalida) {
        long tiempoInicioTotal = System.currentTimeMillis();
        consumidorSalida.accept("--- INICIANDO EJECUCIÓN SECUENCIAL ---");

        for (List<String> comando : listaComandos) {
            int codigo = ejecutarProcesoSincrono(comando, consumidorSalida);
            consumidorSalida.accept("Proceso secuencial finalizado con código: " + codigo);
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        consumidorSalida.accept("Tiempo total secuencial: " + duracionTotal + " ms");
        return duracionTotal;
    }

    /**
     - MODO PARALELO (SIN HILOS MANUALES): Lanza todos los procesos del S.O. de golpe y espera.
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
            }
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        consumidorSalida.accept("Tiempo total paralelo: " + duracionTotal + " ms");
        return duracionTotal;
    }

    private int ejecutarProcesoSincrono(List<String> comando, Consumer<String> consumidorSalida) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            consumidorSalida.accept("[INFO] Proceso iniciado con PID: " + proceso.pid());
            int codigoSalida = proceso.waitFor();
            consumidorSalida.accept("[INFO] Proceso finalizado con Exit Code: " + codigoSalida);
            return codigoSalida;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            consumidorSalida.accept("[ERROR] Error en la ejecución del proceso: " + e.getMessage());
            return -1;
        }
    }

    // Estructuras para vigilar procesos activos mediante Timeline de JavaFX (sin Thread)
    public static class ProcesoEnCurso {
        public Process proceso;
        public EjecucionProceso ejecucion;
        public ProcesoInfo info;
        public long tiempoInicioMs;
        public boolean canceladoPorUsuario = false;
        public File tempStdout;
        public File tempStderr;
        public String modoStdout;
        public String modoStderr;
    }

    private final List<ProcesoEnCurso> procesosVigilados = new ArrayList<>();
    private final ServicioMonitorizacion servicioMonitorizacion = new ServicioMonitorizacion();
    private final List<List<String>> colaPendienteSecuencial = new ArrayList<>();
    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final ServicioComunicacion servicioComunicacion = new ServicioComunicacion();

    /**
     - Construye la lista de comandos para Individual, Secuencial o Paralelo.
     */
    public List<List<String>> construirComandos(String tarea, int numProcesos, String limite,
                                                String palabra, String modoStdin, String rutaFichero) {
        List<List<String>> comandos = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        TipoTarea tipoTarea = "Prime Benchmark".equals(tarea)
                ? TipoTarea.PRIME_BENCHMARK
                : TipoTarea.TEXT_ANALYZER;

        for (int i = 0; i < numProcesos; i++) {
            if (tipoTarea == TipoTarea.PRIME_BENCHMARK) {
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.PrimeBenchmark", limite
                ));
            } else {
                String origen = "Fichero (argumento)".equals(modoStdin) ? rutaFichero : "stdin";
                ConfiguracionProceso config = new ConfiguracionProceso(origen, palabra);
                comandos.add(List.of(
                        "java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", classpath, "com.dataforge.tareas.TextAnalyzer", config.getRuta(), config.getPalabra()
                ));
            }
        }
        return comandos;
    }

    /**
     - Prepara la cola secuencial y lanza el primer proceso.
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
     - Inicia un proceso individual sin bloquear JavaFX y lo deja registrado para su vigilancia.
     */
    public void lanzarUnProcesoAsincrono(List<String> comando, String tarea, String modo,
                                         int idProceso, String modoOut, File archivoOut,
                                         String modoErr, File archivoErr, String modoIn,
                                         File archivoIn, String contenidoStdin,
                                         Consumer<ProcesoInfo> alCrearProceso,
                                         Consumer<String> alError) {
        try {
            ModoEjecucion modoEnum = "Paralelo".equals(modo)
                    ? ModoEjecucion.PARALELO
                    : ("Secuencial".equals(modo) ? ModoEjecucion.SECUENCIAL : ModoEjecucion.INDIVIDUAL);
            if (modoEnum == ModoEjecucion.INDIVIDUAL) {
                colaPendienteSecuencial.clear();
            }

            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(false);

            File[] temporales = servicioComunicacion.configurarRedirecciones(
                    pb, modoOut, archivoOut, modoErr, archivoErr, tarea, modoIn, archivoIn
            );

            Process proceso = pb.start();
            EjecucionProceso ejecucion = servicioMonitorizacion.registrarInicio(proceso);
            long ahora = ejecucion.getTiempoInicio();

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
            pc.ejecucion = ejecucion;
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
     - Comprueba los procesos activos sin bloquear. Devuelve true si terminó alguno en esta pasada.
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
                    if (pc.canceladoPorUsuario) {
                        servicioMonitorizacion.registrarCancelacion(pc.ejecucion, exitCode);
                        pc.info.setEstado("Cancelado (destroy)");
                    } else {
                        servicioMonitorizacion.registrarFinalizacion(pc.ejecucion, exitCode);
                        pc.info.setEstado(exitCode == 0 ? "Finalizado" : "Error");
                    }
                    long finMs = pc.ejecucion.getTiempoFin();
                    long duracion = pc.ejecucion.getDuracion();

                    pc.info.setFin(LocalTime.ofInstant(Instant.ofEpochMilli(finMs), ZoneId.systemDefault()).format(formatoHora));
                    pc.info.setDuracion(String.valueOf(duracion));
                    pc.info.setExitCode(String.valueOf(exitCode));

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
     - Detiene el proceso seleccionado o todos los activos mediante Process.destroy().
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