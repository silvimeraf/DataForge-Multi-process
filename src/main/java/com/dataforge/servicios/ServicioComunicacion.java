package com.dataforge.servicios;

import com.dataforge.modelo.ProcesoInfo;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio encargado de gestionar entradas, salidas, redirecciones (fichero, descarte)
 * y tuberías entre procesos (startPipeline).
 * Pertenece a la capa Servicios.
 */
public class ServicioComunicacion {

    private final ServicioMonitorizacion servicioMonitorizacion = new ServicioMonitorizacion();

    /**
     * Configura las redirecciones de stdout, stderr y stdin en el ProcessBuilder
     * según las opciones elegidas en la interfaz.
     */
    public File[] configurarRedirecciones(ProcessBuilder pb, String modoOut, File archivoOut,
                                          String modoErr, File archivoErr,
                                          String tarea, String modoIn, File archivoIn) throws IOException {
        File tempOut = null;
        File tempErr = null;

        // Configurar stdout según ProcessBuilder.Redirect
        if ("DISCARD".equals(modoOut)) {
            pb.redirectOutput(Redirect.DISCARD);
        } else if ("Fichero".equals(modoOut)) {
            pb.redirectOutput(Redirect.to(archivoOut));
        } else {
            tempOut = File.createTempFile("df_out_", ".tmp");
            tempOut.deleteOnExit();
            pb.redirectOutput(Redirect.to(tempOut));
        }

        // Configurar stderr según ProcessBuilder.Redirect
        if ("DISCARD".equals(modoErr)) {
            pb.redirectError(Redirect.DISCARD);
        } else if ("Fichero".equals(modoErr)) {
            pb.redirectError(Redirect.to(archivoErr));
        } else {
            tempErr = File.createTempFile("df_err_", ".tmp");
            tempErr.deleteOnExit();
            pb.redirectError(Redirect.to(tempErr));
        }

        // Configurar stdin desde fichero si corresponde
        if ("Text Analyzer".equals(tarea) && "Desde fichero (stdin)".equals(modoIn)) {
            pb.redirectInput(Redirect.from(archivoIn));
        }

        return new File[]{tempOut, tempErr};
    }

    /**
     * Lee el contenido de un fichero temporal y lo elimina del disco.
     */
    public String leerFicheroTemporal(File f) {
        if (f == null || !f.exists()) return "";
        try {
            String contenido = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            f.delete();
            return contenido;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Ejecuta la tarea "Pipeline: Prime Benchmark -> Text Analyzer" usando ProcessBuilder.startPipeline()
     * y devuelve los dos registros ProcesoInfo para la tabla de la interfaz.
     */
    public List<ProcesoInfo> ejecutarPipelineUI(int idInicial, String limite, String palabra,
                                                DateTimeFormatter formatoHora)
            throws IOException, InterruptedException {
        String classpath = System.getProperty("java.class.path");
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

        ProcesoInfo info1 = servicioMonitorizacion.registrarInicio(
                idInicial, pPrimos, "Pipeline [1/2] PrimeBenchmark", "Pipeline", "N=" + limite, inicio
        );
        ProcesoInfo info2 = servicioMonitorizacion.registrarInicio(
                idInicial + 1, pAnalizador, "Pipeline [2/2] TextAnalyzer", "Pipeline", "stdin | palabra: " + palabra, inicio
        );

        int cod1 = pPrimos.waitFor();
        String errorPrimos = new String(pPrimos.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        String salidaFinal = new String(pAnalizador.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errorFinal = new String(pAnalizador.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int cod2 = pAnalizador.waitFor();

        long fin = System.currentTimeMillis();

        servicioMonitorizacion.registrarFinalizacion(info1, inicio, fin, cod1, false);
        info1.setSalidaStdout("Salida enviada directamente por PIPE a TextAnalyzer (PID " + pAnalizador.pid() + ")");
        info1.setSalidaStderr(errorPrimos);

        servicioMonitorizacion.registrarFinalizacion(info2, inicio, fin, cod2, false);
        info2.setSalidaStdout(salidaFinal);
        info2.setSalidaStderr(errorFinal);

        return List.of(info1, info2);
    }
}