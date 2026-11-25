package com.dataforge.servicios;

import com.dataforge.modelo.ProcesoInfo;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Servicio encargado de monitorizar los procesos ejecutados por DataForge.
 * Permite registrar su inicio y, posteriormente, consultar su estado, duración y código de salida.
 * No crea procesos: esa responsabilidad corresponde a ServicioEjecucion.
 */
public class ServicioMonitorizacion {

    private final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Registra el inicio de un proceso del sistema operativo.
     * Obtiene el PID del proceso y registra el instante en el que comienza su monitorización.
     */
    public ProcesoInfo registrarInicio(int id, Process proceso, String tarea, String modo,
                                       String parametros, long tiempoInicio) {
        long pid = proceso.pid();
        String horaInicio = LocalTime.ofInstant(Instant.ofEpochMilli(tiempoInicio), ZoneId.systemDefault())
                .format(formatoHora);

        return new ProcesoInfo(
                id,
                pid,
                tarea,
                modo,
                parametros,
                horaInicio,
                "-",
                "-",
                "-",
                "Ejecutando"
        );
    }

    /**
     * Registra la finalización de un proceso.
     * Actualiza el tiempo de finalización, el código de salida y el estado de la ejecución.
     * Un código de salida 0 indica una finalización correcta. Cualquier otro código se registra como ERROR
     * (salvo que haya sido detenido manualmente con destroy()).
     */
    public void registrarFinalizacion(ProcesoInfo info, long tiempoInicio, long tiempoFin,
                                      int codigoSalida, boolean canceladoPorUsuario) {
        long duracion = tiempoFin - tiempoInicio;
        String horaFin = LocalTime.ofInstant(Instant.ofEpochMilli(tiempoFin), ZoneId.systemDefault())
                .format(formatoHora);

        info.setFin(horaFin);
        info.setDuracion(String.valueOf(duracion));
        info.setExitCode(String.valueOf(codigoSalida));

        if (canceladoPorUsuario) {
            info.setEstado("Cancelado (destroy)");
        } else if (codigoSalida == 0) {
            info.setEstado("Finalizado");
        } else {
            info.setEstado("Error");
        }
    }
}