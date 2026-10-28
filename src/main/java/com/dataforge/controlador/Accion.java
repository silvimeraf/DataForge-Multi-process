package com.dataforge.controlador;

import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.servicios.ServicioEjecucion;
import com.dataforge.vista.Vista;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    public void analizarFicheroConRedireccion(String ruta, String rutaSalidaFichero, boolean descartar) {
        Path archivo = Path.of(ruta);
        if (!Files.exists(archivo)) {
            vista.mostrarError("El fichero no existe.");
            return;
        }

        com.dataforge.servicios.ServicioComunicacion servicioCom = new com.dataforge.servicios.ServicioComunicacion();

        List<String> comando = List.of(
                "java", "-cp", System.getProperty("java.class.path"),
                "com.dataforge.tareas.TextAnalyzer",
                ruta,
                "palabraClave" // o pedirla por parámetro
        );

        java.io.File archivoDestino = (rutaSalidaFichero != null && !rutaSalidaFichero.isBlank())
                ? new java.io.File(rutaSalidaFichero)
                : null;

        servicioCom.ejecutarConRedireccion(comando, archivoDestino, descartar);
    }

    public void ejecutarPipeline(int nPrimos, String palabraBusqueda) {
        vista.mostrarMensaje("Iniciando tubería (Pipeline): PrimeBenchmark -> TextAnalyzer...");

        // Instanciamos el servicio de comunicación que acabamos de crear
        com.dataforge.servicios.ServicioComunicacion servicioCom = new com.dataforge.servicios.ServicioComunicacion();

        // Comando 1: PrimeBenchmark (Genera los números primos)
        List<String> cmdPrime = List.of(
                "java", "-cp", System.getProperty("java.class.path"),
                "com.dataforge.tareas.PrimeBenchmark", String.valueOf(nPrimos)
        );

        // Comando 2: TextAnalyzer (Lee del flujo estándar 'stdin' a través del pipe)
        List<String> cmdAnalyzer = List.of(
                "java", "-cp", System.getProperty("java.class.path"),
                "com.dataforge.tareas.TextAnalyzer", "stdin", palabraBusqueda
        );

        // Ejecutamos la tubería conectando ambos procesos
        servicioCom.ejecutarPipeline(cmdPrime, cmdAnalyzer);
    }

}


