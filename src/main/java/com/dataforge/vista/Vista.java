package com.dataforge.vista;

import com.dataforge.controlador.Accion;

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
            System.out.println("4. SALIR");
            System.out.print("Seleciona una opcion: ");
            try {
                opcion = sc.nextInt();
                switch (opcion) {
                    case 1 -> textAnalizer();
                    case 2 -> primeBenchmark();
                    case 3 -> pipeLine();
                    case 4 -> salir();
                    default -> System.err.print("Debes introducir un valor de la lista");
                }
            } catch (InputMismatchException e) {
                System.out.print("Debes introducir un valor de la lista" + e.getMessage());
                sc.nextLine();
                opcion = 0;
            }
        } while (opcion != 4);
    }

    public void textAnalizer() {
        int opcion;
        do {
            System.out.println("===== TEXT_ANALYZER =====");
            System.out.println("Seleccione una tarea:");
            System.out.println("1. ANALIZAR UN FICHERO DE TEXTO");
            System.out.println("2. ANALIZAR TEXTO INTRODUCIDO POR TECLADO");
            System.out.println("3. VOLVER A MENÚ PRINCIPAL");
            System.out.print("Seleciona una opcion: ");
            try {
                opcion = sc.nextInt();
                sc.nextLine();
                switch (opcion) {
                    case 1 -> analizarFichero();
                    case 2 -> analizarTexto();
                    case 3 -> iniciar();
                    default -> System.out.print("Debes introducir un valor de la lista");
                }
            } catch (InputMismatchException e) {
                System.err.print("Debes introducir un valor de la lista" + e.getMessage());
                sc.nextLine();
                opcion = 0;
            }
        } while (opcion != 3);
    }
    public void analizarFichero() {
        System.out.print("Introduce la ruta del fichero: ");
        String ruta = sc.nextLine();
        System.out.print("Introduce la palabra a buscar: ");
        String palabra = sc.nextLine();
        Accion accion = new Accion(this);
        accion.analizarFichero(ruta, palabra);
    }
    public void analizarTexto() {
        System.out.print("Introduce el texto: ");
        String texto = sc.nextLine();
        System.out.print("Introduce la palabra a buscar: ");
        String palabra = sc.nextLine();
        Accion accion = new Accion(this);
        accion.analizarFichero(texto, palabra);
    }

    public void primeBenchmark() {
    }

    public void pipeLine() {
        System.out.println("===== PIPELINE (PrimeBenchmark -> TextAnalyzer) =====");
        System.out.print("Introduce el límite N para buscar primos (ej. 10000): ");
        int n = sc.nextInt();
        sc.nextLine(); // limpiar el buffer del scanner
        System.out.print("Introduce la palabra a buscar en el flujo: ");
        String palabra = sc.nextLine();

        Accion accion = new Accion(this);
        accion.ejecutarPipeline(n, palabra); // Delega la ejecución en la clase Accion
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
