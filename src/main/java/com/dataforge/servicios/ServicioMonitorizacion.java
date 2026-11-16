package com.dataforge.servicios;
import com.dataforge.modelo.EjecucionProceso;
import com.dataforge.modelo.EstadoProceso;
/**
 * Servicio encargado de monitorizar los procesos ejecutados por DataForge.
 * Permite registrar su inicio y, posteriormente, consultar su estado, duración y código de salida.
 * No crea procesos: esa responsabilidad corresponde a ServicioEjecucion.
 */
public class ServicioMonitorizacion {
    /**
     * Registra el inicio de un proceso del sistema operativo.
     * Obtiene el PID del proceso y registra el instante en el que comienza su monitorización.
     * @param proceso proceso hijo previamente iniciado.
     * @return objeto que representa la ejecución registrada.
     */
    public EjecucionProceso registrarInicio(Process proceso) {
        // Obtiene el identificador asignado por el sistema operativo.
        long pid = proceso.pid();
        // Registra el instante de inicio de la monitorización.
        long tiempoInicio = System.currentTimeMillis();
        // Crea el registro del proceso en estado EJECUTANDO.
        EjecucionProceso ejecucion = new EjecucionProceso(
                pid,
                EstadoProceso.EJECUTANDO,
                tiempoInicio
        );
        // Devuelve el registro para su posterior seguimiento.
        return ejecucion;
    }
    /**
     * Registra la finalización de un proceso.
     * Actualiza el tiempo de finalización, el código de salida y el estado de la ejecución.
     * Un código de salida 0 indica una finalización correcta. Cualquier otro código se registra como ERROR.
     * @param ejecucion registro del proceso que ha finalizado.
     * @param codigoSalida código devuelto por el proceso hijo.
     */
    public void registrarFinalizacion(EjecucionProceso ejecucion, int codigoSalida) {
        // Registra el instante en que se confirma la finalización.
        ejecucion.setTiempoFin(System.currentTimeMillis());
        // Almacena el código de salida del proceso.
        ejecucion.setCodigoSalida(codigoSalida);
        // Determina el estado final según el código de salida.
        if (codigoSalida == 0) {
            // El proceso ha terminado correctamente.
            ejecucion.setEstado(EstadoProceso.FINALIZADO);

        } else {
            // El proceso ha terminado con un error.
            ejecucion.setEstado(EstadoProceso.ERROR);
        }
    }
    /**
     * Consulta el estado actual de un proceso hijo.
     * Comprueba si el proceso continúa activo mediante isAlive().
     * Si sigue activo, devuelve EJECUTANDO.
     * Si ha terminado, devuelve el estado registrado en EjecucionProceso, sin modificarlo.
     * @param proceso proceso hijo que se desea consultar.
     * @param ejecucion registro asociado al proceso.
     * @return estado actual de la ejecución.
     */
    public EstadoProceso consultarEstado(Process proceso, EjecucionProceso ejecucion) {
        // Comprueba si el proceso continúa ejecutándose.
        if (proceso.isAlive()) {
            // El proceso sigue activo en el sistema operativo.
            return EstadoProceso.EJECUTANDO;
        }
        // El proceso ya no está activo. Devuelve el estado almacenado en el modelo.
        return ejecucion.getEstado();
    }
}