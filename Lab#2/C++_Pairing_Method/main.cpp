#include <iostream>
#include <vector>
#include <thread>
#include <barrier>
#include <chrono>
#include <algorithm>
#include <atomic>
#include <memory>

constexpr size_t ARRAY_SIZE = 1'000'000'000ULL;

int main() {
    // Визначається кількість апаратних потоків
    unsigned int hardware_threads = std::thread::hardware_concurrency();
    size_t num_workers = (hardware_threads > 0) ? hardware_threads : 12;

    std::cout << "Кількість логічних потоків CPU: " << num_workers << "\n";
    std::cout << "Виділення пам'яті для масиву на 1 млрд елементів (int32)...\n";

    auto t_init_start = std::chrono::high_resolution_clock::now();

    // std::make_unique створює масив в динамічній пам'яті 1 раз без ризику переповнення стека
    auto array = std::make_unique<int[]>(ARRAY_SIZE);

    // Заповнення одиницями
    std::fill_n(array.get(), ARRAY_SIZE, 1);

    auto t_init_end = std::chrono::high_resolution_clock::now();
    std::cout << "Масив створено та заповнено за "
              << std::chrono::duration_cast<std::chrono::milliseconds>(t_init_end - t_init_start).count()
              << " мс\n";

    std::cout << "Початок багатопотокового хвильового згортання...\n";
    auto t_calc_start = std::chrono::high_resolution_clock::now();

    // Стан хвильового згортання
    size_t current_len = ARRAY_SIZE;
    int wave_count = 0;
    bool is_running = true;

    // std::barrier на C++20 із функцією completion step.
    std::barrier wave_barrier(num_workers, [&]() noexcept {
        wave_count++;
        current_len = (current_len + 1) / 2;

        if (current_len <= 1) {
            is_running = false;
        }
    });

    // Функція для робочого потоку
    auto worker_task = [&](size_t worker_id) {
        while (true) {
            size_t len = current_len;
            size_t total_pairs = len / 2;

            // Розподіл пар
            size_t chunk_size = (total_pairs + num_workers - 1) / num_workers;
            size_t start_idx = worker_id * chunk_size;
            size_t end_idx = std::min(start_idx + chunk_size, total_pairs);

            // Обчислення діапазону симетричних пар
            for (size_t i = start_idx; i < end_idx; ++i) {
                size_t symmetric_idx = len - 1 - i;
                array[i] += array[symmetric_idx];
            }

            // Бар'єр синхронізації: очікуємо, поки всі потоки завершать поточну хвилю.
            wave_barrier.arrive_and_wait();

            if (!is_running) {
                break;
            }
        }
    };

    // Створення пулу потоків
    std::vector<std::thread> workers;
    workers.reserve(num_workers);

    for (size_t i = 0; i < num_workers; ++i) {
        workers.emplace_back(worker_task, i);
    }

    // Очікування завершення роботи всіх потоків
    for (auto& t : workers) {
        if (t.joinable()) {
            t.join();
        }
    }

    auto t_calc_end = std::chrono::high_resolution_clock::now();

    std::cout << "==========================================\n";
    std::cout << "Кількість хвиль: " << wave_count << "\n";
    std::cout << "Підсумкова сума елементів: " << array[0] << "\n";
    std::cout << "Час обчислення: "
              << std::chrono::duration_cast<std::chrono::milliseconds>(t_calc_end - t_calc_start).count()
              << " мс\n";
    std::cout << "==========================================\n";

    return 0;
}