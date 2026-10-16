import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

const val ARRAY_SIZE = 1_000_000_000

fun main() = runBlocking {
    println("Number of available logical cores: ${Runtime.getRuntime().availableProcessors()}")
    println("Allocating memory for an array of $ARRAY_SIZE elements...")

    val initTime = measureTimeMillis {
        // ініціалізація
    }
    val array = IntArray(ARRAY_SIZE) { 1 }
    println("Array created and populated in $initTime ms")

    println("Starting wave reduction computation (Kotlin Coroutines)...")
    val elapsedTime = measureTimeMillis {
        waveSumParallel(array)
    }

    println("==========================================")
    println("Total sum of elements: ${array[0]}")
    println("Total computation time: $elapsedTime ms")
    println("==========================================")
}

suspend fun waveSumParallel(array: IntArray) {
    val concurrency = Runtime.getRuntime().availableProcessors()
    var currentLength = array.size
    var wave = 1

    while (currentLength > 1) {
        val totalPairs = currentLength / 2
        val activeCoroutines = if (totalPairs < 20_000) 1 else min(totalPairs, concurrency)

        val waveDurationNanos = measureNanoTime {
            // Якщо пар лишилося зовсім мало (наприклад, менше 20_000),
            // створення об'єктів корутин та перемикання контексту будуть повільнішими за саме додавання.
            if (totalPairs < 20_000) {
                for (i in 0 until totalPairs) {
                    val symmetricIdx = currentLength - 1 - i
                    array[i] += array[symmetricIdx]
                }
            } else {
                // Паралельна обробка поточної хвилі через coroutineScope
                coroutineScope {
                    val chunkSize = (totalPairs + concurrency - 1) / concurrency

                    // Розподіляємо обчислення діапазонів індексів між корутинами
                    val tasks = (0 until concurrency).mapNotNull { workerId ->
                        val startIdx = workerId * chunkSize
                        val endIdx = min(startIdx + chunkSize, totalPairs)

                        if (startIdx < endIdx) {
                            // Dispatchers.Default залучає всі фізичні ядра процесора
                            async(Dispatchers.Default) {
                                for (i in startIdx until endIdx) {
                                    val symmetricIdx = currentLength - 1 - i
                                    array[i] += array[symmetricIdx]
                                }
                            }
                        } else null
                    }

                    // Міжхвильовий бар'єр: очікуємо завершення всіх корутин поточної хвилі
                    tasks.awaitAll()
                }
            }
        }

        // Логування хвилі
        if (wave <= 5 || totalPairs <= 128) {
            println(
                "Wave %2d: pairs = %-10d | time: %8.2f us (%d coroutines)"
                    .format(wave, totalPairs, waveDurationNanos / 1_000.0, activeCoroutines)
            )
        } else if (wave == 6) {
            println("... [computing intermediate waves] ...")
        }

        // Зменшуємо актуальну довжину: відкидаємо другу половину
        // Якщо довжина була непарною, серединний елемент автоматично переходить у наступну хвилю
        currentLength = (currentLength + 1) / 2
        wave++
    }
    println("Waves completed: ${wave - 1}")
}