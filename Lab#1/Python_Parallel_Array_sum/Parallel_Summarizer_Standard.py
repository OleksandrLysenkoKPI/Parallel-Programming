import os
import time
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass

BUFFER_SIZE = 128 * 1024
SPACE_BYTE = 32  # ASCII for ' '
ZERO_BYTE = 48  # ASCII for '0'
NINE_BYTE = 57  # ASCII for '9'


@dataclass(slots=True)
class ChunkResult:
    count: int
    sum: int


def process_chunk(
    file_path: str,
    start_offset: int,
    end_offset: int,
    is_first_chunk: bool
) -> ChunkResult:
    with open(file_path, "rb") as f:
        f.seek(start_offset)
        current_pos = start_offset

        if not is_first_chunk:
            while current_pos < end_offset:
                byte_val = f.read(1)
                current_pos += 1
                if not byte_val or byte_val[0] <= SPACE_BYTE:
                    break

        count = 0
        total_sum = 0
        current_number = 0
        in_number = False
        reached_nominal_end = False

        while True:
            data = f.read(BUFFER_SIZE)
            bytes_read = len(data)
            if bytes_read == 0:
                break

            for i in range(bytes_read):
                b = data[i]
                absolute_pos = current_pos + i

                if absolute_pos >= end_offset:
                    reached_nominal_end = True

                if b <= SPACE_BYTE:
                    if in_number:
                        total_sum += current_number
                        count += 1
                        current_number = 0
                        in_number = False

                    if reached_nominal_end:
                        return ChunkResult(count, total_sum)

                elif ZERO_BYTE <= b <= NINE_BYTE:
                    in_number = True
                    current_number = current_number * 10 + (b - ZERO_BYTE)

            current_pos += bytes_read

        if in_number:
            total_sum += current_number
            count += 1

        return ChunkResult(count, total_sum)


def main():
    file_path = "../Parallel_Array_sum/numbers.txt"

    if not os.path.exists(file_path):
        print(f"Error: '{file_path}' not found.")
        return

    start_time = time.perf_counter()
    file_size = os.path.getsize(file_path)

    workers = os.cpu_count() or 4
    chunk_size = file_size // workers

    tasks = []
    for i in range(workers):
        start_offset = i * chunk_size
        end_offset = file_size if (i == workers - 1) else (i + 1) * chunk_size
        is_first = (i == 0)
        tasks.append((file_path, start_offset, end_offset, is_first))

    total_count = 0
    total_sum = 0

    with ProcessPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(process_chunk, *task) for task in tasks]

        for future in futures:
            result = future.result()
            total_count += result.count
            total_sum += result.sum

    elapsed_ms = (time.perf_counter() - start_time) * 1000

    print(f"Total numbers counted: {total_count}")
    print(f"Sum of numbers: {total_sum}")
    print(f"Work time: {elapsed_ms:.2f} ms")


if __name__ == "__main__":
    main()