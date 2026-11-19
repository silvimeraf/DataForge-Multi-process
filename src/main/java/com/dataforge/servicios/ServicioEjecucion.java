package com.dataforge.servicios;

import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.modelo.EjecucionProceso;

import java.io.*;
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

    // Lista para almacenar los procesos activos (necesaria para el método destroy())
    private static final List<Process> procesosActivos = new ArrayList<>();

    /**
     * Inicia un proceso externo para analizar un fichero.
     * Recibe los parámetros almacenados en ConfiguracionProceso y los transmite al programa TextAnalyzer.
     *
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
                System.out.println(
                        "Ejecución finalizada correctamente."
                );
            } else {
                System.err.println(
                        "Ejecución finalizada con error. Código: "
                                + codigoSalida
                );
            }
        } catch (IOException e) {
            // Error al iniciar el proceso o leer su salida.
            System.err.println(
                    "Error al iniciar o leer el proceso: "
                            + e.getMessage()
            );
        } catch (InterruptedException e) {
            // Restaurar el estado de interrupción del hilo actual.
            Thread.currentThread().interrupt();
            System.err.println(
                    "La espera del proceso fue interrumpida."
            );
        }
    }

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
     * Ejecuta TextAnalyzer utilizando la entrada estándar.
     * Inicia un proceso hijo, escribe el texto recibido en su stdin, cierra el flujo para indicar EOF
     * y recoge los resultados de la ejecución.
     *
     * @param texto   contenido que se desea analizar.
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
                    new OutputStreamWriter(
                            proceso.getOutputStream(),
                            StandardCharsets.UTF_8))) {
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
                System.out.println(
                        "Ejecución finalizada correctamente."
                );
            } else {
                System.err.println(
                        "Ejecución finalizada con error. Código: "
                                + codigoSalida
                );
            }
        } catch (IOException e) {
            System.err.println(
                    "Error de comunicación con el proceso: "
                            + e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(
                    "La espera del proceso fue interrumpida."
            );
            if (proceso != null && proceso.isAlive()) {
                proceso.destroy();
            }
        }
    }

    /**
     * Ejecuta PrimeBenchmark como un proceso Java independiente.
     * Utiliza ProcessBuilder para iniciar una nueva JVM, transmitir el límite numérico como
     * argumento y recoger los resultados generados por el proceso hijo.
     * También permite consultar el PID, esperar la finalización del proceso y comprobar su código de salida.
     *
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
                System.out.println(
                        "PrimeBenchmark finalizado correctamente."
                );

            } else {
                System.err.println(
                        "PrimeBenchmark finalizado con error. Código: "
                                + codigoSalida
                );
            }
        } catch (IOException e) {
            // Gestionar errores al iniciar o leer el proceso.
            System.err.println(
                    "Error al ejecutar PrimeBenchmark: "
                            + e.getMessage()
            );
        } catch (InterruptedException e) {
            // Restaurar el estado de interrupción del hilo actual.
            Thread.currentThread().interrupt();
            System.err.println(
                    "La espera de PrimeBenchmark fue interrumpida."
            );
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
     *
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
                try (BufferedReader lector = new BufferedReader(
                        new InputStreamReader(
                                proceso.getInputStream(),
                                StandardCharsets.UTF_8))) {
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
        long duracionTotal =
                System.currentTimeMillis() - tiempoInicioTotal;
        System.out.println("Duración total secuencial: " + duracionTotal + " ms");
        return duracionTotal;
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
     * Ejecuta una lista de comandos de forma paralela.
     * El paralelismo se consigue con PROCESOS del sistema operativo, no con hilos:
     * todos los procesos se inician con start() sin esperar a que termine ninguno, de modo que
     * el sistema operativo los ejecuta a la vez.
     * Funcionamiento:
     * FASE 1 - Lanzar: se recorre la lista y se llama a start() para cada comando. start() no bloquea.
     * FASE 2 - Vigilar: se revisa con isAlive() qué procesos han terminado. Cuando uno termina, se
     *          obtiene su código con waitFor() (devuelve al instante porque ya ha terminado), se
     *          registra su finalización y se muestra su salida.
     *          Si ninguno ha terminado todavía, se espera como máximo 10 ms a un proceso pendiente
     *          mediante waitFor(tiempo, unidad) para no consumir CPU en un bucle vacío.
     * FASE 3 - Resumen: tabla con PID, estado, tiempos y código de cada proceso, y duración total.
     * Nota: la salida de cada hijo se lee cuando ya ha terminado. Es suficiente porque PrimeBenchmark
     * y TextAnalyzer escriben muy pocas líneas (caben en el buffer de la tubería del sistema operativo).
     *
     * @param listaComandos comandos de los procesos que se ejecutarán.
     * @return duración total de la ejecución, en milisegundos.
     */
    public long ejecutarParalelo(List<List<String>> listaComandos) {
        // 1. Registrar el instante inicial del lote. Lo calcula DataForge, no se introduce a mano.
        long tiempoInicioTotal = System.currentTimeMillis();
        System.out.println("===== EJECUCIÓN PARALELA =====");
        ServicioMonitorizacion monitor = new ServicioMonitorizacion();
        // Listas paralelas: la posición i corresponde siempre al mismo proceso.
        List<Process> procesos = new ArrayList<>();
        List<EjecucionProceso> ejecuciones = new ArrayList<>();
        List<String> tareas = new ArrayList<>();
        // 2. FASE 1: iniciar todos los procesos sin esperar a ninguno.
        for (List<String> comando : listaComandos) {
            try {
                ProcessBuilder pb = new ProcessBuilder(comando);
                // Unificar stdout y stderr para consumir ambos desde un único flujo.
                pb.redirectErrorStream(true);
                Process proceso = pb.start();
                EjecucionProceso ejecucion = monitor.registrarInicio(proceso);
                procesos.add(proceso);
                ejecuciones.add(ejecucion);
                tareas.add(obtenerNombreTarea(comando));
                System.out.println("Lanzado proceso " + procesos.size()
                        + " | PID: " + ejecucion.getPid()
                        + " | Tarea: " + tareas.get(tareas.size() - 1)
                        + " | Estado: " + ejecucion.getEstado());
            } catch (IOException e) {
                // Si un proceso no se puede iniciar, se informa y se continúa con el resto.
                System.err.println("Error al lanzar un proceso: " + e.getMessage());
            }
        }
        // 3. FASE 2: esperar a que terminen todos los procesos lanzados.
        boolean[] finalizado = new boolean[procesos.size()];
        int pendientes = procesos.size();
        try {
            while (pendientes > 0) {
                boolean terminoAlguno = false;
                for (int i = 0; i < procesos.size(); i++) {
                    Process proceso = procesos.get(i);
                    // isAlive() no bloquea: solo comprueba si el proceso sigue activo.
                    if (finalizado[i] || proceso.isAlive()) {
                        continue;
                    }
                    // El proceso ya ha terminado: waitFor() devuelve su código de salida al instante.
                    int codigoSalida = proceso.waitFor();
                    EjecucionProceso ejecucion = ejecuciones.get(i);
                    monitor.registrarFinalizacion(ejecucion, codigoSalida);
                    finalizado[i] = true;
                    pendientes--;
                    terminoAlguno = true;
                    // Mostrar la salida del proceso y su resultado.
                    System.out.println("----- Proceso " + (i + 1) + " (PID " + ejecucion.getPid()
                            + ", " + tareas.get(i) + ") finalizado -----");
                    try (BufferedReader lector = new BufferedReader(
                            new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
                        String linea;
                        while ((linea = lector.readLine()) != null) {
                            System.out.println(linea);
                        }
                    } catch (IOException e) {
                        System.err.println("Error al leer la salida del proceso " + (i + 1)
                                + ": " + e.getMessage());
                    }
                    System.out.println("Estado final: " + ejecucion.getEstado()
                            + " | Duración: " + ejecucion.getDuracion() + " ms"
                            + " | Código de salida: " + ejecucion.getCodigoSalida());
                }
                if (!terminoAlguno && pendientes > 0) {
                    // Ninguno ha terminado en esta pasada: esperar un instante al primer proceso pendiente.
                    for (int i = 0; i < procesos.size(); i++) {
                        if (!finalizado[i]) {
                            procesos.get(i).waitFor(10, TimeUnit.MILLISECONDS);
                            break;
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            // Restaurar la interrupción y no dejar procesos huérfanos.
            Thread.currentThread().interrupt();
            System.err.println("La ejecución paralela ha sido interrumpida.");
            for (Process proceso : procesos) {
                if (proceso.isAlive()) {
                    proceso.destroy();
                }
            }
            return System.currentTimeMillis() - tiempoInicioTotal;
        }
        // 4. Calcular la duración total del lote: desde el primer start() hasta que termina el último.
        long duracionTotal = System.currentTimeMillis() - tiempoInicioTotal;
        // 5. FASE 3: tabla resumen generada automáticamente.
        mostrarTablaEjecuciones(ejecuciones, tareas);
        System.out.println("Procesos lanzados: " + procesos.size() + " de " + listaComandos.size());
        System.out.println("Duración total paralela: " + duracionTotal + " ms");
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

    /**
     * Muestra una tabla con los datos registrados de cada ejecución.
     *
     * @param ejecuciones registros de las ejecuciones, ya finalizadas.
     * @param tareas      nombre de la tarea de cada ejecución (misma posición que en ejecuciones).
     */
    private void mostrarTablaEjecuciones(List<EjecucionProceso> ejecuciones, List<String> tareas) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        System.out.println("===== RESUMEN DE EJECUCIONES =====");
        System.out.printf("%-4s %-8s %-15s %-11s %-13s %-13s %-14s %-6s%n",
                "ID", "PID", "Tarea", "Estado", "Inicio", "Fin", "Duración (ms)", "Código");
        for (int i = 0; i < ejecuciones.size(); i++) {
            EjecucionProceso e = ejecuciones.get(i);
            String inicio = LocalTime.ofInstant(Instant.ofEpochMilli(e.getTiempoInicio()),
                    ZoneId.systemDefault()).format(formato);
            String fin = LocalTime.ofInstant(Instant.ofEpochMilli(e.getTiempoFin()),
                    ZoneId.systemDefault()).format(formato);
            System.out.printf("%-4d %-8d %-15s %-11s %-13s %-13s %-14d %-6s%n",
                    i + 1, e.getPid(), tareas.get(i), e.getEstado(), inicio, fin,
                    e.getDuracion(), e.getCodigoSalida());
        }
    }

    /**
     * Obtiene el nombre de la tarea a partir de su comando, buscando la clase del paquete de tareas.
     * Ejemplo: "com.dataforge.tareas.PrimeBenchmark" devuelve "PrimeBenchmark".
     *
     * @param comando comando completo del proceso.
     * @return nombre de la tarea, o "Desconocida" si el comando no contiene una clase de tareas.
     */
    private String obtenerNombreTarea(List<String> comando) {
        String prefijo = "com.dataforge.tareas.";
        for (String parte : comando) {
            if (parte.startsWith(prefijo)) {
                return parte.substring(prefijo.length());
            }
        }
        return "Desconocida";
    }
}