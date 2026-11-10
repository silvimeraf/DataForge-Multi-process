package com.dataforge.controlador;
import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.servicios.ServicioEjecucion;
import com.dataforge.vista.Vista;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * Controlador de la aplicación DataForge.
 * Recibe las solicitudes procedentes de la Vista, comprueba los datos introducidos por el usuario
 * y coordina las operaciones con la capa de servicios.
 * Actúa como intermediario entre la Vista y los servicios, siguiendo el patrón de arquitectura MVC.
 */
public class Accion {
    private Vista vista;// Referencia a la Vista para comunicar mensajes y errores.
    /**
     * Constructor del controlador.
     * Recibe la Vista desde la que se realizan las solicitudes y guarda su referencia para comunicarse con ella.
     * @param vista instancia de la Vista asociada al controlador.
     */
    public Accion(Vista vista) {
        this.vista = vista;
    }
    /**
     * Recibe la solicitud de análisis de un fichero de texto.
     * Comprueba que la ruta y la palabra de búsqueda sean válidas antes de delegar la ejecución en la capa de servicios.
     * Si detecta datos incorrectos, comunica el error a la Vista y detiene la operación mediante return.
     * @param ruta ruta del fichero que se desea analizar.
     * @param palabra palabra que se desea buscar en el fichero.
     */
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
    /**
     * Coordina el análisis de un texto introducido por teclado.
     * Comprueba que los datos recibidos sean válidos y delega la ejecución en ServicioEjecucion.
     * @param texto contenido que se desea analizar.
     * @param palabra palabra que se desea buscar.
     */
    public void analizarTexto(String texto, String palabra) {
        if (texto == null || texto.isBlank()) {
            vista.mostrarError("El texto no puede estar vacío.");
            return;
        }
        if (palabra == null || palabra.isBlank()) {
            vista.mostrarError("La palabra de búsqueda no puede estar vacía.");
            return;
        }
        ServicioEjecucion servicio = new ServicioEjecucion();
        servicio.analizarTexto(texto, palabra);
    }
    /**
     * Recibe la solicitud de ejecución de PrimeBenchmark desde la Vista.
     * Comprueba que el límite introducido sea un número entero mayor o igual que 2.
     * Si la validación es correcta, delega la ejecución en la capa de servicios.
     * @param limiteTexto límite numérico introducido por el usuario.
     */
    public void ejecutarPrimeBenchmark(String limiteTexto) {
        // Comprueba que el usuario haya introducido un valor.
        if (limiteTexto == null || limiteTexto.isBlank()) {
            vista.mostrarError("Debes introducir un límite numérico.");
            return;
        }
        // Declara la variable que almacenará el límite validado.
        int limite;
        try {
            // Convierte el texto recibido a un número entero.
            limite = Integer.parseInt(limiteTexto.trim());

        } catch (NumberFormatException e) {
            // Comunica el error y detiene la operación.
            vista.mostrarError("El límite debe ser un número entero válido.");
            return;
        }
        // Comprueba que el límite permita calcular números primos.
        if (limite < 2) {
            vista.mostrarError("El límite debe ser mayor o igual que 2.");
            return;
        }
        // Confirma que los datos han superado las validaciones.
        vista.mostrarMensaje("Datos validados correctamente.");
        // Crea el servicio encargado de gestionar la ejecución.
        ServicioEjecucion servicio = new ServicioEjecucion();
        // Delega la ejecución de PrimeBenchmark en la capa de servicios.
        servicio.ejecutarPrimeBenchmark(limite);
    }
}


