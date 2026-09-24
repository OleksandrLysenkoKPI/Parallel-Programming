import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// This is deliberately written wrong version
class ParallelSummarizer implements Runnable {
    private byte[] buffer;
    private long numberCount = 0;
    private long totalSum = 0;
    private long currentNumber = 0;
    private boolean isNumber = false;

    private static final Object fileLock = new Object();

    ParallelSummarizer(byte[] buffer) {
        this.buffer = buffer;
    }

    @Override
    public void run() {
        System.out.println("SUMMARIZER THREAD " + Thread.currentThread().getName() + " starting");

        synchronized (fileLock) {
            try (FileInputStream fis = new FileInputStream("numbers.txt")) {
                int byteRead;

                while ((byteRead = fis.read(buffer)) != -1) {
                    for (int i = 0; i < byteRead; i++) {
                        byte b = buffer[i];

                        if (b <= ' ') {
                            if (isNumber) {
                                totalSum += currentNumber;
                                numberCount++;
                                currentNumber = 0;
                                isNumber = false;
                            }
                        } else if (b >= '0' && b <= '9') {
                            isNumber = true;
                            currentNumber = currentNumber * 10 + (b - '0');
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Total numbers counted: " + numberCount);
        System.out.println("Sum of numbers: " + totalSum);

        System.out.println("SUMMARIZER THREAD " + Thread.currentThread().getName() + " starting");
    }
}

public class SummarizerRunnable {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        int threadCount = 5;
        int byteArraySize = 128 * 1024;

        try (ExecutorService threadPool = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                threadPool.submit(new ParallelSummarizer(new byte[byteArraySize]));
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("Work time: " + (end - start) + "ms");
    }
}