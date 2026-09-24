import java.io.FileInputStream;
import java.io.IOException;

public class ChunkSummarizer {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        byte[] buffer = new byte[128 * 1024];
        long numberCount = 0;
        long totalSum = 0;
        long currentNumber = 0;
        boolean isNumber = false;

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

            System.out.println("Total numbers counted: " + numberCount);
            System.out.println("Sum of numbers: " + totalSum);

            long end = System.currentTimeMillis();
            System.out.println("Total work time is: " + (end - start) + " ms");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
