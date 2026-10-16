import java.util.Arrays;

public class SingleThreadedWaveSum {
    public static void main(String[] args) {
        int size = 1_000_000_000;
        System.out.println("Ініціалізація масиву розміром " + size + "...");

        long initStart = System.currentTimeMillis();
        int[] array = new int[size];
        Arrays.fill(array, 1);
        System.out.println("Масив ініціалізовано за " + (System.currentTimeMillis() - initStart) + " мс");

        long start = System.currentTimeMillis();

        int len = size;
        int wave = 1;

        while (len > 1) {
            int pairs = len / 2;

            long waveStart = System.nanoTime();

            for (int i = 0; i < pairs; i++) {
                int symmetricIndex = len - 1 - i;
                array[i] += array[symmetricIndex];
            }

            long waveDurationNanos = System.nanoTime() - waveStart;

            // Логування перших кількох і останніх хвиль для аналізу
            if (wave <= 5 || pairs <= 128) {
                System.out.printf(
                        "Wave %2d: pairs = %-10d | time: %8.2f us (1 thread)%n",
                        wave, pairs, waveDurationNanos / 1_000.0
                );
            } else if (wave == 6) {
                System.out.println("... [computing intermediate waves] ...");
            }

            len = (len + 1) / 2;
            wave++;
        }

        long end = System.currentTimeMillis();
        System.out.println("Хвиль виконано: " + (wave - 1));
        System.out.println("Сума елементів масиву: " + array[0]);
        System.out.println("Час обчислення (однопоточно): " + (end - start) + " мс");
    }
}