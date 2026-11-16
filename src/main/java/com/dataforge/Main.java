package com.dataforge;
import com.dataforge.vista.Vista;
/**
 * Clase principal de la aplicación DataForge.
 * Contiene el punto de entrada del programa y se encarga de iniciar su ejecución.
 * Su responsabilidad consiste en crear una instancia de la Vista y llamar al métod o que inicia la interacción
 * con el usuario.
 * No contiene lógica de negocio ni ejecuta procesos.
 */
public class Main {
    /**
     * Métod o principal de la aplicación.
     * Es el primer métod o que ejecuta la JVM cuando se inicia DataForge.
     * @param args argumentos recibidos desde la línea de comandos. Actualmente no se utilizan.
     */
    public static void main(String[] args) {
        Vista vista = new Vista();
        vista.iniciar();
    }
}
