package com.dataforge.modelo;
/**
 * Representa los posibles estados de ejecución de un proceso del sistema operativo.
 */
public enum EstadoProceso {
    PENDIENTE,    // El proceso todavía no se ha iniciado.
    EJECUTANDO,   // El proceso está en ejecución.
    FINALIZADO,   // El proceso ha terminado correctamente.
    ERROR,        // El proceso ha terminado con un error.
    CANCELADO     // El proceso ha sido cancelado.
}
