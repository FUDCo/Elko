#include "defs.h"
#include "free.h"

  void
free_ClassFile(ClassFile *ptr)
{
    int i;

    for (i = 0; i < ptr->constant_pool_count; ++i) {
        free_cp_info(ptr->constant_pool[i]);
    }
    for (i = 0; i < ptr->fields_count; ++i) {
        free_field_info(ptr->fields[i]);
    }
    for (i = 0; i < ptr->methods_count; ++i) {
        free_method_info(ptr->methods[i]);
    }
    for (i = 0; i < ptr->attributes_count; ++i) {
        free_attribute_info(ptr->attributes[i]);
    }
    free(ptr);
}

  void
free_cp_info(cp_info *ptr)
{
    if (ptr == null) {
        return;
    }
    switch (ptr->tag) {
        case CONSTANT_Class:
            free(ptr);
            break;
        case CONSTANT_Fieldref:
            free(ptr);
            break;
        case CONSTANT_Methodref:
            free(ptr);
            break;
        case CONSTANT_InterfaceMethodref:
            free(ptr);
            break;
        case CONSTANT_String:
            free(ptr);
            break;
        case CONSTANT_Integer:
            free(ptr);
            break;
        case CONSTANT_Float:
            free(ptr);
            break;
        case CONSTANT_Long:
            free(ptr);
            break;
        case CONSTANT_Double:
            free(ptr);
            break;
        case CONSTANT_NameAndType:
            free(ptr);
            break;
        case CONSTANT_Utf8: {
            constant_utf8_info *info = (constant_utf8_info *) ptr;
            free(info->bytes);
            free(info);
            break;
        }
        default:
            fprintf(stderr, "invalid constant pool tag %d", ptr->tag);
    }
}

  void
free_field_info(field_info *ptr)
{
    int i;
    for (i = 0; i < ptr->attributes_count; ++i) {
        free_attribute_info(ptr->attributes[i]);
    }
    free(ptr);
}

  void
free_method_info(method_info *ptr)
{
    int i;
    for (i = 0; i < ptr->attributes_count; ++i) {
        free_attribute_info(ptr->attributes[i]);
    }
    free(ptr);
}

  void
free_attribute_info(attribute_info *ptr)
{
    free(ptr->info);
    free(ptr);
}
