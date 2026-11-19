package com.dataforge.modelo;

/**
 * Modelo de datos para cada fila de la tabla "Monitor de Procesos" en JavaFX.
 * Representa una ejecución individual de un proceso externo lanzado por DataForge.
 */
public class ProcesoInfo {
    private int id;
    private long pid;
    private String tarea;
    private String modo;
    private String parametros;
    private String inicio;
    private String fin;
    private String duracion;
    private String exitCode;
    private String estado;
    private String salidaStdout;
    private String salidaStderr;

    public ProcesoInfo(int id, long pid, String tarea, String modo, String parametros,
                       String inicio, String fin, String duracion, String exitCode, String estado) {
        this.id = id;
        this.pid = pid;
        this.tarea = tarea;
        this.modo = modo;
        this.parametros = parametros;
        this.inicio = inicio;
        this.fin = fin;
        this.duracion = duracion;
        this.exitCode = exitCode;
        this.estado = estado;
        this.salidaStdout = "";
        this.salidaStderr = "";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }

    public String getTarea() { return tarea; }
    public void setTarea(String tarea) { this.tarea = tarea; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }

    public String getParametros() { return parametros; }
    public void setParametros(String parametros) { this.parametros = parametros; }

    public String getInicio() { return inicio; }
    public void setInicio(String inicio) { this.inicio = inicio; }

    public String getFin() { return fin; }
    public void setFin(String fin) { this.fin = fin; }

    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }

    public String getExitCode() { return exitCode; }
    public void setExitCode(String exitCode) { this.exitCode = exitCode; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getSalidaStdout() { return salidaStdout; }
    public void setSalidaStdout(String salidaStdout) { this.salidaStdout = salidaStdout; }

    public String getSalidaStderr() { return salidaStderr; }
    public void setSalidaStderr(String salidaStderr) { this.salidaStderr = salidaStderr; }
}