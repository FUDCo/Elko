#include "defs.h"
#include "util.h"

bool g_littleEndian = false;

  void
reverseBytes(char *data, int length)
{
    int i;
    for (i = 0; i < length / 2; ++i) {
        char temp = data[i];
        data[i] = data[length - 1 - i];
        data[length - 1 - i] = temp;
    }
}

  char *
buf()
{
    static char bufs[100][BUFLEN];
    static int bufCtr = 0;
    char *result = bufs[bufCtr];
    bufCtr = (bufCtr + 1) % 100;
    result[0] = '\0';
    return result;
}

  char *
pClassName(ClassFile *cf, int index)
{
    cp_info *cpi = cf->constant_pool[index];
    if (cpi->tag == CONSTANT_Class) {
        constant_class_info *classInfo = (constant_class_info *) cpi;
        return pUtf8(cf, classInfo->name_index);
    } else {
        return "<invalidclassref>";
    }
}

  char *
pClassAccessFlags(int flags)
{
    char *result = buf();
    if (flags & ACC_PUBLIC) {
        spAppend(result, "public");
    }
    if (flags & ACC_PRIVATE) {
        spAppend(result, "private");
    }
    if (flags & ACC_PROTECTED) {
        spAppend(result, "protected");
    }
    if (flags & ACC_STATIC) {
        spAppend(result, "static");
    }
    if (flags & ACC_FINAL) {
        spAppend(result, "final");
    }
    if (flags & ACC_SUPER) {
        spAppend(result, "super");
    }
    if (flags & ACC_INTERFACE) {
        spAppend(result, "interface");
    }
    if (flags & ACC_ABSTRACT) {
        spAppend(result, "abstract");
    }
    return result;
}

  char *
pFieldAccessFlags(int flags)
{
    char *result = buf();
    if (flags & ACC_PUBLIC) {
        spAppend(result, "public");
    }
    if (flags & ACC_PRIVATE) {
        spAppend(result, "private");
    }
    if (flags & ACC_PROTECTED) {
        spAppend(result, "protected");
    }
    if (flags & ACC_STATIC) {
        spAppend(result, "static");
    }
    if (flags & ACC_FINAL) {
        spAppend(result, "final");
    }
    if (flags & ACC_VOLATILE) {
        spAppend(result, "volatile");
    }
    if (flags & ACC_TRANSIENT) {
        spAppend(result, "transient");
    }
    return result;
}

  char *
pMethodAccessFlags(int flags)
{
    char *result = buf();
    if (flags & ACC_PUBLIC) {
        spAppend(result, "public");
    }
    if (flags & ACC_PRIVATE) {
        spAppend(result, "private");
    }
    if (flags & ACC_PROTECTED) {
        spAppend(result, "protected");
    }
    if (flags & ACC_STATIC) {
        spAppend(result, "static");
    }
    if (flags & ACC_FINAL) {
        spAppend(result, "final");
    }
    if (flags & ACC_SYNCHRONIZED) {
        spAppend(result, "synchronized");
    }
    if (flags & ACC_NATIVE) {
        spAppend(result, "native");
    }
    if (flags & ACC_ABSTRACT) {
        spAppend(result, "abstract");
    }
    if (flags & ACC_STRICT) {
        spAppend(result, "strict");
    }
    return result;
}

  char *
pMethodName(ClassFile *cf, int index)
{
    method_info *methodInfo = cf->methods[index];
    cp_info *nameInfo = cf->constant_pool[methodInfo->name_index];
    return pUtf8(cf, methodInfo->name_index);
}

  char *
pNameAndType(ClassFile *cf, int index)
{
    cp_info *cpi = cf->constant_pool[index];
    if (cpi->tag == CONSTANT_NameAndType) {
        char *result = buf();
        constant_nameAndType_info *info = (constant_nameAndType_info *) cpi;
        strcpy(result, pUtf8(cf, info->name_index));
        strcat(result, "->");
        strcat(result, pUtf8(cf, info->descriptor_index));
        return result;
    } else {
        return "<invalidnameandtype>";
    }
}

  char *
pUtf8(ClassFile *cf, int index)
{
    cp_info *cpi = cf->constant_pool[index];
    if (cpi->tag == CONSTANT_Utf8) {
        char *result = buf();
        constant_utf8_info *utf = (constant_utf8_info *) cpi;
        strncpy(result, utf->bytes, utf->length);
        result[utf->length] = '\0';
        return result;
    } else {
        return "<invalidutf8>";
    }
}

  void
spAppend(char *buf, char *str)
{
    if (buf[0] == '\0') {
        strcpy(buf, str);
    } else {
        strcat(buf, " ");
        strcat(buf, str);
    }
}

  void
testEndianism()
{
    union {
        u2 asU2;
        u1 asU1s[2];
    } tester;

    tester.asU2 = 1;
    g_littleEndian = (tester.asU1s[0] == 1);
}
