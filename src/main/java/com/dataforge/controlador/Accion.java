package com.dataforge.controlador;
import com.dataforge.modelo.ConfiguracionProceso;
import com.dataforge.servicios.ServicioEjecucion;
import com.dataforge.vista.Vista;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    /**
     * Coordina la ejecución secuencial de un lote de procesos.
     * Recibe los comandos configurados desde la vista y el número de repeticiones solicitado por el usuario.
     * Comprueba que el lote sea válido y delega la ejecución en ServicioEjecucion.
     * @param comandos lista de comandos de los procesos hijos.
     * @param repeticiones número de veces que se ejecutará el lote.
     */
    public void ejecutarSecuencial(List<List<String>> comandos, int repeticiones) {
        // 1. Comprobar que existen procesos configurados.
        if (comandos == null || comandos.isEmpty()) {
            vista.mostrarError("No hay procesos configurados.");
            return;
        }
        // 2. Comprobar que el número de repeticiones es válido.
        if (repeticiones <= 0) {
            vista.mostrarError("El número de repeticiones debe ser positivo.");
            return;
        }
        // 3. Validar el límite total de ejecuciones.
        long total = (long) comandos.size() * repeticiones;
        if (total > 100) {
            vista.mostrarError("El lote supera el máximo de 100 ejecuciones.");
            return;
        }
        // 4. Crear el servicio encargado de ejecutar los procesos.
        ServicioEjecucion servicio = new ServicioEjecucion();
        // 5. Registrar el inicio de la operación completa.
        long inicioTotal = System.currentTimeMillis();
        // 6. Ejecutar el lote tantas veces como se haya solicitado.
        for (int i = 1; i <= repeticiones; i++) {
            // Respetar una interrupción previa.
            if (Thread.currentThread().isInterrupted()) {
                vista.mostrarError("Ejecución secuencial interrumpida.");
                return;
            }
            vista.mostrarMensaje(
                    "===== REPETICIÓN " + i
                            + " DE " + repeticiones + " ====="
            );
            // El servicio espera a que termine cada proceso antes de iniciar el siguiente.
            servicio.ejecutarSecuencial(comandos);
        }
        // 7. Calcular la duración total de todas las repeticiones.
        long duracionTotal = System.currentTimeMillis() - inicioTotal;
        // 8. Mostrar el resumen de la operación.
        vista.mostrarMensaje("Total de ejecuciones solicitadas: " + total);
        vista.mostrarMensaje("Duración total del lote: " + duracionTotal + " ms");
    }
}


