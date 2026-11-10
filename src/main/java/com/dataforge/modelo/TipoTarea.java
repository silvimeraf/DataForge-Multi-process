package com.dataforge.modelo;
/**
 * Enumeración que identifica los tipos de tareas que puede ejecutar la aplicación DataForge.
 * Permite distinguir qué programa debe ejecutarse mediante ProcessBuilder.
 * Esta enumeración pertenece a la capa Modelo.
 * No contiene lógica de ejecución ni interactúa con el usuario.
 */
public enum TipoTarea {
    /**
     * Analiza un texto procedente de un fichero o de la entrada estándar (stdin).
     * Calcula líneas, palabras, caracteres, palabra más larga y apariciones
     * de una palabra determinada.
     */
    TEXT_ANALYZER,
    /**
     * Calcula los números primos hasta un límite numérico N.
     * Permite realizar pruebas de rendimiento mediante la ejecución de procesos.
     */
    PRIME_BENCHMARK
}
