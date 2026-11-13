import subprocess
import argparse
import os

def compile_file(file_path):
    if not os.path.exists(file_path):
        print(f"Error: File '{file_path}' not found.")
        return
    full_file_name = os.path.basename(file_path)
    file_name = os.path.splitext(full_file_name)[0]
    
    print(f"Transferring {file_path} to manager:cloud...")
    subprocess.run(f"multipass transfer \"{file_path}\" manager:cloud/{full_file_name}", shell=True)
    print(f"File {full_file_name} transferred to manager.")

    print(f"Compiling /home/ubuntu/cloud/{full_file_name} on manager...")
    compile_cmd = f"multipass exec manager -- mpicc -O2 /home/ubuntu/cloud/{full_file_name} -o /home/ubuntu/cloud/{file_name}"
    res = subprocess.run(compile_cmd, shell=True)
    if res.returncode == 0:
        print("File compiled successfully.")
    else:
        print("Compilation failed.")

def run_mpi_program(file_name, num_threads, prog_args=""):
    check_cmd = f"multipass exec manager -- test -f /home/ubuntu/cloud/{file_name}"
    if subprocess.run(check_cmd, shell=True).returncode != 0:
        print(f"Error: Compiled file '{file_name}' not found on manager machine.")
        return

    mpi_command = "mpiexec -hostfile /home/ubuntu/cloud/mpi_hosts"
    if num_threads != -1:
        mpi_command += f" -n {num_threads}"
    
    full_exec = f"{mpi_command} /home/ubuntu/cloud/{file_name} {prog_args}"
    print(f"Executing: {full_exec}")
    subprocess.run(f"multipass exec manager -- {full_exec}", shell=True)

def main():
    parser = argparse.ArgumentParser(description='Run MPI cluster')
    parser.add_argument('--c', '-c', metavar='path_to_file.c', type=str, help='Path to the C file to compile')
    parser.add_argument('--r', '-r', metavar='file_name', type=str, help='Name of the compiled file to run')
    parser.add_argument('--n', '-n', type=int, default=-1, help='Number of processes to run the MPI program with')
    parser.add_argument('--args', '-a', type=str, default="", help='Arguments to pass to the running MPI binary (e.g. array size)')
    args = parser.parse_args()

    if args.c:
        compile_file(args.c)
    elif args.r:
        run_mpi_program(args.r, args.n, args.args)
    else:
        print("Please provide either -c (compile) or -r (run) option. Run with -h for help.")

if __name__ == "__main__":
    main()
