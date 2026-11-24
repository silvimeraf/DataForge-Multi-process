package com.dataforge.servicios;

import com.dataforge.modelo.EjecucionProceso;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Servicio encargado de medir, comparar y estructurar métricas de rendimiento
 * al ejecutar lotes de procesos (por ejemplo, PrimeBenchmark) en modo secuencial
 * frente a paralelo utilizando {@link ServicioEjecucion} (sin hilos manuales).
 * Pertenece a la capa Servicios.
 */
public class ServicioRendimiento {

    /**
     * Tamaños de lote estándar del experimento de escalabilidad exigido en RA1[cite: 33].
     */
    public static final List<Integer> CANTIDADES_EXPERIMENTO_POR_DEFECTO =
            List.of(1, 5, 10, 20, 50, 100);

    // Servicio encargado de lanzar y coordinar los procesos del sistema operativo.
    private final ServicioEjecucion servicioEjecucion;

    /**
     * Crea una instancia de {@code ServicioRendimiento} con un {@link ServicioEjecucion} por defecto.
     */
    public ServicioRendimiento() {
        this(new ServicioEjecucion());
    }

    /**
     * Crea una instancia de {@code ServicioRendimiento} inyectando el {@link ServicioEjecucion} a utilizar.
     *
     * @param servicioEjecucion servicio responsable de ejecutar los lotes de procesos.
     */
    public ServicioRendimiento(ServicioEjecucion servicioEjecucion) {
        // Garantiza que el servicio de ejecución esté disponible antes de medir tiempos.
        this.servicioEjecucion = Objects.requireNonNull(
                servicioEjecucion, "servicioEjecucion no puede ser nulo");
    }

    /**
     * Estructura de datos que almacena los tiempos totales y métricas derivadas
     * al comparar una misma cantidad de procesos en modo secuencial y paralelo.
     */
    public static class ResultadoComparativa {
        private final int cantidadProcesos;
        private final int limitePrimos;
        private final long tiempoSecuencialMs;
        private final long tiempoParaleloMs;

        /**
         * Construye un resultado comparativo para una cantidad concreta de procesos y un límite {@code N}.
         *
         * @param cantidadProcesos   número de instancias de proceso ejecutadas en cada modo.
         * @param limitePrimos       límite superior {@code N} pasado a {@code PrimeBenchmark}.
         * @param tiempoSecuencialMs duración total del lote en modo secuencial (en milisegundos).
         * @param tiempoParaleloMs   duración total del lote en modo paralelo (en milisegundos).
         */
        public ResultadoComparativa(int cantidadProcesos,
                                    int limitePrimos,
                                    long tiempoSecuencialMs,
                                    long tiempoParaleloMs) {
            this.cantidadProcesos = cantidadProcesos;
            this.limitePrimos = limitePrimos;
            this.tiempoSecuencialMs = tiempoSecuencialMs;
            this.tiempoParaleloMs = tiempoParaleloMs;
        }

        /**
         * @return número de procesos del lote comparado.
         */
        public int getCantidadProcesos() {
            return cantidadProcesos;
        }

        /**
         * @return límite superior {@code N} utilizado en {@code PrimeBenchmark}.
         */
        public int getLimitePrimos() {
            return limitePrimos;
        }

        /**
         * @return duración total en milisegundos de la ejecución secuencial.
         */
        public long getTiempoSecuencialMs() {
            return tiempoSecuencialMs;
        }

        /**
         * @return duración total en milisegundos de la ejecución paralela.
         */
        public long getTiempoParaleloMs() {
            return tiempoParaleloMs;
        }

        /**
         * Calcula la diferencia absoluta de tiempo ({@code tiempoSecuencialMs - tiempoParaleloMs}).
         * Un valor positivo indica que el modo paralelo ha terminado antes que el secuencial.
         *
         * @return diferencia de tiempo en milisegundos.
         */
        public long getDiferenciaMs() {
            return tiempoSecuencialMs - tiempoParaleloMs;
        }

        /**
         * Calcula el factor de aceleración o <em>speedup</em> ({@code tiempoSecuencialMs / tiempoParaleloMs}).
         * Devuelve {@code 0.0} si el tiempo paralelo es menor o igual que cero.
         *
         * @return factor de aceleración aproximado del modo paralelo frente al secuencial.
         */
        public double getFactorAceleracion() {
            if (tiempoParaleloMs <= 0L) {
                return 0.0;
            }
            return (double) tiempoSecuencialMs / (double) tiempoParaleloMs;
        }
    }

    /**
     * Construye la lista de comandos para lanzar {@code cantidadProcesos} procesos externos
     * de {@code PrimeBenchmark} con el límite {@code limitePrimos}.
     *
     * @param cantidadProcesos cantidad de procesos a preparar (debe ser mayor que 0).
     * @param limitePrimos     límite superior {@code N} para el cálculo de números primos.
     * @return lista de comandos lista para pasar a {@link ServicioEjecucion}.
     * @throws IllegalArgumentException si {@code cantidadProcesos} es menor o igual que 0.
     */
    public List<List<String>> construirComandosPrimeBenchmark(int cantidadProcesos, int limitePrimos) {
        if (cantidadProcesos <= 0) {
            throw new IllegalArgumentException("La cantidad de procesos debe ser mayor que 0.");
        }
        // Recupera el classpath actual para lanzar cada proceso hijo en su propia JVM.
        String classpath = System.getProperty("java.class.path");
        List<List<String>> comandos = new ArrayList<>(cantidadProcesos);
        for (int i = 0; i < cantidadProcesos; i++) {
            comandos.add(List.of(
                    "java",
                    "-Dfile.encoding=UTF-8",
                    "-cp",
                    classpath,
                    "com.dataforge.tareas.PrimeBenchmark",
                    String.valueOf(limitePrimos)
            ));
        }
        return comandos;
    }

    /**
     * Ejecuta un lote de {@code PrimeBenchmark} en modo secuencial (uno detrás de otro)
     * y devuelve el tiempo total empleado en milisegundos.
     *
     * @param cantidadProcesos número de procesos a ejecutar.
     * @param limitePrimos     límite superior {@code N} para cada instancia.
     * @param salida           consumidor para recibir líneas o mensajes de salida (puede ser {@code null}).
     * @return tiempo total del lote secuencial en milisegundos.
     */
    public long medirSecuencialPrimeBenchmark(int cantidadProcesos,
                                              int limitePrimos,
                                              Consumer<String> salida) {
        List<List<String>> comandos = construirComandosPrimeBenchmark(cantidadProcesos, limitePrimos);
        // Evita comprobaciones de nulidad repetidas usando un consumidor vacío si no se pasa ninguno.
        Consumer<String> consumidorSeguro = salida != null ? salida : linea -> {};
        return servicioEjecucion.ejecutarSecuencial(comandos, consumidorSeguro);
    }

    /**
     * Ejecuta un lote de {@code PrimeBenchmark} en modo paralelo (lanzando los procesos
     * sin esperar a que termine cada uno antes de iniciar el siguiente) y devuelve el tiempo total en ms[cite: 32].
     *
     * @param cantidadProcesos número de procesos a ejecutar.
     * @param limitePrimos     límite superior {@code N} para cada instancia.
     * @param salida           consumidor para recibir líneas o mensajes de salida (puede ser {@code null}).
     * @return tiempo total del lote paralelo en milisegundos.
     */
    public long medirParaleloPrimeBenchmark(int cantidadProcesos,
                                            int limitePrimos,
                                            Consumer<String> salida) {
        List<List<String>> comandos = construirComandosPrimeBenchmark(cantidadProcesos, limitePrimos);
        Consumer<String> consumidorSeguro = salida != null ? salida : linea -> {};
        return servicioEjecucion.ejecutarParalelo(comandos, consumidorSeguro);
    }

    /**
     * Ejecuta y compara secuencialmente y en paralelo un lote de {@code PrimeBenchmark}
     * para una misma cantidad de procesos y un mismo límite {@code N}[cite: 32, 33].
     *
     * @param cantidadProcesos número de procesos del lote.
     * @param limitePrimos     límite superior {@code N} para el cálculo de primos.
     * @param salida           consumidor para registrar el progreso de la prueba (puede ser {@code null}).
     * @return objeto {@link ResultadoComparativa} con ambos tiempos y las métricas calculadas.
     */
    public ResultadoComparativa compararModoPrimeBenchmark(int cantidadProcesos,
                                                           int limitePrimos,
                                                           Consumer<String> salida) {
        Consumer<String> log = salida != null ? salida : linea -> {};
        log.accept("=== Benchmark [" + cantidadProcesos + " procesos | N=" + limitePrimos + "] ===");

        // Se mide primero el lote secuencial y luego el paralelo con los mismos parámetros.
        long tiempoSecuencial = medirSecuencialPrimeBenchmark(cantidadProcesos, limitePrimos, log);
        long tiempoParalelo = medirParaleloPrimeBenchmark(cantidadProcesos, limitePrimos, log);

        return new ResultadoComparativa(
                cantidadProcesos,
                limitePrimos,
                tiempoSecuencial,
                tiempoParalelo
        );
    }

    /**
     * Ejecuta la batería completa de comparativas para cada tamaño de lote indicado
     * (o {@link #CANTIDADES_EXPERIMENTO_POR_DEFECTO} si la lista es nula o vacía)[cite: 33].
     *
     * @param cantidades   lista de cantidades de procesos a probar (por ejemplo: 1, 5, 10, 20, 50, 100)[cite: 33].
     * @param limitePrimos límite superior {@code N} común para todas las pruebas[cite: 33].
     * @param salida       consumidor para mostrar el avance de la ejecución (puede ser {@code null}).
     * @return lista ordenada de {@link ResultadoComparativa} obtenida en el experimento.
     */
    public List<ResultadoComparativa> ejecutarExperimentoEscalabilidad(List<Integer> cantidades,
                                                                       int limitePrimos,
                                                                       Consumer<String> salida) {
        List<Integer> lotes = (cantidades == null || cantidades.isEmpty())
                ? CANTIDADES_EXPERIMENTO_POR_DEFECTO
                : cantidades;
        List<ResultadoComparativa> resultados = new ArrayList<>(lotes.size());
        for (int cantidad : lotes) {
            resultados.add(compararModoPrimeBenchmark(cantidad, limitePrimos, salida));
        }
        return resultados;
    }

    /**
     * Calcula la duración media en milisegundos a partir de una colección de {@link EjecucionProceso}.
     *
     * @param ejecuciones lista de ejecuciones registradas.
     * @return duración media en milisegundos, o {@code 0.0} si la lista está vacía o es nula.
     */
    public double calcularDuracionMediaMs(List<EjecucionProceso> ejecuciones) {
        if (ejecuciones == null || ejecuciones.isEmpty()) {
            return 0.0;
        }
        long suma = 0L;
        for (EjecucionProceso ejecucion : ejecuciones) {
            if (ejecucion != null) {
                // Evita acumular duraciones negativas si algún registro no tiene duración válida.
                suma += Math.max(0L, ejecucion.getDuracion());
            }
        }
        return (double) suma / ejecuciones.size();
    }

    /**
     * Formatea una tabla de texto alineada con los resultados comparativos para mostrarla
     * en consola, en la interfaz o como apoyo para la memoria técnica.
     *
     * @param resultados lista de resultados a formatear.
     * @return tabla de resultados en formato texto.
     */
    public String formatearTablaResultados(List<ResultadoComparativa> resultados) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s %-8s %-16s %-16s %-15s %-10s%n",
                "Procesos", "Limite N", "Secuencial (ms)", "Paralelo (ms)", "Diferencia (ms)", "Speedup"));
        sb.append("------------------------------------------------------------------------------\n");
        if (resultados != null) {
            for (ResultadoComparativa r : resultados) {
                sb.append(String.format("%-10d %-8d %-16d %-16d %-15d %-10.2fx%n",
                        r.getCantidadProcesos(),
                        r.getLimitePrimos(),
                        r.getTiempoSecuencialMs(),
                        r.getTiempoParaleloMs(),
                        r.getDiferenciaMs(),
                        r.getFactorAceleracion()));
            }
        }
        return sb.toString();
    }
}