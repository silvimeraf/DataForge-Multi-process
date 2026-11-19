package com.dataforge.tareas;
/**
 * Clase encargada de calcular números primos hasta un límite determinado
 * y medir el tiempo empleado en realizar el cálculo.
 * Se ejecuta como un proceso Java independiente, que posteriormente
 * será iniciado y gestionado mediante ProcessBuilder desde DataForge.
 */
public class PrimeBenchmark {
    /**
     * Punto de entrada del programa PrimeBenchmark.
     * Recibe el límite numérico como argumento, valida su valor,
     * ejecuta el cálculo de números primos y muestra los resultados.
     * @param args Argumentos recibidos al iniciar el proceso. args[0] contiene el límite superior del cálculo.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Error: se requiere un límite numérico.");
            System.exit(1);
        }
        // Variable que almacenará el límite superior del cálculo.
        int limite = 0;
        try {
            // Convierte el argumento recibido de String a int.
            limite = Integer.parseInt(args[0]);

            // Comprueba que el límite permita calcular números primos.
            if (limite < 2) {
                System.err.println("Error: el límite debe ser mayor o igual que 2.");
                System.exit(1);
            }
        } catch (NumberFormatException e) {

            // Captura el error cuando el argumento no es un entero válido.
            System.err.println("Error: el límite debe ser un número entero.");
            System.exit(1);
        }
        // Registra el instante en el que comienza el cálculo.
        // nanoTime() permite medir intervalos de tiempo con alta precisión.
        long tiempoInicio = System.nanoTime();
        // Contador de números primos encontrados.
        int cantidadPrimos = 0;
        // Recorre los números desde 2 hasta el límite indicado.
        // Utilizamos long para evitar el desbordamiento de int.
        for (long numero = 2; numero <= limite; numero++) {

            // Comprueba si el número actual es primo.
            if (esPrimo((int) numero)) {

                // Incrementa el contador cuando encuentra un primo.
                cantidadPrimos++;
            }
        }
        // Registra el instante en el que termina el cálculo.
        long tiempoFin = System.nanoTime();
        // Calcula la duración del proceso de cálculo en nanosegundos.
        long duracionNanos = tiempoFin - tiempoInicio;
        // Convierte la duración a milisegundos.
        double duracionMillis = duracionNanos / 1_000_000.0;
        // Muestra el tiempo empleado mediante la salida estándar.
        System.out.printf("Duración del cálculo: %.3f ms%n", duracionMillis);
        // Muestra el resultado del cálculo por la salida estándar.
        System.out.println("Límite analizado: " + limite);
        System.out.println("Cantidad de números primos: " + cantidadPrimos);
    }
    /**
     * Determina si un número es primo.
     * Un número primo es mayor que 1 y solamente es divisible entre 1 y él mismo.
     * @param numero Número que se desea comprobar.
     * @return true si el número es primo; false en caso contrario.
     */
    public static boolean esPrimo(int numero) {
        // Los números menores que 2 no son primos.
        if (numero < 2) {
            return false;
        }
        // Busca posibles divisores desde 2 hasta la raíz cuadrada.
        for (int divisor = 2; divisor <= numero / divisor; divisor++) {

            // Si la división es exacta, el número no es primo.
            if (numero % divisor == 0) {
                return false;
            }
        }
        // Si no se encuentra ningún divisor, el número es primo.
        return true;
    }
}
