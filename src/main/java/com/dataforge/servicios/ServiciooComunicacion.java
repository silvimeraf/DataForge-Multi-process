package com.dataforge.servicios;

import com.dataforge.modelo.EjecucionProceso;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gestionar entradas, salidas, redirecciones y pipelines.
 * En esta versión implementa la tubería (pipe) entre PrimeBenchmark y TextAnalyzer
 * mediante ProcessBuilder.startPipeline().
 * Pertenece a la capa Servicios.
 */
public class ServiciooComunicacion {
    /**
     * Ejecuta el pipeline: PrimeBenchmark --stdout--> PIPE --stdin--> TextAnalyzer.
     * Funcionamiento:
     * 1. PrimeBenchmark calcula los primos hasta el límite y escribe el resultado por su stdout.
     * 2. startPipeline() conecta ese stdout directamente con el stdin de TextAnalyzer.
     *    DataForge no interviene en el paso de datos entre ambos procesos.
     * 3. TextAnalyzer lee su stdin hasta EOF (cuando PrimeBenchmark termina), analiza el texto,
     *    busca la palabra que recibe como argumento y escribe el resultado por su stdout.
     * 4. DataForge lee ese resultado mediante getInputStream() del ÚLTIMO proceso.
     * 5. Se espera la finalización de ambos procesos con waitFor() y se comprueban sus códigos de salida.
     * @param limite límite superior para PrimeBenchmark (ya validado por el controlador).
     * @param palabra palabra que TextAnalyzer buscará en la salida de PrimeBenchmark.
     * @return true si los dos procesos han terminado con código 0; false en caso contrario.
     */
    public boolean ejecutarPipeline(int limite, String palabra) {
        // Referencias a los procesos para poder detenerlos si hay una interrupción.
        List<Process> procesos = null;
        try {
            String classpath = System.getProperty("java.class.path");
            // 1. ProcessBuilder del primer proceso: PrimeBenchmark.
            // stdout.encoding / stderr.encoding: fuerzan UTF-8 en la salida del hijo cuando no es una consola,
            // para que las tildes ("Límite", "números") lleguen bien a TextAnalyzer, que lee stdin en UTF-8.
            ProcessBuilder pbPrimos = new ProcessBuilder(
                    "java",
                    "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8",
                    "-cp",
                    classpath,
                    "com.dataforge.tareas.PrimeBenchmark",
                    String.valueOf(limite)
            );
            // 2. ProcessBuilder del segundo proceso: TextAnalyzer. "stdin" indica que el texto llega por la
            // entrada estándar y la palabra buscada se pasa como argumento.
            ProcessBuilder pbAnalizador = new ProcessBuilder(
                    "java",
                    "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8",
                    "-Dstderr.encoding=UTF-8",
                    "-cp",
                    classpath,
                    "com.dataforge.tareas.TextAnalyzer",
                    "stdin",
                    palabra
            );
            // Solo en el ÚLTIMO proceso se unifica stderr con stdout: así se lee todo desde un único
            // InputStream. En el primero NO se puede, porque su stdout está conectado al pipe y mezclaría
            // los errores con los datos que recibe TextAnalyzer.
            pbAnalizador.redirectErrorStream(true);
            System.out.println("===== PIPELINE: PrimeBenchmark -> TextAnalyzer =====");
            System.out.println("Límite: " + limite + " | Palabra: " + palabra);
            // 3. Iniciar los dos procesos conectados por la tubería.
            procesos = ProcessBuilder.startPipeline(List.of(pbPrimos, pbAnalizador));
            Process primos = procesos.get(0);
            Process analizador = procesos.get(1);
            // 4. Registrar el inicio de ambos procesos.
            ServicioMonitorizacion monitor = new ServicioMonitorizacion();
            EjecucionProceso ejecPrimos = monitor.registrarInicio(primos);
            EjecucionProceso ejecAnalizador = monitor.registrarInicio(analizador);
            System.out.println("Proceso 1 (PrimeBenchmark) PID: " + ejecPrimos.getPid()
                    + " | Estado: " + ejecPrimos.getEstado());
            System.out.println("Proceso 2 (TextAnalyzer)   PID: " + ejecAnalizador.getPid()
                    + " | Estado: " + ejecAnalizador.getEstado());
            // 5. Esperar a PrimeBenchmark. Mientras calcula, TextAnalyzer ya está leyendo del pipe, por lo
            // que PrimeBenchmark nunca se queda bloqueado por falta de espacio en la tubería.
            int codigoPrimos = primos.waitFor();
            monitor.registrarFinalizacion(ejecPrimos, codigoPrimos);
            // El stderr de PrimeBenchmark no va al pipe: se lee aparte (solo tiene contenido si hubo error).
            String errorPrimos = leerFlujo(primos.getErrorStream());
            // 6. Leer la salida del ÚLTIMO proceso (TextAnalyzer) hasta que cierre su stdout.
            String salidaFinal = leerFlujo(analizador.getInputStream());
            // 7. Esperar a TextAnalyzer y registrar su finalización.
            int codigoAnalizador = analizador.waitFor();
            monitor.registrarFinalizacion(ejecAnalizador, codigoAnalizador);
            // 8. Mostrar los resultados.
            System.out.println("----- Salida final del pipeline (stdout de TextAnalyzer) -----");
            System.out.print(salidaFinal);
            if (!errorPrimos.isEmpty()) {
                System.err.println("----- stderr de PrimeBenchmark -----");
                System.err.print(errorPrimos);
            }
            System.out.println("----- Comprobación de procesos -----");
            mostrarResumen("PrimeBenchmark", ejecPrimos);
            mostrarResumen("TextAnalyzer", ejecAnalizador);
            // 9. El pipeline es correcto solo si ambos procesos terminan con código 0.
            boolean correcto = codigoPrimos == 0 && codigoAnalizador == 0;
            if (correcto) {
                System.out.println("Pipeline finalizado correctamente.");
            } else {
                System.err.println("Pipeline finalizado con errores. Códigos: PrimeBenchmark="
                        + codigoPrimos + ", TextAnalyzer=" + codigoAnalizador);
            }
            return correcto;
        } catch (IOException e) {
            // Error al iniciar el pipeline o al leer los flujos.
            System.err.println("Error en el pipeline: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            // Restaurar el estado de interrupción y no dejar procesos huérfanos.
            Thread.currentThread().interrupt();
            System.err.println("La espera del pipeline fue interrumpida.");
            if (procesos != null) {
                for (Process p : procesos) {
                    if (p.isAlive()) {
                        p.destroy();
                    }
                }
            }
            return false;
        }
    }
    /**
     * Lee un flujo de un proceso hasta su final (EOF) y lo devuelve como texto UTF-8.
     * @param flujo flujo del proceso (getInputStream() o getErrorStream()).
     * @return contenido leído, con un salto de línea al final de cada línea.
     * @throws IOException si falla la lectura.
     */
    private String leerFlujo(InputStream flujo) throws IOException {
        StringBuilder texto = new StringBuilder();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(flujo, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                texto.append(linea).append(System.lineSeparator());
            }
        }
        return texto.toString();
    }
    /**
     * Muestra el PID, estado, duración y código de salida de un proceso del pipeline.
     * @param nombre nombre de la tarea.
     * @param ejecucion registro de la ejecución ya finalizada.
     */
    private void mostrarResumen(String nombre, EjecucionProceso ejecucion) {
        System.out.println(nombre + " | PID: " + ejecucion.getPid()
                + " | Estado: " + ejecucion.getEstado()
                + " | Duración: " + ejecucion.getDuracion() + " ms"
                + " | Código de salida: " + ejecucion.getCodigoSalida());
    }
}
