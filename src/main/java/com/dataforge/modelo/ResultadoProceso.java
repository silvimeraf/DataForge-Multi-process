package com.dataforge.modelo;
/**
 * Representa el resultado de la ejecución de un proceso externo en DataForge.
 * Almacena la información generada por el proceso:
 * - Salida estándar (stdout).
 * - Salida de errores (stderr).
 * - Código de salida (exit code).
 * Esta clase pertenece a la capa Modelo.
 * No inicia procesos ni realiza operaciones de entrada o salida.
 * La obtención de estos datos corresponde a la capa Servicios.
 */
public class ResultadoProceso {
    /**
     * Contenido generado por el proceso a través de su salida estándar (stdout).
     */
    private String salidaEstandar;
    /**
     * Contenido generado por el proceso a través de su salida de errores (stderr).
     */
    private String salidaErrores;
    /**
     * Código devuelto por el proceso al finalizar.
     * Habitualmente:
     * 0 = finalización correcta.
     * Distinto de 0 = error o condición especial.
     * Su significado concreto depende del programa ejecutado.
     */
    private int codigoSalida;
    /**
     * Constructor del resultado de un proceso.
     * @param salidaEstandar contenido de stdout.
     * @param salidaErrores contenido de stderr.
     * @param codigoSalida código de finalización.
     */
    public ResultadoProceso(String salidaEstandar, String salidaErrores, int codigoSalida) {
        this.salidaEstandar = salidaEstandar;
        this.salidaErrores = salidaErrores;
        this.codigoSalida = codigoSalida;
    }
    /**
     * Obtiene la salida estándar del proceso.
     * @return contenido de stdout.
     */
    public String getSalidaEstandar() {
        return salidaEstandar;
    }
    /**
     * Obtiene la salida de errores del proceso.
     * @return contenido de stderr.
     */
    public String getSalidaErrores() {
        return salidaErrores;
    }
    /**
     * Obtiene el código de salida del proceso.
     * @return código de finalización.
     */
    public int getCodigoSalida() {
        return codigoSalida;
    }
}

