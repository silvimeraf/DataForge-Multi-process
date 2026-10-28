package com.dataforge.servicios;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;

/**
 * Servicio encargado de la gestión de flujos estándar, redirecciones (fichero, descarte)
 * y tuberías entre procesos (startPipeline).
 */

public class ServicioComunicacion {

    /**
     * Ejecuta un proceso configurando redirecciones personalizadas para la salida (stdout/stderr)
     * o descarte de flujos.
     *
     * @param comando Lista con los argumentos del comando.
     * @param archivoSalida Fichero de destino si se quiere redirigir a disco (null si no aplica).
     * @param descartar True si se desea aplicar Redirect.DISCARD.
     */
    public int ejecutarConRedireccion(List<String> comando, File archivoSalida, boolean descartar) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);

            // Configuración de la redirección de salida según los parámetros
            if (descartar) {
                pb.redirectOutput(Redirect.DISCARD);
                pb.redirectError(Redirect.DISCARD);
                System.out.println("Flujos configurados para ser DESCARTADOS (Redirect.DISCARD).");
            } else if (archivoSalida != null) {
                pb.redirectOutput(Redirect.to(archivoSalida));
                pb.redirectError(Redirect.to(new File(archivoSalida.getAbsolutePath() + ".err")));
                System.out.println("Flujos redirigidos a disco en: " + archivoSalida.getAbsolutePath());
            } else {
                // Por defecto, redirigir stderr a stdout para capturarlo conjuntamente
                pb.redirectErrorStream(true);
            }

            Process proceso = pb.start();
            System.out.println("Proceso con redirección iniciado con PID: " + proceso.pid());

            int codigoSalida = proceso.waitFor();
            System.out.println("Proceso finalizado con código de salida: " + codigoSalida);
            return codigoSalida;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error en la ejecución con redirección: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Ejecuta una tubería (Pipeline) conectando la salida de un proceso
     * directamente con la entrada de otro utilizando ProcessBuilder.startPipeline().
     * Ejemplo: Prime Benchmark -> Text Analyzer[cite: 2]
     */
    public void ejecutarPipeline(List<String> comando1, List<String> comando2) {
        try {
            ProcessBuilder pb1 = new ProcessBuilder(comando1);
            ProcessBuilder pb2 = new ProcessBuilder(comando2);

            System.out.println("--- INICIANDO PIPELINE DE PROCESOS ---");

            // Lanzar la tubería utilizando la API nativa de Java
            List<Process> procesos = ProcessBuilder.startPipeline(List.of(pb1, pb2));

            for (Process p : procesos) {
                System.out.println("Proceso en pipeline iniciado con PID: " + p.pid());
            }

            // Esperar a que ambos procesos terminen
            for (int i = 0; i < procesos.size(); i++) {
                Process p = procesos.get(i);
                int codigo = p.waitFor();
                System.out.println("Etapa " + (i + 1) + " (PID " + p.pid() + ") finalizada con código: " + codigo);
            }

            System.out.println("--- PIPELINE FINALIZADO CORRECTAMENTE ---");

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error al ejecutar el pipeline de procesos: " + e.getMessage());
        }
    }
}
