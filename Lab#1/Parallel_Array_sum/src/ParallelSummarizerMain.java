import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

class ChunkResult {
    final long count;
    final long sum;

    ChunkResult( long count, long sum) {
        this.count = count;
        this.sum = sum;
    }
}

class ChunkTask implements Callable<ChunkResult> {
    private final FileChannel channel;
    private final long startOffset;
    private final long endOffset;
    private final boolean isFirstChunk;

    ChunkTask(FileChannel channel, long startOffset, long endOffset, boolean isFirstChunk) {
        this.channel = channel;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.isFirstChunk = isFirstChunk;
    }

    @Override
    public ChunkResult call() throws Exception {
        long currentPos = startOffset;

        if (!isFirstChunk) {
            ByteBuffer singleByte = ByteBuffer.allocate(1);
            while (currentPos < endOffset) {
                singleByte.clear();
                if(channel.read(singleByte, currentPos++) <= 0) break;
                if (singleByte.get(0) <= ' ') {
                    break;
                }
            }
        }

        ByteBuffer buffer = ByteBuffer.allocate(128 * 1024);
        long count = 0;
        long sum = 0;
        long currentNumber = 0;
        boolean isNumber = false;
        boolean reachedNominalEnd = false;

        while (true) {
            buffer.clear();
            int bytesRead = channel.read(buffer, currentPos);
            if (bytesRead <= 0) break;

            buffer.flip();
            for (int i = 0; i < bytesRead; i++) {
                byte b = buffer.get();
                long absolutePos = currentPos + i;

                if (absolutePos >= endOffset) {
                    reachedNominalEnd = true;
                }

                if (b <= ' ') {
                    if (isNumber) {
                        sum += currentNumber;
                        count++;
                        currentNumber = 0;
                        isNumber = false;
                    }

                    if (reachedNominalEnd) {
                        return new ChunkResult(count, sum);
                    }
                } else if (b >= '0' && b <= '9') {
                    isNumber = true;
                    currentNumber = currentNumber * 10 + (b - '0');
                }
            }

            currentPos += bytesRead;
        }

        if (isNumber) {
            sum += currentNumber;
            count++;
        }

        return new ChunkResult(count, sum);
    }
}

public class ParallelSummarizerMain {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        File file = new File("numbers.txt");
        long fileSize = file.length();
        int threads = Runtime.getRuntime().availableProcessors();

        long chunkSize = fileSize / threads;
        List<Future<ChunkResult>> futures = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel channel = raf.getChannel();
            ExecutorService pool = Executors.newFixedThreadPool(threads); {
                for (int i = 0; i < threads; i++) {
                    long startOffset = i * chunkSize;
                    long endOffset = (i == threads - 1) ? fileSize : (i + 1) * chunkSize;

                    futures.add(pool.submit(new ChunkTask(channel, startOffset, endOffset, i == 0)));
                }

                long totalCount = 0;
                long totalSum = 0;

                for (Future<ChunkResult> future : futures) {
                    ChunkResult res = future.get();
                    totalCount += res.count;
                    totalSum += res.sum;
                }

                System.out.println("Total numbers counted: " + totalCount);
                System.out.println("Sum of numbers: " + totalSum);
                pool.shutdown();
            }

            long end = System.currentTimeMillis();
            System.out.println("Work time: " + (end - start) + " ms");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
