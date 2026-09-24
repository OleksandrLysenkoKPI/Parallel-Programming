import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class ChunkResult(val count: Long, val sum: Long)

class ChunkTask(
    private val channel: FileChannel,
    private val startOffset: Long,
    private val endOffset: Long,
    private val isFirstChunk: Boolean
) : Callable<ChunkResult> {

    override fun call(): ChunkResult {
        var currentPos = startOffset

        if (!isFirstChunk) {
            val singleByte = ByteBuffer.allocate(1)
            while (currentPos < endOffset) {
                singleByte.clear()
                if (channel.read(singleByte, currentPos++) <= 0) break
                if (singleByte.get(0) <= ' '.code.toByte()) {
                    break
                }
            }
        }

        val buffer = ByteBuffer.allocate(128 * 1024)
        var count = 0L
        var sum = 0L
        var currentNumber = 0L
        var isNumber = false
        var reachedNominalEnd = false

        val spaceByte = ' '.code.toByte()
        val zeroByte = '0'.code.toByte()
        val nineByte = '9'.code.toByte()

        while (true) {
            buffer.clear()
            val bytesRead = channel.read(buffer, currentPos)
            if (bytesRead <= 0) break

            buffer.flip()
            for (i in 0 until bytesRead) {
                val b = buffer.get()
                val absolutePos = currentPos + i

                if (absolutePos >= endOffset) {
                    reachedNominalEnd = true
                }

                if (b <= spaceByte) {
                    if (isNumber) {
                        sum += currentNumber
                        count++
                        currentNumber = 0L
                        isNumber = false
                    }

                    if (reachedNominalEnd) {
                        return ChunkResult(count, sum)
                    }
                } else if (b in zeroByte..nineByte) {
                    isNumber = true
                    currentNumber = currentNumber * 10 + (b - zeroByte)
                }
            }

            currentPos += bytesRead
        }

        if (isNumber) {
            sum += currentNumber
            count++
        }

        return ChunkResult(count, sum)
    }
}

fun main() {
    val start = System.currentTimeMillis()

    val file = File("../Parallel_Array_sum/numbers.txt")
    val fileSize = file.length()
    val threads = Runtime.getRuntime().availableProcessors()

    val chunkSize = fileSize / threads
    val futures = mutableListOf<Future<ChunkResult>>()

    RandomAccessFile(file, "r").use { raf ->
        val channel = raf.channel
        val pool = Executors.newFixedThreadPool(threads)

        try {
            for (i in 0 until threads) {
                val startOffset = i * chunkSize
                val endOffset = if (i == threads - 1) fileSize else (i + 1) * chunkSize

                futures += pool.submit(ChunkTask(channel, startOffset, endOffset, i == 0))
            }

            var totalCount = 0L
            var totalSum = 0L

            for (future in futures) {
                val res = future.get()
                totalCount += res.count
                totalSum += res.sum
            }

            println("Total numbers counted: $totalCount")
            println("Sum of numbers: $totalSum")
        } finally {
            pool.shutdown()
        }

        val end = System.currentTimeMillis()
        println("Work time: ${end - start} ms")
    }
}