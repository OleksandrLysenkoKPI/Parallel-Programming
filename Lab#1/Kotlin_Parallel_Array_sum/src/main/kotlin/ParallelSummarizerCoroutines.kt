import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel


fun parseChunk(
    channel: FileChannel,
    startOffset: Long,
    endOffset: Long,
    isFirstChunk: Boolean
): ChunkResult {
    var currentPos = startOffset
    val spaceByte = ' '.code.toByte()
    val zeroByte = '0'.code.toByte()
    val nineByte = '9'.code.toByte()

    if (!isFirstChunk) {
        val singleByte = ByteBuffer.allocate(1)
        while (currentPos < endOffset) {
            singleByte.clear()
            if (channel.read(singleByte, currentPos++) <= 0) break
            if (singleByte.get(0) <= spaceByte) {
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

suspend fun processFileParallel(file: File): ChunkResult = coroutineScope {
    val fileSize = file.length()
    if (fileSize == 0L) return@coroutineScope ChunkResult(0L, 0L)

    val concurrency = Runtime.getRuntime().availableProcessors()
    val chunkSize = fileSize / concurrency
    val dispatcher = Dispatchers.IO.limitedParallelism(concurrency)

    RandomAccessFile(file, "r").use { raf ->
        val channel = raf.channel

        val deferredResults = (0 until concurrency).map { i ->
            val startOffset = i * chunkSize
            val endOffset = if (i == concurrency - 1) fileSize else (i + 1) * chunkSize

            async(dispatcher) {
                parseChunk(
                    channel = channel,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    isFirstChunk = (i == 0)
                )
            }
        }

        val results = deferredResults.awaitAll()
        val totalCount = results.sumOf { it.count }
        val totalSum = results.sumOf { it.sum }

        ChunkResult(totalCount, totalSum)
    }
}

fun main() = runBlocking {
    val file = File("../Parallel_Array_sum/numbers.txt")

    println("Processing file: ${file.name} (${file.length()} bytes)")
    val startTime = System.currentTimeMillis()

    val result = processFileParallel(file)

    val endTime = System.currentTimeMillis()

    println("Total numbers counted: ${result.count}")
    println("Sum of numbers: ${result.sum}")
    println("Work time: ${endTime - startTime} ms")
}