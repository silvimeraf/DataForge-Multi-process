package com.dataforge.modelo;
/**
 * Enumeración que identifica los modos de ejecución disponibles en la aplicación DataForge.
 * Permite determinar cómo debe gestionar ServicioEjecucion los procesos del sistema operativo.
 * Esta enumeración pertenece a la capa Modelo.
 * No inicia procesos ni contiene lógica de ejecución.
 */
public enum ModoEjecucion {
    /**
     * EJECUCIÓN INDIVIDUAL
     * Ejecuta un único proceso independiente.
     * Ejemplo -> Ejecutar TextAnalyzer para analizar un fichero.
     */
    INDIVIDUAL,
    /**
     * EJECUCIÓN SECUENCIAL
     * Ejecuta varios procesos consecutivamente.
     * Cada proceso debe finalizar antes de iniciar el siguiente.
     * Ejemplo:
     * Proceso 1 -> Finaliza
     * Proceso 2 -> Finaliza
     * Proceso 3 -> Finaliza
     */
    SECUENCIAL,
    /**
     * EJECUCIÓN PARALELA
     * Inicia varios procesos independientes sin esperar a que termine cada uno antes de lanzar el siguiente.
     * El paralelismo se consigue mediante procesos del sistema operativo, utilizando ProcessBuilder.
     * No requiere crear hilos Java.
     * Ejemplo:
     * Proceso 1 -> Ejecutándose
     * Proceso 2 -> Ejecutándose
     * Proceso 3 -> Ejecutándose
     */
    PARALELO
}
