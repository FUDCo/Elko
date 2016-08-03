#include "defs.h"
#include "free.h"
#include "read.h"
#include "util.h"
#include "code.h"
#include "refct.h"
#include "write.h"
#include "dump.h"
#include "prune.h"
#include "gc.h"

#define ACT_PRUNE 1
#define ACT_DUMP  2
#define ACT_WRITE 3

  int
main(int argc, char *argv[])
{
    int action = ACT_PRUNE;
    char *filename = null;
    int i;

    for (i = 1; i < argc; ++i) {
        char *arg = argv[i];
        if (strcmp(arg, "-v") == 0) {
            g_verbose = true;
        } else if (strcmp(arg, "-d") == 0) {
            action = ACT_DUMP;
        } else if (strcmp(arg, "-w") == 0) {
            action = ACT_WRITE;
        } else if (strcmp(arg, "-p") == 0) {
            action = ACT_PRUNE;
        } else if (arg[0] == '-') {
            fprintf(stderr, "unknown flag '%s'\n", arg);
            exit(1);
        } else {
            filename = arg;
        }
    }
    if (filename == null) {
        fprintf(stderr, "no class file specified!\n");
        exit(1);
    }

    FILE *fyle = fopen(filename, "r");
    if (fyle) {
        testEndianism();
        ClassFile *classfile = read_ClassFile(fyle);
        fclose(fyle);
        if (action == ACT_DUMP) {
            refct_ClassFile(classfile);
            dump_ClassFile(classfile);
        } else if (action == ACT_PRUNE) {
            prune_ClassFile(classfile);
            bool done = false;
            do {
                refct_ClassFile(classfile);
                done = gc_ClassFile(classfile);
            } while (!done);
        }
        if (classfile->access_flags & (ACC_PUBLIC | ACC_ABSTRACT)) {
            if (action == ACT_WRITE || action == ACT_PRUNE) {
                char nameBuf[1000];
                strcpy(nameBuf, filename);
                strcat(nameBuf, ".alt");
                fyle = fopen(nameBuf, "w");
                write_ClassFile(fyle, classfile);
                fclose(fyle);
            }
        }
    } else {
        fprintf(stderr, "unable to open class file %s", filename);
    }
}
