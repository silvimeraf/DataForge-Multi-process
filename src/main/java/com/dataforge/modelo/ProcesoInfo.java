package com.dataforge.modelo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class ProcesoInfo {
    private final SimpleIntegerProperty id;
    private final SimpleLongProperty pid; // O long según cómo devuelva el PID tu ProcessBuilder
    private final SimpleStringProperty tarea;
    private final SimpleStringProperty estado;
    private final SimpleStringProperty exitCode;

    public ProcesoInfo(int id, long pid, String tarea, String estado, String exitCode) {
        this.id = new SimpleIntegerProperty(id);
        this.pid = new SimpleLongProperty(pid);
        this.tarea = new SimpleStringProperty(tarea);
        this.estado = new SimpleStringProperty(estado);
        this.exitCode = new SimpleStringProperty(exitCode);
    }

    public int getId() { return id.get(); }
    public long getPid() { return pid.get(); }
    public String getTarea() { return tarea.get(); }
    public String getEstado() { return estado.get(); }
    public String getExitCode() { return exitCode.get(); }
}