import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

class NumberGenerator implements Runnable {
    private int arrayLength;
    private static final Object fileLock = new Object();

    NumberGenerator(int arrayLength) {
        this.arrayLength = arrayLength;
    }

    @Override
    public void run() {
        System.out.println("WRITING THREAD " + Thread.currentThread().getName() + " starting");
        StringBuilder numbersString = new StringBuilder();

        for (int i = 0; i < arrayLength; i++) {
            int randomNumber = ThreadLocalRandom.current().nextInt(1, 101);
            numbersString.append(randomNumber).append(" ");
        }

        synchronized (fileLock) {
            try(FileWriter fw = new FileWriter("numbers.txt", true)) {
                fw.write(numbersString.toString());
            } catch (IOException e){
                e.printStackTrace();
            }
        }


        System.out.println("WRITING THREAD " + Thread.currentThread().getName() + " exiting");
    }

}

public class GeneratorRunnable {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int threadCount = 10;
        int arrayLength = 1_000_000_000;
        int batchPerThread = arrayLength / threadCount;

        try (ExecutorService threadPool = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                threadPool.submit(new NumberGenerator(batchPerThread));
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("All threads have finished writing to numbers.txt in " + (end - start) + "ms");
    }
}