import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ParallelWaveSum {
    private static final int SIZE = 1_000_000_000;
    private static final int WORKER_COUNT = Runtime.getRuntime().availableProcessors();

    private static final int[] array = new int[SIZE];
    private static volatile int currentLength = SIZE;
    private static volatile boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Логічних ядер процесора: " + WORKER_COUNT);
        System.out.println("Ініціалізація масиву...");

        long initStart = System.currentTimeMillis();
        Arrays.fill(array, 1);
        System.out.println("Масив заповнено за " + (System.currentTimeMillis() - initStart) + " мс");

        // Бар'єр синхронізує WORKER_COUNT робочих потоків + 1 керуючий потік main
        CyclicBarrier barrier = new CyclicBarrier(WORKER_COUNT + 1);

        // Створення та запуск робочих потоків
        Thread[] workers = new Thread[WORKER_COUNT];
        for (int i = 0; i < WORKER_COUNT; i++) {
            final int workerId = i;
            workers[i] = new Thread(new Worker(workerId, barrier), "Worker-" + workerId);
            workers[i].start();
        }

        long start = System.currentTimeMillis();
        int waveCount = 0;

        // Керуючий потік (Main Thread) контролює перехід між хвилями
        while (currentLength > 1) {
            waveCount++;
            int totalPairs = currentLength / 2;
            long waveStart = System.nanoTime();

            // 1. Очікуємо, поки всі робочі потоки завершать поточну хвилю
            try {
                barrier.await();
            } catch (BrokenBarrierException e) {
                break;
            }

            long waveDurationNanos = System.nanoTime() - waveStart;

            // Логування перших кількох і останніх хвиль для аналізу
            if (waveCount <= 5 || totalPairs <= 128) {
                System.out.printf(
                        "Wave %2d: pairs = %-10d | time: %8.2f us (%d threads)%n",
                        waveCount, totalPairs, waveDurationNanos / 1_000.0, Math.min(totalPairs, WORKER_COUNT)
                );
            } else if (waveCount == 6) {
                System.out.println("... [computing intermediate waves] ...");
            }

            // 2. Керуючий потік зменшує довжину актуальної частини масиву
            // (відкидає другу половину масиву)
            currentLength = (currentLength + 1) / 2;

            // 3. Якщо дійшли до 1 елемента — зупиняємо алгоритм
            if (currentLength == 1) {
                running = false;
            }

            // 4. Даємо сигнал робочим потокам починати нову хвилю (або вийти)
            try {
                barrier.await();
            } catch (BrokenBarrierException e) {
                break;
            }
        }

        // Чекаємо коректного завершення всіх потоків
        for (Thread worker : workers) {
            worker.join();
        }

        long end = System.currentTimeMillis();

        System.out.println("Всі хвилі завершено!");
        System.out.println("Кількість хвиль: " + waveCount);
        System.out.println("Сума елементів масиву: " + array[0]);
        System.out.println("Загальний час обчислення: " + (end - start) + " мс");
    }

    static class Worker implements Runnable {
        private final int workerId;
        private final CyclicBarrier barrier;

        Worker(int workerId, CyclicBarrier barrier) {
            this.workerId = workerId;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                while (running) {
                    int len = currentLength;
                    int totalPairs = len / 2;

                    // Керуючий розподіл індексів: кожен потік бере свій діапазон
                    int chunkSize = (totalPairs + WORKER_COUNT - 1) / WORKER_COUNT;
                    int startIdx = workerId * chunkSize;
                    int endIdx = Math.min(startIdx + chunkSize, totalPairs);

                    // Обчислення пар для свого діапазону
                    for (int i = startIdx; i < endIdx; i++) {
                        // Індекс другого елементу симетричний до центру: len - 1 - i
                        int symmetricIdx = len - 1 - i;
                        array[i] += array[symmetricIdx];
                    }

                    // Сигналізуємо керуючому потоку, що свою частину хвилі пораховано
                    barrier.await();

                    // Очікуємо, поки керуючий потік оновить currentLength або зупинить процес
                    barrier.await();
                }
            } catch (InterruptedException | BrokenBarrierException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}