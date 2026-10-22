
package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Iniciar procesos, gestionar modos de ejecución y detenerlos.
 */
public class ServicioEjecucion {

    public void iniciar(ConfiguracionProceso configuracion) {

        System.out.println("Solicitud de ejecución recibida.");
        System.out.println("Ruta: " + configuracion.getRuta());
        System.out.println("Palabra: " + configuracion.getPalabra());

        try {
            ProcessBuilder pb = new ProcessBuilder();

            pb.command(
                    "java",
                    "-cp",
                    System.getProperty("java.class.path"),
                    "com.dataforge.tareas.TextAnalyzer",
                    configuracion.getRuta(),
                    configuracion.getPalabra()
            );

            // Unificar stderr y stdout para leer ambos sin bloqueo
            pb.redirectErrorStream(true);

            // Iniciar el proceso hijo
            Process p = pb.start();

            // Leer la salida del proceso hijo
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            p.getInputStream(),
                            StandardCharsets.UTF_8))) {

                String linea = br.readLine();

                while (linea != null) {
                    System.out.println(linea);
                    linea = br.readLine();
                }
            }

            // Esperar a que termine y obtener su código de salida
            int codigoSalida = p.waitFor();

            if (codigoSalida == 0) {
                System.out.println("Ejecución finalizada correctamente.");
            } else {
                System.err.println(
                        "Ejecución finalizada con error. Código: "
                                + codigoSalida
                );
            }

        } catch (IOException e) {
            System.err.println(
                    "Error al iniciar o leer el proceso: " + e.getMessage()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("La espera del proceso fue interrumpida.");
        }
    }
}

