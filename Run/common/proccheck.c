#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>

  void
check(int pid)
{
    int rc = kill(pid, 0);
    if (rc == 0) {
        printf("%d running\n", pid);
    } else if (errno == EINVAL) {
        //printf("%d inval\n", pid);
    } else if (errno == EPERM) {
        printf("%d noperm\n", pid);
    } else if (errno == ESRCH) {
        //printf("%d noproc\n", pid);
    } else {
        printf("%d error %d\n", pid, errno);
    }
}

  int
probe(int pid)
{
    int rc = kill(pid, 0);
    return rc == 0 || errno == EPERM;
}

  int
main(int argc, char *argv[])
{
    if (argc != 2) {
        fprintf(stderr, "usage: proccheck PID\n");
        exit(1);
    }

    int pid = atoi(argv[1]);

    if (pid == 0) {
        for (pid = 1; pid < 65536; ++pid) {
            check(pid);
        }
    } else {
        printf("%s\n", probe(pid) ? "proc" : "noproc");
    }
}
