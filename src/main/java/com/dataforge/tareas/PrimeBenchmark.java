package com.dataforge.tareas;

public class PrimeBenchmark {

    public static void main(String[] args) {
        // Comprobar que se recibe el límite N como argumento
        if (args.length != 1) {
            System.err.println("ERROR: Se requiere el límite N para calcular los primos.");
            System.exit(1);
        }

        int limite = 0; // Inicializada correctamente para evitar el error del compilador
        try {
            limite = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("ERROR: El límite debe ser un número entero.");
            System.exit(1);
        }

        try {
            // Generar números primos hasta el límite y escribirlos por stdout
            for (int i = 2; i <= limite; i++) {
                if (esPrimo(i)) {
                    System.out.println(i);
                }
            }
            System.exit(0);
        } catch (Exception e) {
            // Si la tubería se cierra desde el otro extremo (TextAnalyzer),
            // se considera un cierre normal del pipeline.
            System.exit(0);
        }
    }

    private static boolean esPrimo(int numero) {
        if (numero <= 1) return false;
        for (int i = 2; i <= Math.sqrt(numero); i++) {
            if (numero % i == 0) {
                return false;
            }
        }
        return true;
    }
}