

package com.dataforge.tareas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TextAnalyzer {

    public static void main(String[] args) {

        // Comprobar los argumentos recibidos
        if (args.length != 2) {
            System.err.println("ERROR: Se necesita una ruta y una palabra.");
            System.exit(1);
        }

        String ruta = args[0];
        String palabraBuscada = args[1];

        try {
            List<String> lineas = Files.readAllLines(Path.of(ruta));

            int numeroLineas = lineas.size();
            int numeroPalabras = 0;
            int numeroCaracteres = 0;
            int apariciones = 0;
            String palabraMasLarga = "";

            for (String linea : lineas) {

                numeroCaracteres += linea.length();

                String texto = linea.trim();

                if (texto.isEmpty()) {
                    continue;
                }

                String[] palabras = texto.split("\\s+");

                for (String palabra : palabras) {

                    numeroPalabras++;

                    if (palabra.length() > palabraMasLarga.length()) {
                        palabraMasLarga = palabra;
                    }

                    if (palabra.equalsIgnoreCase(palabraBuscada)) {
                        apariciones++;
                    }
                }
            }

            // Resultados por stdout
            System.out.println("===== RESULTADO TEXT ANALYZER =====");
            System.out.println("Líneas: " + numeroLineas);
            System.out.println("Palabras: " + numeroPalabras);
            System.out.println("Caracteres: " + numeroCaracteres);
            System.out.println("Palabra más larga: " + palabraMasLarga);
            System.out.println("Apariciones de '" + palabraBuscada + "': " + apariciones);

        } catch (IOException | SecurityException | IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}


