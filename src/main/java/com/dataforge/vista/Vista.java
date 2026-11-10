package com.dataforge.vista;
import com.dataforge.controlador.Accion;

import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
/**
 * Vista no ejecuta procesos ni contiene la lógica de negocio. Su responsabilidad es la interacción con el usuario.
 */
public class Vista {
    Scanner sc = new Scanner(System.in);
    public void iniciar() {
        int opcion;
        do {
            System.out.println("===== DATAFORGE =====");
            System.out.println("Seleccione una tarea:");
            System.out.println("1. TEXT_ANALYZER");
            System.out.println("2. PRIME_BENCHMARK");
            System.out.println("3. PIPELINE");
            System.out.println("4. EJECUCIÓN SECUENCIAL");
            System.out.println("5. SALIR");
            System.out.print("Selecciona una opción: ");
            try {
                opcion = sc.nextInt();
                // Consumir el salto de línea pendiente tras leer el entero.
                sc.nextLine();
                switch (opcion) {
                    case 1 -> textAnalizer();
                    case 2 -> primeBenchmark();
                    case 3 -> pipeLine();
                    case 4 -> configurarEjecucionSecuencial();
                    case 5 -> salir();
                    default -> mostrarError("Debes introducir una opción válida.");
                }
            } catch (InputMismatchException e) {
                System.out.print("Debes introducir un valor de la lista" + e.getMessage());
                sc.nextLine();
                opcion = 0;
            }
        } while (opcion != 5);
    }
    /**
     * Muestra el submenú de TextAnalyzer.
     * Permite analizar un fichero, introducir texto por teclado o regresar al menú principal.
     * El regreso se realiza finalizando el bucle, sin llamar nuevamente al métod o iniciar().
     */
    public void textAnalizer() {
        int opcion;
        do {
            System.out.println("===== TEXT_ANALYZER =====");
            System.out.println("Seleccione una tarea:");
            System.out.println("1. ANALIZAR UN FICHERO DE TEXTO");
            System.out.println("2. ANALIZAR TEXTO INTRODUCIDO POR TECLADO");
            System.out.println("3. VOLVER AL MENÚ PRINCIPAL");
            System.out.print("Selecciona una opción: ");
            try {
                opcion = sc.nextInt();
                sc.nextLine(); // Consumir el salto de línea pendiente.
                switch (opcion) {
                    case 1 -> analizarFichero();
                    case 2 -> analizarTexto();
                    // No llamamos a iniciar(). El bucle terminará porque opcion == 3.
                    case 3 -> System.out.println(
                            "Volviendo al menú principal..."
                    );
                    default -> System.out.println(
                            "Debes introducir una opción válida."
                    );
                }
            } catch (InputMismatchException e) {
                System.err.println(
                        "Debes introducir un número válido."
                );
                sc.nextLine(); // Descartar la entrada incorrecta.
                opcion = 0;
            }
        } while (opcion != 3);
    }
    /**
     * Solicita al usuario la ruta de un fichero de texto y la palabra que desea buscar.
     * Recoge los datos introducidos por teclado y los envía al controlador
     * Accion, que se encargará de validar la información y coordinar la ejecución del análisis.
     * La Vista no realiza el análisis ni accede directamente al fichero.
     */
    public void analizarFichero() {
        System.out.print("Introduce la ruta del fichero: ");
        String ruta = sc.nextLine();
        System.out.print("Introduce la palabra a buscar: ");
        String palabra = sc.nextLine();
        Accion accion = new Accion(this);
        accion.analizarFichero(ruta, palabra);
    }

    /**
     * Solicita al usuario un texto de varias líneas y una palabra de búsqueda.
     * La introducción del texto finaliza cuando el usuario escribe FIN en una línea independiente.
     * Envía los datos al controlador para que sean procesados mediante la
     * entrada estándar (stdin) de un proceso hijo.
     */
    public void analizarTexto() {
        // Informar al usuario de cómo finalizar la entrada.
        System.out.println("Introduce el texto:");
        System.out.println("Escribe FIN en una línea independiente para terminar.");
        // Acumular las distintas líneas introducidas.
        StringBuilder contenido = new StringBuilder();
        // Leer la primera línea.
        String linea = sc.nextLine();
        // Continuar mientras no se introduzca FIN.
        while (!linea.equals("FIN")) {
            // Añadir la línea al contenido.
            if (contenido.length() > 0) {
                contenido.append(System.lineSeparator());
            }
            contenido.append(linea);
            // Leer la siguiente línea.
            linea = sc.nextLine();
        }
        // Convertir el contenido acumulado en String.
        String texto = contenido.toString();
        // Solicitar la palabra que se desea buscar.
        System.out.print("Introduce la palabra a buscar: ");
        String palabra = sc.nextLine();
        // Crear el controlador.
        Accion accion = new Accion(this);
        // Enviar el texto completo y la palabra al controlador.
        accion.analizarTexto(texto, palabra);
    }
    /**
     * Solicita al usuario el límite superior para calcular números primos.
     * La Vista se encarga exclusivamente de recoger el dato y enviarlo al controlador Accion.
     * No valida las reglas de negocio ni ejecuta procesos.
     */
    public void primeBenchmark() {
        // Limpia el salto de línea pendiente de la lectura del menú.
        sc.nextLine();
        // Solicita al usuario el límite del cálculo.
        System.out.print("Introduce el límite para calcular números primos: ");
        // Recoge la entrada como texto para que el controlador la valide.
        String limite = sc.nextLine();
        // Crea el controlador y le proporciona la referencia a la Vista.
        Accion accion = new Accion(this);
        // Envía el dato al controlador para gestionar la solicitud.
        accion.ejecutarPrimeBenchmark(limite);
    }
        /**
     * Recoge desde consola la configuración de un lote secuencial.
     * Permite configurar las repeticiones, el número de procesosy los parámetros específicos de cada tarea.
     * En esta fase no ejecuta procesos: únicamente construye la configuración que posteriormente recibirá Accion.
     */
    public void configurarEjecucionSecuencial() {

        System.out.println("===== EJECUCIÓN SECUENCIAL =====");
        // 1. Solicitar el número de repeticiones.
        int repeticiones = leerEnteroPositivo("Número de repeticiones: ");

        // 2. Solicitar el número de procesos por repetición.
        int numeroProcesos;

        do {
            numeroProcesos = leerEnteroPositivo("Número de procesos por repetición (1-100): ");
            if (numeroProcesos > 100) {
                mostrarError("El máximo permitido es 100 procesos.");
            }

        } while (numeroProcesos > 100);
        // 3. Comprobar que el total no supera 100 ejecuciones.
        long total = (long) repeticiones * numeroProcesos;
        if (total > 100) {mostrarError("El lote supera el máximo de 100 ejecuciones.");
            return;
        }
        // 4. Crear la lista de comandos del lote.
        List<List<String>> comandos = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        // 5. Solicitar la configuración de cada proceso.
        for (int i = 1; i <= numeroProcesos; i++) {
            System.out.println("===== PROCESO " + i + " =====");
            System.out.println("1. PRIME_BENCHMARK");
            System.out.println("2. TEXT_ANALYZER (FICHERO)");
            int tipoTarea;
            do {
                tipoTarea = leerEnteroPositivo("Selecciona la tarea (1-2): ");
                if (tipoTarea > 2) {
                    mostrarError("Selecciona una tarea válida.");
                }
            } while (tipoTarea > 2);
            if (tipoTarea == 1) {
                int limite = leerEnteroPositivo(
                        "Introduce el límite de números primos: "
                );
                comandos.add(List.of(
                        "java",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        classpath,
                        "com.dataforge.tareas.PrimeBenchmark",
                        String.valueOf(limite)
                ));
            } else {
                System.out.print("Ruta del fichero: ");
                String ruta = sc.nextLine().trim();

                System.out.print("Palabra que deseas buscar: ");
                String palabra = sc.nextLine().trim();

                comandos.add(List.of(
                        "java",
                        "-Dfile.encoding=UTF-8",
                        "-cp",
                        classpath,
                        "com.dataforge.tareas.TextAnalyzer",
                        ruta,
                        palabra
                ));
            }
        }
        // 6. Mostrar el resumen del lote configurado.
        System.out.println("===== RESUMEN DEL LOTE =====");
        System.out.println("Repeticiones: " + repeticiones);
        System.out.println("Procesos por repetición: " + numeroProcesos);
        System.out.println("Total de ejecuciones: " + total);
        System.out.println("Comandos configurados: " + comandos.size());
        // 7. Crear el controlador y delegar la ejecución del lote.
        Accion accion = new Accion(this);
        accion.ejecutarSecuencial(comandos, repeticiones);
    }


    /**
     * Solicita un número entero positivo desde consola.
     * Repite la solicitud mientras el usuario introduzca un valor no numérico, cero o negativo.
     * @param mensaje texto que se muestra al usuario.
     * @return número entero positivo introducido.
     */
    private int leerEnteroPositivo(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String entrada = sc.nextLine().trim();
            try {
                int numero = Integer.parseInt(entrada);
                if (numero > 0) {
                    return numero;
                }
                mostrarError("Debes introducir un número mayor que cero.");
            } catch (NumberFormatException e) {
                mostrarError("Debes introducir un número entero válido.");
            }
        }
    }


    public void pipeLine() {
    }
    public void salir() {
        System.out.println("Saliendo de la app");
    }
    public void mostrarMensaje(String mensaje) {
        System.out.println(mensaje);
    }
    public void mostrarError(String mensaje) {
        System.err.println("ERROR: " + mensaje);
    }
}
