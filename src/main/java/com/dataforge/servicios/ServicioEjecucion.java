package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
 * @version 1.0
 */
public class ServicioEjecucion {

    // Lista para almacenar los procesos activos (necesaria para el método destroy())
    private static final List<Process> procesosActivos = new ArrayList<>();

    /**
     * Ejecuta un proceso individual (como ya tenías funcionando).
     */
    public void iniciar(ConfiguracionProceso configuracion) {
        List<String> comando = List.of(
                "java",
                "-cp",
                System.getProperty("java.class.path"),
                "com.dataforge.tareas.TextAnalyzer",
                configuracion.getRuta(),
                configuracion.getPalabra()
        );
        ejecutarIndividualProceso(comando);
    }

    /**
     * Ejecución individual interna que gestiona ProcessBuilder, PID y waitFor().
     */
    private int ejecutarIndividualProceso(List<String> comando) {
        Process procesoActual = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(true);

            procesoActual = pb.start();

            synchronized (procesosActivos) {
                procesosActivos.add(procesoActual);
            }

            System.out.println("Proceso iniciado con PID: " + procesoActual.pid());

            // Lectura de la salida estándar
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(procesoActual.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    System.out.println(linea);
                }
            }

            int codigoSalida = procesoActual.waitFor();
            return codigoSalida;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error en la ejecución del proceso: " + e.getMessage());
            return -1;
        } finally {
            if (procesoActual != null) {
                synchronized (procesosActivos) {
                    procesosActivos.remove(procesoActual);
                }
            }
        }
    }

    /**
     * MODO SECUENCIAL: Los procesos se lanzan uno detrás de otro.
     * El siguiente no comienza hasta que el anterior termina.
     */
    public long ejecutarSecuencial(List<List<String>> listaComandos) {
        long tiempoInicioTotal = System.currentTimeMillis();
        System.out.println("--- INICIANDO EJECUCIÓN SECUENCIAL ---");

        for (List<String> comando : listaComandos) {
            int codigo = ejecutarIndividualProceso(comando);
            System.out.println("Proceso secuencial finalizado con código: " + codigo);
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        System.out.println("Tiempo total secuencial: " + duracionTotal + " ms");
        return duracionTotal;
    }

    /**
     * MODO PARALELO (SIN HILOS): Lanza todos los procesos de golpe y espera a que terminen.
     */
    public long ejecutarParalelo(List<List<String>> listaComandos) {
        long tiempoInicioTotal = System.currentTimeMillis();
        System.out.println("--- INICIANDO EJECUCIÓN PARALELA ---");

        List<Process> procesosLanzados = new ArrayList<>();

        // 1. Lanzar todos los procesos de forma simultánea sin usar hilos
        for (List<String> comando : listaComandos) {
            try {
                ProcessBuilder pb = new ProcessBuilder(comando);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                procesosLanzados.add(p);

                synchronized (procesosActivos) {
                    procesosActivos.add(p);
                }
                System.out.println("Proceso paralelo lanzado con PID: " + p.pid());
            } catch (IOException e) {
                System.err.println("Error al lanzar proceso en paralelo: " + e.getMessage());
            }
        }

        // 2. Esperar a que todos terminen mediante waitFor() y recoger códigos de salida
        for (Process p : procesosLanzados) {
            try {
                int codigo = p.waitFor();
                System.out.println("Proceso PID " + p.pid() + " terminó con código: " + codigo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Espera interrumpida para el proceso PID " + p.pid());
            } finally {
                synchronized (procesosActivos) {
                    procesosActivos.remove(p);
                }
            }
        }

        long tiempoFinTotal = System.currentTimeMillis();
        long duracionTotal = tiempoFinTotal - tiempoInicioTotal;
        System.out.println("Tiempo total paralelo: " + duracionTotal + " ms");
        return duracionTotal;
    }

    /**
     * Método para detener procesos activos mediante destroy() (Requisito del RA1).
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