import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class PureCoroutineWaveSum(
    private val array: IntArray
) {
    private val concurrency = Runtime.getRuntime().availableProcessors()

    suspend fun compute() = coroutineScope {
        var currentLength = array.size
        var wave = 1

        while (currentLength > 1) {
            val totalPairs = currentLength / 2

            // Вимірюємо час виконання кожної конкретної хвилі в мікросекундах (мкс)
            val waveDurationNanos = measureNanoTime {
                // Кожна хвиля, навіть коли totalPairs == 1, нарізається на корутини
                val chunkSize = (totalPairs + concurrency - 1) / concurrency

                val tasks = (0 until concurrency).mapNotNull { workerId ->
                    val startIdx = workerId * chunkSize
                    val endIdx = min(startIdx + chunkSize, totalPairs)

                    if (startIdx < endIdx) {
                        async(Dispatchers.Default) {
                            for (i in startIdx until endIdx) {
                                val symmetricIdx = currentLength - 1 - i
                                array[i] += array[symmetricIdx]
                            }
                        }
                    } else null
                }

                // Бар'єр міжхвильової синхронізації
                tasks.awaitAll()
            }

            // Логування перших кількох і останніх хвиль
            if (wave <= 5 || totalPairs <= 128) {
                println(
                    "Wave %2d: pairs = %-10d | time: %8.2f us (%d coroutines)"
                        .format(wave, totalPairs, waveDurationNanos / 1_000.0, min(totalPairs, concurrency))
                )
            } else if (wave == 6) {
                println("... [computing intermediate waves] ...")
            }

            // Перехід до наступної хвилі
            currentLength = (currentLength + 1) / 2
            wave++
        }

        println("Total waves completed: ${wave - 1}")
    }

    companion object {
        private const val ARRAY_SIZE = 1_000_000_000

        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            println("Logical processors: ${Runtime.getRuntime().availableProcessors()}")
            println("Creating array with $ARRAY_SIZE elements...")

            val array = IntArray(ARRAY_SIZE)
            val fillTime = measureTimeMillis {
                array.fill(1)
            }
            println("Array filled with ones in $fillTime ms")

            println("\n--- Starting PureCoroutineWaveSum (without threshold optimization) ---")
            val calculator = PureCoroutineWaveSum(array)

            val totalTime = measureTimeMillis {
                calculator.compute()
            }

            println("==========================================")
            println("Total sum: ${array[0]}")
            println("Total reduction time: $totalTime ms")
            println("==========================================")
        }
    }
}