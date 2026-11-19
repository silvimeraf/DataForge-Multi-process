package com.dataforge.modelo;
/**
 * Representa una ejecución individual de un proceso del sistema operativo dentro de DataForge.
 * Permite almacenar información sobre el proceso: identificador (PID), estado, tiempos de ejecución
 * y código de salida.
 * Esta clase pertenece a la capa Modelo pues no crea, ejecuta ni finaliza procesos.
 * La gestión del ciclo de vida corresponde a la capa Servicios.
 */
public class EjecucionProceso {
    /**
     * Identificador del proceso asignado
     * por el sistema operativo.
     */
    private long pid;
    /**
     * Estado actual de la ejecución.
     * Se utilizará EstadoProceso cuando se implemente como enumeración.
     */
    private EstadoProceso estado;
    /**
     * Momento en que comienza la ejecución, expresado en milisegundos.
     */
    private long tiempoInicio;
    /**
     * Momento en que termina la ejecución, expresado en milisegundos.
     */
    private long tiempoFin;
    /**
     * Código devuelto por el proceso al finalizar.
     * Se utiliza Integer para permitir null mientras el proceso no haya terminado.
     */
    private Integer codigoSalida;
    /**
     * Constructor de una ejecución.
     * @param pid identificador del proceso.
     * @param estado estado inicial de la ejecución.
     * @param tiempoInicio instante de inicio.
     */
    public EjecucionProceso(long pid,EstadoProceso estado,long tiempoInicio) {
        this.pid = pid;
        this.estado = estado;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = 0;
        this.codigoSalida = null;
    }
    /**
     * Obtiene el PID del proceso.
     * @return identificador del proceso.
     */
    public long getPid() {
        return pid;
    }
    /**
     * Obtiene el estado actual.
     * @return estado del proceso.
     */
    public EstadoProceso getEstado() {
        return estado;
    }
    /**
     * Modifica el estado del proceso.
     * @param estado nuevo estado.
     */
    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }
    /**
     * Obtiene el instante de inicio.
     * @return tiempo de inicio en milisegundos.
     */
    public long getTiempoInicio() {
        return tiempoInicio;
    }
    /**
     * Obtiene el instante de finalización.
     * @return tiempo de fin en milisegundos.
     */
    public long getTiempoFin() {
        return tiempoFin;
    }
    /**
     * Registra el instante de finalización.
     * @param tiempoFin instante de finalización.
     */
    public void setTiempoFin(long tiempoFin) {
        this.tiempoFin = tiempoFin;
    }
    /**
     * Obtiene el código de salida.
     * @return código de salida o null
     *         si el proceso no ha terminado.
     */
    public Integer getCodigoSalida() {
        return codigoSalida;
    }
    /**
     * Registra el código de salida.
     * @param codigoSalida código devuelto
     *                     por el proceso.
     */
    public void setCodigoSalida(Integer codigoSalida) {
        this.codigoSalida = codigoSalida;
    }
    /**
     * Calcula la duración de la ejecución.
     * Si el proceso todavía no ha terminado, calcula el tiempo transcurrido hasta ahora.
     * @return duración en milisegundos.
     */
    public long getDuracion() {
        if (tiempoFin == 0) {
            return System.currentTimeMillis() - tiempoInicio;
        }
        return tiempoFin - tiempoInicio;
    }
}

