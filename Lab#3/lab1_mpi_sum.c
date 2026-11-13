#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char** argv) {
    // 1. Ініціалізація середовища MPI
    MPI_Init(&argc, &argv);

    int rank, size;
    // Отримуємо унікальний номер поточної ноди (0, 1, 2 або 3)
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    // Отримуємо загальну кількість нод у кластері (4)
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    // Отримуємо ім'я хоста (машини), на якій виконується цей процес
    char processor_name[MPI_MAX_PROCESSOR_NAME];
    int name_len;
    MPI_Get_processor_name(processor_name, &name_len);

    // Загальна кількість елементів масиву (за замовчуванням 1 000 000 000)
    // Можна передати параметром командного рядка: mpiexec ... ./lab1_mpi_sum 100000000
    long long total_elements = 1000000000LL;
    if (argc > 1) {
        total_elements = atoll(argv[1]);
    }

    // 2. Децентралізований розподіл: кожна нода рахує свій шматочок
    long long chunk_size = total_elements / size;
    long long remainder = total_elements % size;

    // Якщо раптом розмір не ділиться порівну, залишок віддаємо останній ноді
    if (rank == size - 1) {
        chunk_size += remainder;
    }

    // Синхронізуємо всі ноди перед початком вимірювання часу
    MPI_Barrier(MPI_COMM_WORLD);
    double start_time = MPI_Wtime();

    // 3. Виділення пам'яті ТІЛЬКИ під свій локальний шматок
    int* local_array = (int*)malloc(chunk_size * sizeof(int));
    if (local_array == NULL) {
        fprintf(stderr, "[%s | Rank %d] Error: Failed to allocate %lld bytes!\n",
                processor_name, rank, (long long)(chunk_size * sizeof(int)));
        MPI_Abort(MPI_COMM_WORLD, 1);
    }

    // Заповнення локального шматка (заповнюємо 1, щоб перевірити правильність суми)
    // Глобальний індекс кожного елемента: global_idx = rank * (total_elements / size) + i
    for (long long i = 0; i < chunk_size; i++) {
        local_array[i] = 1; 
    }

    // Локальне обчислення суми
    long long local_sum = 0;
    for (long long i = 0; i < chunk_size; i++) {
        local_sum += local_array[i];
    }

    // Звільняємо пам'ять масиву на ноді
    free(local_array);

    printf("[%s | Rank %d] Computed local chunk of %lld elements. Local sum = %lld\n",
           processor_name, rank, chunk_size, local_sum);

    // 4. МІЖНОДОВА ВЗАЄМОДІЯ: MPI_Reduce
    // Збирає 64-бітне число (MPI_LONG_LONG) з кожної ноди, додає їх разом (MPI_SUM) і зберігає результат на ноді 0 (manager).
    long long global_sum = 0;
    MPI_Reduce(&local_sum, &global_sum, 1, MPI_LONG_LONG, MPI_SUM, 0, MPI_COMM_WORLD);

    // Час завершення
    double end_time = MPI_Wtime();
    double total_time = end_time - start_time;

    // 5. Тільки головна нода (manager) виводить фінальний результат
    if (rank == 0) {
        printf("\n=======================================================\n");
        printf("                    РЕЗУЛЬТАТИ ЛАБ #1                  \n");
        printf("=======================================================\n");
        printf("Загальна кількість елементів: %lld\n", total_elements);
        printf("Кількість нод у кластері   : %d\n", size);
        printf("Обчислена глобальна сума   : %lld\n", global_sum);
        printf("Очікувана контрольна сума : %lld\n", total_elements);
        printf("Загальний час виконання    : %.6f секунд\n", total_time);
        if (global_sum == total_elements) {
            printf("СТАТУС: УСПІШНО! (Сума повністю зійшлася)\n");
        } else {
            printf("СТАТУС: ПОМИЛКА (Сума не зійшлася)\n");
        }
        printf("=======================================================\n");
    }

    // Завершення роботи з MPI
    MPI_Finalize();
    return 0;
}
