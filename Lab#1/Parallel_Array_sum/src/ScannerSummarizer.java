import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

public class ScannerSummarizer {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream("numbers.txt")) {
            BufferedInputStream bis = new BufferedInputStream(fis);
            Scanner scanner = new Scanner(bis);

            long numberCount = 0;
            long totalSum = 0;

            while (scanner.hasNext()) {
                String token = scanner.next();

                int number = Integer.parseInt(token);
                totalSum += number;
                numberCount++;

                if (numberCount % 10_000_000 == 0) {
                    System.out.print("\rTokens processed: " + numberCount);
                }
            }

            System.out.println("\rTokens processed: " + numberCount);
            System.out.println("Total tokens: " + numberCount);
            System.out.println("Sum of numbers: " + totalSum);

            long end = System.currentTimeMillis();
            System.out.println("Total work time is: " + (end - start) + " ms");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}