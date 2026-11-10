package com.dataforge.modelo;
/**
 * Representa la configuración necesaria para ejecutar una tarea de análisis de texto en DataForge.
 * Almacena los parámetros que utilizará el servicio de ejecución para iniciar el proceso externo
 * mediante ProcessBuilder.
 * Esta clase pertenece a la capa Modelo del patrón MVC.
 * No ejecuta procesos ni interactúa con el usuario.
 */
public class ConfiguracionProceso {
    //Encapsulación -> los atributos son private y se accede a ellos mediante métodos públicos
    /**
     * Ruta del fichero de texto que se desea analizar.
     */
    private String ruta;
    /**
     * Palabra que se desea buscar dentro del texto.
     */
    private String palabra;
    /**
     * Constructor de la configuración del proceso.
     * Permite crear un objeto con los parámetros necesarios para ejecutar TextAnalyzer.
     * @param ruta ruta del fichero que se analizará.
     * @param palabra palabra que se buscará en el texto.
     */
    public ConfiguracionProceso(String ruta, String palabra) {
        this.ruta = ruta;
        this.palabra = palabra;
    }
    /**
     * Obtiene la ruta del fichero configurado.
     * @return ruta del fichero.
     */
    public String getRuta() {
        return ruta;
    }
    /**
     * Obtiene la palabra que se desea buscar.
     * @return palabra de búsqueda.
     */
    public String getPalabra() {
        return palabra;
    }
    /**
     * Modifica la ruta del fichero configurado.
     * @param ruta nueva ruta del fichero.
     */
    public void setRuta(String ruta) {
        this.ruta = ruta;
    }
    /**
     * Modifica la palabra de búsqueda.
     * @param palabra nueva palabra que se desea buscar.
     */
    public void setPalabra(String palabra) {
        this.palabra = palabra;
    }
}

