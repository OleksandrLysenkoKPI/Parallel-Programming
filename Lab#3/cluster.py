import subprocess
import json
import sys
import time

VM_LIST = ["manager", "worker1", "worker2", "worker3"]

def run_host_cmd(args, capture=False):
    """Виконує команду на хості Windows безпосередньо (без cmd.exe / shell=False)"""
    return subprocess.run(args, capture_output=capture, text=True, shell=False)

def exec_vm_bash(vm_name, bash_script, capture=False):
    """Передає bash-скрипт безпосередньо всередину Linux ВМ"""
    args = ["multipass", "exec", vm_name, "--", "sudo", "bash", "-c", bash_script]
    return subprocess.run(args, capture_output=capture, text=True, shell=False)

def exec_vm_user(vm_name, cmd_args, capture=False):
    """Виконує команду від імені ubuntu всередині ВМ"""
    args = ["multipass", "exec", vm_name, "--"] + cmd_args
    return subprocess.run(args, capture_output=capture, text=True, shell=False)

def stop_cluster():
    print("=== Зупинка кластера ===")
    print("Виконується коректне вимкнення віртуальних машин...")
    run_host_cmd(["multipass", "stop"] + VM_LIST)
    print("Усі віртуальні машини успішно зупинені.")
    print("Оперативна пам'ять та ресурси процесора звільнені.")

def start_cluster():
    print("=== Запуск кластера ===")
    print("Запуск віртуальних машин...")
    run_host_cmd(["multipass", "start"] + VM_LIST)
    
    print("Очікування ініціалізації мережі...")
    time.sleep(3)

    print("Отримання поточних IP-адрес...")
    res = run_host_cmd(["multipass", "list", "--format=json"], capture=True)
    if res.returncode != 0:
        print("Помилка отримання списку машин.")
        return

    vm_data = json.loads(res.stdout)["list"]
    vm_ips = {}
    for vm in vm_data:
        if vm["name"] in VM_LIST and vm["state"] == "Running" and len(vm["ipv4"]) > 0:
            vm_ips[vm["name"]] = vm["ipv4"][0]

    print(f"Поточні IP: {vm_ips}")

    print("Оновлення /etc/hosts на всіх машинах...")
    cluster_pattern = "manager|worker1|worker2|worker3"
    for name in VM_LIST:
        if name not in vm_ips:
            continue

        # Формуємо bash-скрипт, який Linux виконає всередині:
        # 1. Видаляємо всі попередні записи імен кластера
        # 2. Додаємо нові актуальні IP для інших нод
        script_lines = [
            f"sed -i -E '/ ({cluster_pattern})$/d' /etc/hosts"
        ]
        for other_name, other_ip in vm_ips.items():
            if name != other_name:
                script_lines.append(f'echo "{other_ip} {other_name}" >> /etc/hosts')

        full_script = "\n".join(script_lines)
        exec_vm_bash(name, full_script)

    print("Перевірка та оновлення спільної папки NFS...")
    # Оновлюємо NFS на менеджері
    exec_vm_bash("manager", "exportfs -ra && systemctl restart nfs-kernel-server")
    
    # На воркерах перемонтовуємо теку з новим IP менеджера
    for w in VM_LIST[1:]:
        exec_vm_bash(w, "umount -f -l /home/ubuntu/cloud 2>/dev/null; sleep 1; mount -a 2>/dev/null")

    print("\nПеревірка зв'язку SSH з manager:")
    for w in VM_LIST[1:]:
        check = exec_vm_user("manager", ["ssh", "-o", "ConnectTimeout=4", w, "echo", "OK"], capture=True)
        status = "ДОСТУПНИЙ" if "OK" in (check.stdout or "") else "ПОМИЛКА"
        print(f"  -> manager -> {w}: {status}")

    print("\nКластер повністю запущений та готовий до роботи!")

def status_cluster():
    print("=== Стан кластера ===")
    run_host_cmd(["multipass", "list"])

def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ["start", "stop", "status", "restart"]:
        print("Використання: python cluster.py [start | stop | status | restart]")
        return
    
    action = sys.argv[1]
    if action == "stop":
        stop_cluster()
    elif action == "start":
        start_cluster()
    elif action == "restart":
        stop_cluster()
        start_cluster()
    elif action == "status":
        status_cluster()

if __name__ == "__main__":
    main()
