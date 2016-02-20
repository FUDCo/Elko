#include <sys/types.h>
#include <sys/stat.h>
#include <sys/event.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>
#include <ctype.h>

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
    
    struct kevent events[itemCount];
    t_watchInfo watchInfos[itemCount];
    int timeoutTime = -1;
    itemCount = 0;
    for (int i = 1; i < argc; i++) {
        char *item = argv[i];

        if (strcmp(item, "-t") == 0) {
            timeoutTime = atoi(argv[++i]);
        } else if (isdigit(item[0])) {
            int pid = atoi(item);
            EV_SET(&events[itemCount], pid, EVFILT_PROC,
                   EV_ADD | EV_ENABLE | EV_CLEAR, NOTE_EXIT, 0, NULL);
            ++itemCount;
            
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
            
            int fd = open(item, O_RDONLY);
            if (fd == -1) {
                die("open");
                exit(-1);
            }
            EV_SET(&events[itemCount], fd, EVFILT_VNODE,
                   EV_ADD | EV_ENABLE | EV_CLEAR, NOTE_RENAME | NOTE_WRITE, 0,
                   &watchInfos[itemCount]);
            ++itemCount;
        }
    }
    
    struct timespec timeout;
    memset(&timeout, 0, sizeof(struct timespec));
    timeout.tv_sec = timeoutTime;
    int result = kevent(kqueue(), events, itemCount, events, itemCount,
                        timeoutTime >= 0 ? &timeout : NULL);
    if (result > 0) {
        for (int i = 0; i < result; ++i) {
            t_watchInfo *hit = events[i].udata;
            if (hit == NULL) {
                fprintf(stdout, "proc %d\n", (int) events[i].ident);
            } else if (hit->dir != NULL) {
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
        }
        return 0;
    } else if (result == 0) {
        fprintf(stdout, "timeout\n");
    } else {
        fprintf(stderr, "result: %d\n", result);
        die("kevent");
    }
    return 1;
}
