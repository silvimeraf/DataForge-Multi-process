package com.dataforge.controlador;

import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.servicios.ServicioEjecucion;
import com.dataforge.vista.Vista;

import java.nio.file.Files;
import java.nio.file.Path;

public class Accion {

    private Vista vista;

    public Accion(Vista vista) {
        this.vista = vista;
    }

    public void analizarFichero(String ruta, String palabra) {

        // Comprobar que se ha introducido una ruta
        if (ruta == null || ruta.isBlank()) {
            vista.mostrarError("Debes introducir una ruta.");
            return;
        }

        Path archivo = Path.of(ruta);

        // Comprobar que existe
        if (!Files.exists(archivo)) {
            vista.mostrarError("El fichero no existe.");
            return;
        }

        // Comprobar que es un fichero
        if (!Files.isRegularFile(archivo)) {
            vista.mostrarError("La ruta no corresponde a un fichero.");
            return;
        }

        // Comprobar que se puede leer
        if (!Files.isReadable(archivo)) {
            vista.mostrarError("No se puede leer el fichero.");
            return;
        }
        // Validar la palabra que queremos buscar
        if (palabra == null || palabra.isBlank()) {
            vista.mostrarError("Debes introducir una palabra para buscar.");
            return;
        }

        vista.mostrarMensaje("Datos validados correctamente.");
        ConfiguracionProceso configuracion = new ConfiguracionProceso(ruta, palabra);
        ServicioEjecucion servicio = new ServicioEjecucion();
        servicio.iniciar(configuracion);
    }



}


