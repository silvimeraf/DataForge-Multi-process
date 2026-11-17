package com.dataforge.servicios;
import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.modelo.EjecucionProceso;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servicio encargado de iniciar y gestionar procesos externos.
 * Utiliza ProcessBuilder para ejecutar tareas Java en procesos independientes del sistema operativo.
 * En esta primera versión implementa la ejecución individual de TextAnalyzer.
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
    /**
     * Inicia un proceso externo para analizar un fichero.
     * Recibe los parámetros almacenados en ConfiguracionProceso y los transmite  al programa TextAnalyzer.
     * @param configuracion configuración con la ruta y la palabra de búsqueda.
     */
    public void iniciar(ConfiguracionProceso configuracion) {
        System.out.println("Solicitud de ejecución recibida.");
        System.out.println("Ruta: " + configuracion.getRuta());
        System.out.println("Palabra: " + configuracion.getPalabra());
        try {
            // 1. Crear el constructor de procesos.
            ProcessBuilder pb = new ProcessBuilder();
            // 2. Definir el comando que ejecutará una nueva instancia de la JVM.
            pb.command("java", "-cp", System.getProperty("java.class.path"), "com.dataforge.tareas.TextAnalyzer",
                    configuracion.getRuta(), configuracion.getPalabra()
            );
            // 3. Unificar stderr con stdout. Ambos flujos podrán leerse desde el mismo InputStream.
            pb.redirectErrorStream(true);
            // 4. Iniciar el proceso hijo.
            Process p = pb.start();
            // Crear el servicio de monitorización.
            ServicioMonitorizacion monitor = new ServicioMonitorizacion();
            // Registrar el inicio del proceso hijo.
            EjecucionProceso ejecucion = monitor.registrarInicio(p);
            // Mostrar los datos iniciales.
            System.out.println("PID: " + ejecucion.getPid());
            System.out.println("Estado inicial: " + ejecucion.getEstado());
            // 5. Leer la salida del proceso hijo utilizando la codificación UTF-8.
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String linea = br.readLine();
                // Leer hasta que el proceso cierre su flujo de salida.
                while (linea != null) {
                    System.out.println(linea);
                    linea = br.readLine();
                }
            }
            // 6. Esperar a que termine el proceso. waitFor() devuelve su código de salida.
            int codigoSalida = p.waitFor();
            // Registrar la finalización del proceso.
            monitor.registrarFinalizacion(ejecucion, codigoSalida);
            // Mostrar los resultados de la monitorización.
            System.out.println("Estado final: " + ejecucion.getEstado());
            System.out.println("Duración monitorizada: "
                    + ejecucion.getDuracion() + " ms");
            System.out.println("Código de salida: "
                    + ejecucion.getCodigoSalida());
            // 7. Interpretar el resultado.
            if (codigoSalida == 0) {
                System.out.println("Ejecución finalizada correctamente.");
            } else {
                System.err.println("Ejecución finalizada con error. Código: " + codigoSalida);
            }
        } catch (IOException e) {
            // Error al iniciar el proceso o leer su salida.
            System.err.println("Error al iniciar o leer el proceso: " + e.getMessage());
        } catch (InterruptedException e) {
            // Restaurar el estado de interrupción del hilo actual.
            Thread.currentThread().interrupt();
            System.err.println("La espera del proceso fue interrumpida.");
        }
    }
    /**
     * Ejecuta TextAnalyzer utilizando la entrada estándar.
     * Inicia un proceso hijo, escribe el texto recibido en su stdin, cierra el flujo para indicar EOF
     * y recoge los resultados de la ejecución.
     * @param texto contenido que se desea analizar.
     * @param palabra palabra de búsqueda.
     */
    public void analizarTexto(String texto, String palabra) {
        Process proceso = null;
        try {
            // 1. Configurar la JVM hija y sus argumentos.
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Dfile.encoding=UTF-8",
                    "-cp",
                    System.getProperty("java.class.path"),
                    "com.dataforge.tareas.TextAnalyzer",
                    "stdin",
                    palabra
            );
            // 2. Combinar stdout y stderr para consumir ambos flujos desde un único lector.
            pb.redirectErrorStream(true);
            // 3. Iniciar el proceso hijo.
            proceso = pb.start();
            // Crear el servicio encargado de monitorizar la ejecución.
            ServicioMonitorizacion monitor = new ServicioMonitorizacion();
            // Registrar el PID, el estado inicial y el instante de inicio.
            EjecucionProceso ejecucion = monitor.registrarInicio(proceso);
            // Mostrar la información inicial del proceso.
            System.out.println("PID: " + ejecucion.getPid());
            System.out.println("Estado inicial: " + ejecucion.getEstado());
            // 4. Enviar el texto al stdin del proceso.
            try (BufferedWriter escritor = new BufferedWriter(
                    new OutputStreamWriter(proceso.getOutputStream(), StandardCharsets.UTF_8))) {
                escritor.write(texto);
                escritor.newLine();
            }
            // Al cerrar el escritor se comunica EOF.
            // TextAnalyzer puede terminar de leer stdin.
            // 5. Leer los resultados del proceso hijo.
            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(
                            proceso.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println(linea);
                }
            }
            // 6. Esperar la finalización del proceso.
            int codigoSalida = proceso.waitFor();
            // Registrar la finalización del proceso hijo.
            monitor.registrarFinalizacion(ejecucion, codigoSalida);
            // Mostrar los resultados de la monitorización.
            System.out.println("Estado final: " + ejecucion.getEstado());
            System.out.println("Duración monitorizada: "
                    + ejecucion.getDuracion() + " ms");
            System.out.println("Código de salida: "
                    + ejecucion.getCodigoSalida());
            if (codigoSalida == 0) {
                System.out.println("Ejecución finalizada correctamente.");
            } else {
                System.err.println("Ejecución finalizada con error. Código: " + codigoSalida);
            }
        } catch (IOException e) {
            System.err.println("Error de comunicación con el proceso: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("La espera del proceso fue interrumpida.");
            if (proceso != null && proceso.isAlive()) {
                proceso.destroy();
            }
        }
    }
    /**
     * Ejecuta PrimeBenchmark como un proceso Java independiente.
     * Utiliza ProcessBuilder para iniciar una nueva JVM,transmitir el límite numérico como
     * argumento y recoger los resultados generados por el proceso hijo.
     * También permite consultar el PID, esperar la finalización del proceso y comprobar su código de salida.
     * @param limite límite superior para calcular números primos.
     */
    public void ejecutarPrimeBenchmark(int limite) {
        // Referencia al proceso hijo para poder gestionarlo.
        Process proceso = null;
        try {
            // 1. Configurar el comando que ejecutará la JVM hija.
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Dfile.encoding=UTF-8",
                    "-cp",
                    System.getProperty("java.class.path"),
                    "com.dataforge.tareas.PrimeBenchmark",
                    String.valueOf(limite)
            );
            // 2. Unificar la salida estándar y la salida de errores.
            pb.redirectErrorStream(true);
            // 3. Iniciar el proceso hijo.
            proceso = pb.start();
            // Crea el servicio encargado de monitorizar el proceso hijo.
            ServicioMonitorizacion monitor = new ServicioMonitorizacion();
            // Registra el PID, el estado EJECUTANDO y el instante de inicio.
            EjecucionProceso ejecucion = monitor.registrarInicio(proceso);
            // Muestra la información registrada por el monitor.
            System.out.println("PID: " + ejecucion.getPid());
            System.out.println("Estado inicial: " + ejecucion.getEstado());
            // 4. Leer la salida generada por PrimeBenchmark.
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                String linea;
                // Leer hasta que el proceso cierre su salida.
                while ((linea = lector.readLine()) != null) {
                    System.out.println(linea);
                }
            }
            // 5. Esperar a que finalice el proceso hijo.
            int codigoSalida = proceso.waitFor();
            // Registra el resultado una vez finalizado el proceso hijo.
            monitor.registrarFinalizacion(ejecucion, codigoSalida);
            // Consulta el estado final almacenado en el modelo.
            System.out.println("Estado final: " + ejecucion.getEstado());
            // Muestra la duración registrada por el monitor, en milisegundos.
            System.out.println("Duración monitorizada: " + ejecucion.getDuracion() + " ms");
            // Muestra el código de salida del proceso hijo.
            System.out.println("Código de salida: " + ejecucion.getCodigoSalida());
            // 6. Comprobar el resultado de la ejecución.
            if (codigoSalida == 0) {
                System.out.println("PrimeBenchmark finalizado correctamente.");

            } else {
                System.err.println("PrimeBenchmark finalizado con error. Código: " + codigoSalida);
            }
        } catch (IOException e) {
            // Gestionar errores al iniciar o leer el proceso.
            System.err.println("Error al ejecutar PrimeBenchmark: " + e.getMessage());
        } catch (InterruptedException e) {
            // Restaurar el estado de interrupción del hilo actual.
            Thread.currentThread().interrupt();
            System.err.println("La espera de PrimeBenchmark fue interrumpida.");
            // Finalizar el proceso hijo si continúa ejecutándose.
            if (proceso != null && proceso.isAlive()) {
                proceso.destroy();
            }
        }
    }
    /**
     * Ejecuta una lista de comandos de forma secuencial.
     * Cada proceso comienza únicamente cuando el anterior ha finalizado.
     * Registra el PID, estado, duración y código de salida de cada proceso mediante ServicioMonitorizacion.
     * @param listaComandos comandos de los procesos que se ejecutarán.
     * @return duración total de la ejecución, en milisegundos.
     */
    public long ejecutarSecuencial(List<List<String>> listaComandos) {
        // 1. Registrar el instante inicial del lote.
        long tiempoInicioTotal = System.currentTimeMillis();
        System.out.println("===== EJECUCIÓN SECUENCIAL =====");
        // 2. Recorrer los comandos en el orden recibido.
        for (List<String> comando : listaComandos) {
            Process proceso = null;
            try {
                // 3. Preparar el proceso hijo.
                ProcessBuilder pb = new ProcessBuilder(comando);
                // Unificar stdout y stderr para evitar dejar sin consumir uno de los dos flujos.
                pb.redirectErrorStream(true);
                // 4. Iniciar el proceso.
                proceso = pb.start();
                // 5. Registrar el inicio mediante nuestro monitor.
                ServicioMonitorizacion monitor = new ServicioMonitorizacion();
                EjecucionProceso ejecucion = monitor.registrarInicio(proceso);
                System.out.println("PID: " + ejecucion.getPid());
                System.out.println("Estado inicial: " + ejecucion.getEstado());
                // 6. Leer la salida del proceso hijo.
                try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                    String linea;
                    while ((linea = lector.readLine()) != null) {
                        System.out.println(linea);
                    }
                }
                // 7. Esperar su finalización. El siguiente proceso no comienza hasta terminar este.
                int codigoSalida = proceso.waitFor();
                // 8. Registrar los resultados individuales.
                monitor.registrarFinalizacion(ejecucion, codigoSalida);
                System.out.println("Estado final: " + ejecucion.getEstado());
                System.out.println("Duración: " + ejecucion.getDuracion() + " ms");
                System.out.println("Código de salida: " + ejecucion.getCodigoSalida());

            } catch (IOException e) {
                System.err.println("Error al ejecutar el proceso: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println(
                        "La ejecución secuencial ha sido interrumpida."
                );
                if (proceso != null && proceso.isAlive()) {
                    proceso.destroy();
                }
                // Detener el lote ante una interrupción.
                return System.currentTimeMillis() - tiempoInicioTotal;
            }
        }
        // 9. Calcular la duración total del lote.
        long duracionTotal = System.currentTimeMillis() - tiempoInicioTotal;
        System.out.println("Duración total secuencial: " + duracionTotal + " ms");
        return duracionTotal;
    }
    /**
     * Ejecuta varios procesos hijos en paralelo.
     * Primero inicia todos los procesos y después espera su finalización individual.
     * Cada proceso utiliza archivos temporales independientes para stdout y stderr, evitando bloqueos por saturación
     * de las tuberías de salida.
     * @param listaComandos comandos que se ejecutarán en paralelo.
     * @return duración total del lote en milisegundos.
     */
    public long ejecutarParalelo(List<List<String>> listaComandos) {

        long inicioTotal = System.currentTimeMillis();

        System.out.println("===== EJECUCIÓN PARALELA =====");

        // Referencias de los procesos iniciados.
        List<Process> procesos = new ArrayList<>();

        // Registros de monitorización correspondientes.
        List<EjecucionProceso> ejecuciones = new ArrayList<>();

        // Archivos temporales de salida y error.
        List<Path> salidas = new ArrayList<>();
        List<Path> errores = new ArrayList<>();

        ServicioMonitorizacion monitor = new ServicioMonitorizacion();

        try {

            // FASE 1: iniciar todos los procesos.
            for (List<String> comando : listaComandos) {

                // Crear y registrar inmediatamente el archivo de salida.
                Path salida = Files.createTempFile("dataforge_stdout_", ".txt");
                salidas.add(salida);

                // Crear y registrar inmediatamente el archivo de error.
                Path error = Files.createTempFile("dataforge_stderr_", ".txt");
                errores.add(error);
                ProcessBuilder pb = new ProcessBuilder(comando);

                // Mantener separados stdout y stderr.
                pb.redirectOutput(salida.toFile());
                pb.redirectError(error.toFile());

                Process proceso = pb.start();
                // Registrar inmediatamente el proceso para poder cerrarlo si falla la monitorización.
                procesos.add(proceso);
                // Registrar su monitorización
                EjecucionProceso ejecucion = monitor.registrarInicio(proceso);
                ejecuciones.add(ejecucion);
                System.out.println("Proceso iniciado. PID: " + ejecucion.getPid());
            }

            // FASE 2: esperar las finalizaciones.
            for (int i = 0; i < procesos.size(); i++) {

                Process proceso = procesos.get(i);
                EjecucionProceso ejecucion = ejecuciones.get(i);
                int codigoSalida = proceso.waitFor();
                monitor.registrarFinalizacion(ejecucion, codigoSalida);

                System.out.println("===== RESULTADO DEL PROCESO " + (i + 1) + " =====");
                System.out.println("PID: " + ejecucion.getPid());
                System.out.println("Estado: " + ejecucion.getEstado());
                System.out.println("Duración: " + ejecucion.getDuracion() + " ms");
                System.out.println("Código de salida: " + ejecucion.getCodigoSalida());

                // Recuperar stdout y cerrar el flujo al finalizar.
                System.out.println("----- STDOUT -----");

                try (var lineasSalida = Files.lines(salidas.get(i), StandardCharsets.UTF_8)) {

                    lineasSalida.forEach(System.out::println);
                }

                // Recuperar stderr y cerrar el flujo al finalizar.
                System.out.println("----- STDERR -----");

                try (var lineasError = Files.lines(errores.get(i), StandardCharsets.UTF_8)) {

                    lineasError.forEach(System.err::println);
                }
            }

        } catch (IOException e) {
            System.err.println("Error durante la ejecución paralela: " + e.getMessage());
            // Esperar a que terminen los procesos antes de liberar sus archivos.
            finalizarProcesos(procesos);

        } catch (InterruptedException e) {
            System.err.println("La ejecución paralela fue interrumpida.");
            // El métod o auxiliar restaura el estado de interrupción cuando corresponde.
            finalizarProcesos(procesos);
            Thread.currentThread().interrupt();

        } finally {

            // Eliminar los archivos temporales de salida.
            for (Path salida : salidas) {
                try {
                    Files.deleteIfExists(salida);
                } catch (IOException e) {
                    System.err.println(
                            "No se pudo eliminar el archivo temporal: "
                                    + salida + " | Causa: " + e.getMessage()
                    );
                }
            }

            // Eliminar los archivos temporales de error.
            for (Path error : errores) {
                try {
                    Files.deleteIfExists(error);
                } catch (IOException e) {
                    System.err.println(
                            "No se pudo eliminar el archivo temporal: " + error
                                    + " | Causa: " + e.getMessage()
                    );
                }
            }
        }

        long duracionTotal = System.currentTimeMillis() - inicioTotal;

        System.out.println("Duración total paralela: " + duracionTotal + " ms");

        return duracionTotal;
    }
    /**
     * Solicita la terminación de los procesos hijos.
     * Espera un máximo de 2 segundos por proceso y, si alguno continúa activo, fuerza su terminación.
     * @param procesos lista de procesos hijos iniciados.
     */
    private void finalizarProcesos(List<Process> procesos) {

        boolean interrumpido = Thread.interrupted();

        // 1. Solicitar la terminación de todos los procesos activos.
        for (Process proceso : procesos) {
            if (proceso.isAlive()) {
                proceso.destroy();
            }
        }

        // 2. Esperar su finalización con un tiempo máximo.
        for (Process proceso : procesos) {
            try {
                if (!proceso.waitFor(2, TimeUnit.SECONDS)) {

                    // Forzar la terminación si sigue activo.
                    proceso.destroyForcibly();

                    // Esperar a que termine realmente.
                    proceso.waitFor();
                }

            } catch (InterruptedException e) {

                interrumpido = true;

                // Forzar la terminación del proceso.
                proceso.destroyForcibly();

                // Esperar a que finalice, conservando después
                // el estado de interrupción.
                boolean finalizado = false;

                while (!finalizado) {
                    try {
                        proceso.waitFor();
                        finalizado = true;
                    } catch (InterruptedException nuevaInterrupcion) {
                        interrumpido = true;
                    }
                }
            }
        }
        // Cerrar los flujos asociados a los procesos hijos.
        for (Process proceso : procesos) {
            try {
                proceso.getOutputStream().close();
                proceso.getInputStream().close();
                proceso.getErrorStream().close();
            } catch (IOException e) {
                System.err.println(
                        "Error al cerrar los flujos del proceso "
                                + proceso.pid() + ": " + e.getMessage()
                );
            }
        }

        // 3. Restaurar el estado de interrupción original.
        if (interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
