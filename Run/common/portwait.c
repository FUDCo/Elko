#include <sys/types.h>
#include <sys/stat.h>
#include <port.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>

#define TARGET_EVTS NOTE_RENAME|NOTE_WRITE

typedef int bool;
#define true 1
#define false 0
#define null 0

typedef struct {
    int count;
    char **entries;
} t_dirContents;

typedef struct {
    char *path;
    t_dirContents *dir;
} t_watchInfo;

  t_dirContents *
addEntry(t_dirContents *dir, char *entry) {
    int newCount = (dir != NULL ? dir->count : 0) + 1;
    t_dirContents *newDI = malloc(sizeof(t_dirContents));
    newDI->entries = malloc(sizeof(char*) * newCount);
    if (newCount > 1) {
        for (int i = 0; i < newCount - 1; i++) {
            newDI->entries[i] = dir->entries[i];
        }
    }
    newDI->entries[newCount-1] = entry;
    newDI->count = newCount;
    return newDI;
}

  bool
contains(t_dirContents *dir, char *entry) {
    if (dir != NULL) {
        for (int i = 0; i < dir->count; i++) {
            if (strcmp(dir->entries[i], entry) == 0) {
                return true;
            }
        }
    }
    return false;
}

  t_dirContents *
diffs(t_dirContents *di1, t_dirContents *di2) {
    t_dirContents *result = NULL;
    t_dirContents *iter = di1;
    t_dirContents *other = di2;
    
    if (di1 == NULL) {
        return di2;
    }
    if (di2 == NULL) {
        return di1;
    }
    if (di2->count > di1->count) {
        iter = di2;
        other = di1;
    }
    
    for (int i = 0; i < iter->count; i++) {
        if (!contains(other, iter->entries[i])) {
            result = addEntry(result, iter->entries[i]);
        }
    }
    return result;
}

  void
die(char *failedCall)
{
    perror(failedCall);
    exit(-1);
}

  t_dirContents *
parseDir(char *filePath) {
    t_dirContents *dir = NULL;
    DIR *d = opendir(filePath);
    if (d == NULL) {
        die("opendir");
    }
    struct dirent *dp;
    while ((dp = readdir(d)) != NULL) {
        if (dp->d_name[0] == '.') {
            continue;
        }
        dir = addEntry(dir, dp->d_name);
    }
    return dir;
}

  int
main(int argc, char **argv) {
    
    if (argc < 1) {
        printf("usage: %s <item> [<item> ...]\n", argv[0]);
        printf("An <item> can be a file path, a directory path, or a pid.\n");
        return 1;
    }
    
    int itemCount = argc - 1;
    
    struct file_obj fileobjs[itemCount];
    t_watchInfo watchInfos[itemCount];
    int timeoutTime = -1;
    itemCount = 0;

    int port = port_create();

    for (int i = 1; i < argc; i++) {
        char *item = argv[i];

        if (strcmp(item, "-t") == 0) {
            timeoutTime = atoi(argv[++i]);
        } else {
            struct stat sb;
            if (stat(item, &sb) == -1) {
                die("stat");
            }
            if (S_ISDIR(sb.st_mode)) {
                watchInfos[itemCount].dir = parseDir(item);
            } else {
                watchInfos[itemCount].dir = NULL;
            }
            watchInfos[itemCount].path = item;

            fileobjs[i].fo_atime.tv_sec = sb.st_atim.tv_sec;
            fileobjs[i].fo_atime.tv_nsec = sb.st_atim.tv_nsec;
            fileobjs[i].fo_mtime.tv_sec = sb.st_mtim.tv_sec;
            fileobjs[i].fo_mtime.tv_nsec = sb.st_mtim.tv_nsec;
            fileobjs[i].fo_ctime.tv_sec = sb.st_ctim.tv_sec;
            fileobjs[i].fo_ctime.tv_nsec = sb.st_ctim.tv_nsec;
            fileobjs[i].fo_name = item;


            int rc = port_associate(port, PORT_SOURCE_FILE,
                                    (uintptr_t) &fileobjs[i], FILE_MODIFIED,
                                    &watchInfos[itemCount]);
            if (rc != 0) {
                die("port_associate");
                exit(-1);
            }
            ++itemCount;
        }
    }
    
    struct timespec timeout;
    memset(&timeout, 0, sizeof(struct timespec));
    timeout.tv_sec = timeoutTime;

    port_event_t event;
    int result = port_get(port, &event, timeoutTime >= 0 ? &timeout : NULL);

    if (result == 0) {
        t_watchInfo *hit = event.portev_user;
        if (hit->dir != NULL) {
            t_dirContents *dirNow = parseDir(hit->path);
            t_dirContents *dirDiffs = diffs(hit->dir, dirNow);
            if (dirDiffs->count > 0) {
                fprintf(stdout, "%s %s%s%s\n",
                        ((hit->dir != NULL && dirNow != NULL &&
                          hit->dir->count > dirNow->count) ||
                         (hit->dir != NULL && dirNow == NULL)) ? "-" : "+",
                        hit->path,
                        (hit->path[strlen(hit->path)-1] == '/') ? "" : "/",
                        dirDiffs->entries[0]);
            }
        } else {
            fprintf(stdout, "%s\n", hit->path);
        }
        return 0;
    } else if (errno == ETIME) {
        fprintf(stdout, "timeout\n");
        return 0;
    } else {
        fprintf(stderr, "result: %d\n", result);
        die("port_get");
    }
    return 1;
}
