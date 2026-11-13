#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>

long long local_wave_reduction(int* arr, long long length, int rank, const char* proc_name) {
    long long current_len = length;
    long long wave = 1;

    while (current_len > 1) {
        long long pairs = current_len / 2;

        // Попарне симетричне додавання елементів
        for (long long i = 0; i < pairs; i++) {
            long long symmetric_idx = current_len - 1 - i;
            arr[i] += arr[symmetric_idx];
        }

        // Якщо довжина була непарною, центральний елемент залишається на місці
        current_len = (current_len + 1) / 2;
        wave++;
    }

    return (long long)arr[0];
}

int main(int argc, char** argv) {
    MPI_Init(&argc, &argv);

    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    char processor_name[MPI_MAX_PROCESSOR_NAME];
    int name_len;
    MPI_Get_processor_name(processor_name, &name_len);

    long long total_elements = 1000000000LL;
    if (argc > 1) {
        total_elements = atoll(argv[1]);
    }

    long long chunk_size = total_elements / size;
    long long remainder = total_elements % size;
    if (rank == size - 1) {
        chunk_size += remainder;
    }

    // Бар'єрна синхронізація перед початком
    MPI_Barrier(MPI_COMM_WORLD);
    double start_time = MPI_Wtime();

    // 1. Виділення пам'яті під локальну частину
    int* local_array = (int*)malloc(chunk_size * sizeof(int));
    if (local_array == NULL) {
        fprintf(stderr, "[%s | Rank %d] Error: Out of memory!\n", processor_name, rank);
        MPI_Abort(MPI_COMM_WORLD, 1);
    }

    // Заповнюємо тестовими даними (наприклад, 1)
    for (long long i = 0; i < chunk_size; i++) {
        local_array[i] = 1;
    }

    // 2. Локальне хвильове згортання всередині кожної ноди
    long long my_reduced_val = local_wave_reduction(local_array, chunk_size, rank, processor_name);
    free(local_array);

    printf("[%s | Rank %d] Finished local wave reduction. Intermediate value = %lld\n",
           processor_name, rank, my_reduced_val);

    // 3. МІЖНОДОВЕ ХВИЛЬОВЕ ПОПАРНЕ ДОДАВАННЯ (Point-to-Point MPI_Send / MPI_Recv)
    // Розмір активних нод: спочатку 4 (ноди 0, 1, 2, 3)
    // Хвиля 1: спаровуємо симетричні ноди: 
    //         Нода 0 приймає від Ноди 3 (0 <-> 3)
    //         Нода 1 приймає від Ноди 2 (1 <-> 2)
    // Хвиля 2: спаровуємо активні ноди:
    //         Нода 0 приймає від Ноди 1 (0 <-> 1)
    int active_nodes = size;
    int inter_node_wave = 1;

    while (active_nodes > 1) {
        int pairs = active_nodes / 2;

        for (int i = 0; i < pairs; i++) {
            int left_rank = i;
            int right_rank = active_nodes - 1 - i;

            if (rank == right_rank) {
                // Відпрацьована права половина нод відправляє своє значення симетричній лівій ноді
                printf("  >> [Хвиля %d] Нода %d (%s) ВІДПРАВЛЯЄ %lld -> Ноді %d\n",
                       inter_node_wave, rank, processor_name, my_reduced_val, left_rank);
                MPI_Send(&my_reduced_val, 1, MPI_LONG_LONG, left_rank, 0, MPI_COMM_WORLD);
            } else if (rank == left_rank) {
                // Ліва нода приймає значення і складає зі своїм (перший з останнім)
                long long received_val = 0;
                MPI_Status status;
                MPI_Recv(&received_val, 1, MPI_LONG_LONG, right_rank, 0, MPI_COMM_WORLD, &status);
                
                long long old_val = my_reduced_val;
                my_reduced_val += received_val;

                printf("  << [Хвиля %d] Нода %d (%s) ОТРИМАЛА %lld від Ноди %d. Пару додано: %lld + %lld = %lld\n",
                       inter_node_wave, rank, processor_name, received_val, right_rank,
                       old_val, received_val, my_reduced_val);
            }
        }

        active_nodes = (active_nodes + 1) / 2;
        inter_node_wave++;
        MPI_Barrier(MPI_COMM_WORLD);
    }

    double end_time = MPI_Wtime();
    double total_time = end_time - start_time;

    // Фінальний вивід
    if (rank == 0) {
        printf("\n=======================================================\n");
        printf("                    РЕЗУЛЬТАТИ ЛАБ #2                  \n");
        printf("         (Попарне хвильове міжнодове згортання)        \n");
        printf("=======================================================\n");
        printf("Загальна кількість елементів: %lld\n", total_elements);
        printf("Кількість нод у кластері   : %d\n", size);
        printf("Обчислена фінальна сума    : %lld\n", my_reduced_val);
        printf("Очікувана контрольна сума : %lld\n", total_elements);
        printf("Загальний час виконання    : %.6f секунд\n", total_time);
        if (my_reduced_val == total_elements) {
            printf("СТАТУС: УСПІШНО! (Хвильова редукція зійшлася)\n");
        } else {
            printf("СТАТУС: ПОМИЛКА!\n");
        }
        printf("=======================================================\n");
    }

    MPI_Finalize();
    return 0;
}
