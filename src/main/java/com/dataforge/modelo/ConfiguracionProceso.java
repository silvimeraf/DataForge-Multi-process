package com.dataforge.modelo;

/**
 * Contiene los parámetros, nos permite definir cómo queremos ejecutar un proceso.
 */
public class ConfiguracionProceso {
    private String ruta;
    private String palabra;

    /**
     *
     * @param ruta
     * @param palabra
     */
    public ConfiguracionProceso(String ruta, String palabra) {
        this.ruta = ruta;
        this.palabra = palabra;
    }

    /**
     *
     * @return
     */
    public String getRuta() {
        return ruta;
    }

    public String getPalabra() {
        return palabra;
    }

}
