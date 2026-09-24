# Kotlin версія: розпаралелювання алгоритму обрахунку суми елементів масиву

## Parallel Summarizer Threads
Багатопоточна версія на базі пулу потоків JVM (`Executors`.`newFixedThreadPool`). Використовує `FileChannel` для незалежного паралельного читання блоків файлу за зміщеннями та повертає підсумки через `Future`.

### Результати роботи
1. ![Kotlin threaded results 1](img/Kotlin_threaded_1.png)
2. ![Kotlin threaded results 2](img/Kotlin_threaded_2.png)
3. ![Kotlin threaded results 3](img/Kotlin_threaded_3.png)

## Parallel Summarizer Coroutines
Асинхронна версія на базі Kotlin Coroutines (`Dispatchers.IO` та `async`). Забезпечує неблокуючий розподіл завдань між процесорними ядрами за принципами структурованої конкурентності (structured concurrency).

### Результати роботи
1. ![Kotlin coroutines results 1](img/Kotlin_coroutines_1.png)
2. ![Kotlin coroutines results 2](img/Kotlin_coroutines_2.png)
3. ![Kotlin coroutines results 3](img/Kotlin_coroutines_3.png)