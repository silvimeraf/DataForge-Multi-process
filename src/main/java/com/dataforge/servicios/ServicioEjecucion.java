package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ServicioEjecucion.java
 *
 * Clase encargada de la gestión integral del ciclo de vida de los procesos externos
 * dentro de la aplicación DataForge.
 *
 * Cumpliendo estrictamente con las directrices del proyecto (RA1), esta clase centraliza:
 * - La ejecución individual de tareas con seguimiento de PID y códigos de salida.
 * - El procesamiento por lotes en modo secuencial.
 * - La ejecución concurrente en modo paralelo utilizando exclusivamente orquestación
 *   de procesos del sistema operativo, prohibiendo categóricamente el uso de hilos (Threads).
 * - La monitorización de tiempos de ejecución y la terminación forzosa de procesos activos
 *   mediante el método destroy().
 *
 * @author Yessenia Martinez / Grupo DataForge
 * @version 2.0
 */
public class ServicioEjecucion {

    // Lista para almacenar los procesos activos (necesaria para el método destroy())
    private static final List<Process> procesosActivos = new ArrayList<>();

    /**
     * Ejecuta un proceso individual notificando el PID y las líneas de salida en tiempo real
     * hacia la interfaz gráfica mediante funciones de retrollamada (Consumer).
     *
     * @param configuracion     Configuración con la ruta y parámetros de la tarea.
     * @param consumidorSalida  Callback para enviar cada línea de texto leída al TextArea de la UI.
     * @param consumidorPid     Callback para notificar el PID real del proceso al iniciarse.
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
}