package com.dataforge.tareas;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
/**
 * Programa independiente encargado de analizar textos.
 * Puede recibir el contenido desde dos fuentes:
 * 1. FICHERO:
 *    Lee un fichero cuya ruta se recibe como argumento.
 * 2. STDIN:
 *    Lee el texto enviado por otro proceso mediante
 *    su entrada estándar (System.in).
 * Calcula:
 * - Número de líneas.
 * - Número de palabras.
 * - Número de caracteres.
 * - Palabra más larga.
 * - Apariciones de una palabra determinada.
 * Los resultados se escriben en stdout.
 * Los errores se escriben en stderr.
 * Esta clase se ejecuta en una JVM independiente,
 * iniciada mediante ProcessBuilder.
 */
public class TextAnalyzer {
    /**
     * Punto de entrada del programa.
     * @param args argumentos recibidos:
     *             args[0] = ruta del fichero o "stdin".
     *             args[1] = palabra que se desea buscar.
     */
    public static void main(String[] args) {
        // 1. Comprobar que se reciben dos argumentos.
        if (args.length != 2) {
            System.err.println(
                    "ERROR: Se necesita una ruta (o stdin) y una palabra."
            );
            System.exit(1);
        }
        // 2. Obtener los parámetros de ejecución.
        String fuente = args[0];
        String palabraBuscada = args[1];
        try {
            // 3. Obtener las líneas según la fuente.
            List<String> lineas;
            if (fuente.equalsIgnoreCase("stdin")) {

                // Leer la entrada estándar del proceso. Se utiliza UTF-8 para interpretar los carácteres,(Quizás habría que cambiarlo).
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                System.in,
                                StandardCharsets.UTF_8
                        )
                );
                // Leer hasta que se cierre stdin (EOF). No cerramos System.in explícitamente.
                lineas = br.lines().toList();
            } else {
                // Leer el contenido completo del fichero.
                lineas = Files.readAllLines(
                        Path.of(fuente),
                        StandardCharsets.UTF_8
                );
            }
            // 4. Inicializar los contadores.
            int numeroLineas = lineas.size();
            int numeroPalabras = 0;
            int numeroCaracteres = 0;
            int apariciones = 0;
            String palabraMasLarga = "";
            // 5. Recorrer las líneas del texto.
            for (String linea : lineas) {
                // Contar los caracteres de cada línea. No se incluyen los saltos de línea.
                numeroCaracteres += linea.length();
                // Eliminar espacios iniciales y finales.
                String texto = linea.trim();
                // Si la línea está vacía, pasar a la siguiente.
                if (texto.isEmpty()) {
                    continue;
                }
                // Separar la línea en palabras.
                String[] palabras = texto.split("\\s+");
                // 6. Analizar cada palabra.
                for (String palabra : palabras) {
                    // Incrementar el contador de palabras.
                    numeroPalabras++;
                    // Comprobar si es la palabra más larga.
                    if (palabra.length() > palabraMasLarga.length()) {
                        palabraMasLarga = palabra;
                    }
                    // Contar las coincidencias exactas, sin distinguir mayúsculas y minúsculas.
                    if (palabra.equalsIgnoreCase(palabraBuscada)) {
                        apariciones++;
                    }
                }
            }
            // 7. Mostrar los resultados por stdout.
            System.out.println(
                    "===== RESULTADO TEXT ANALYZER ====="
            );
            System.out.println("Líneas: " + numeroLineas);
            System.out.println("Palabras: " + numeroPalabras);
            System.out.println("Caracteres: " + numeroCaracteres);
            System.out.println("Palabra más larga: " + palabraMasLarga);

            System.out.println(
                    "Apariciones de '" + palabraBuscada + "': "
                            + apariciones
            );
            // Si termina normalmente, la JVM devuelve el código de salida 0.
        } catch (IOException | SecurityException
                 | IllegalArgumentException e) {
            // 8. Comunicar el error por stderr.
            System.err.println("ERROR: " + e.getMessage());
            // Indicar que el proceso terminó con error.
            System.exit(1);
        }
    }
}



