#include "defs.h"
#include "code.h"
#include "util.h"

  void
prune_ClassFile(ClassFile *cf)
{
    int i;
    int newIdx;

    for (i = 0; i < cf->fields_count; ++i) {
        if (prune_field_info(cf, cf->fields[i])) {
            cf->fields[i] = null;
        }
    }
    newIdx = 0;
    for (i = 0; i < cf->fields_count; ++i) {
        if (cf->fields[i] != null) {
            cf->fields[newIdx++] = cf->fields[i];
        }
    }
    cf->fields_count = newIdx;

    for (i = 0; i < cf->methods_count; ++i) {
        if (prune_method_info(cf, cf->methods[i])) {
            cf->methods[i] = null;
        }
    }
    newIdx = 0;
    for (i = 0; i < cf->methods_count; ++i) {
        if (cf->methods[i] != null) {
            cf->methods[newIdx++] = cf->methods[i];
        }
    }
    cf->methods_count = newIdx;

    for (i = 0; i < cf->attributes_count; ++i) {
        if (prune_attribute_info(cf, cf->attributes[i], null)) {
            cf->attributes[i] = null;
        }
    }
    newIdx = 0;
    for (i = 0; i < cf->attributes_count; ++i) {
        if (cf->attributes[i] != null) {
            cf->attributes[newIdx++] = cf->attributes[i];
        }
    }
    cf->attributes_count = newIdx;
}

  void
prune_code(ClassFile *cf, char returnType, attribute_value_Code *value)
{
    static u1 VoidReturn[1]  = { 0xb1 };       // return
    static u1 ZeroReturn[2]  = { 0x03, 0xac }; // iconst_0, ireturn
    static u1 DZeroReturn[2] = { 0x0e, 0xaf }; // dconst_0, dreturn
    static u1 FZeroReturn[2] = { 0x0b, 0xae }; // fconst_0, freturn
    static u1 LZeroReturn[2] = { 0x09, 0xad }; // lconst_0, lreturn
    static u1 NullReturn[2]  = { 0x01, 0xb0 }; // aconst_null, areturn

    switch (returnType) {
    case 'V': // void
        value->code = VoidReturn;
        value->code_length = 1;
        break;

    case 'B': // byte
    case 'C': // char
    case 'I': // int
    case 'S': // short
    case 'Z': // boolean
        value->code = ZeroReturn;
        value->code_length = 2;
        break;

    case 'D': // double
        value->code = DZeroReturn;
        value->code_length = 2;
        break;

    case 'F': // float
        value->code = FZeroReturn;
        value->code_length = 2;
        break;

    case 'J': // long
        value->code = LZeroReturn;
        value->code_length = 2;
        break;

    case '[': // array
    case 'L': // object
        value->code = NullReturn;
        value->code_length = 2;
        break;
    }
    u1 *codeCopy = TYPE_ALLOC_MULTI(u1, value->code_length);
    memcpy(codeCopy, value->code, value->code_length);
    value->code = codeCopy;
}

  bool
prune_attribute_info(ClassFile *cf, attribute_info *ai, method_info *mi)
{
    int i;
    int newIdx;

    switch (ai->value->attribute_type) {
        case ATT_UNKNOWN:
            return false;
        case ATT_CONSTANT_VALUE:
            return false;
        case ATT_CODE: {
            char *descriptor = pUtf8(cf, mi->descriptor_index);
            char *returnTypeCode = index(descriptor, ')') + 1;
            
            attribute_value_Code *value =
                (attribute_value_Code *) ai->value;
            prune_code(cf, *returnTypeCode, value);
            value->exception_table_length = 0;
            for (i = 0; i < value->attributes_count; ++i) {
                if (prune_attribute_info(cf, value->attributes[i], null)) {
                    value->attributes[i] = null;
                }
            }
            newIdx = 0;
            for (i = 0; i < value->attributes_count; ++i) {
                if (value->attributes[i] != null) {
                    value->attributes[newIdx++] = value->attributes[i];
                }
            }
            value->attributes_count = newIdx;
            return false;
        }
        case ATT_EXCEPTIONS:
            return false;
        case ATT_INNER_CLASSES:
            return false;
        case ATT_SYNTHETIC:
            return false;
        case ATT_SOURCE_FILE:
            return true;
        case ATT_LINE_NUMBER_TABLE:
            return true;
        case ATT_LOCAL_VARIABLE_TABLE:
            return true;
        case ATT_DEPRECATED:
            return false;
        case ATT_RUNTIME_VISIBLE_ANNOTATIONS:
            return false;
        case ATT_ENCLOSING_METHOD:
            return false;
        case ATT_STACK_MAP_TABLE:
            return true;
        case ATT_SIGNATURE:
            return false;
        case ATT_LOCAL_VARIABLE_TYPE_TABLE:
            return true;
        case ATT_ANNOTATION_DEFAULT:
            return false;
    }
}

  bool
prune_field_info(ClassFile *cf, field_info *fi)
{
    if (fi->access_flags & (ACC_PUBLIC | ACC_PROTECTED)) {
        int i;
        for (i = 0; i < fi->attributes_count; ++i) {
            if (prune_attribute_info(cf, fi->attributes[i], null)) {
                fi->attributes[i] = null;
            }
        }
        int newIdx = 0;
        for (i = 0; i < fi->attributes_count; ++i) {
            if (fi->attributes[i] != null) {
                fi->attributes[newIdx++] = fi->attributes[i];
            }
        }
        fi->attributes_count = newIdx;
        return false;
    } else {
        return true;
    }
}

  bool
prune_method_info(ClassFile *cf, method_info *mi)
{
    if (mi->access_flags & (ACC_PUBLIC | ACC_PROTECTED)) {
        int i;
        for (i = 0; i < mi->attributes_count; ++i) {
            if (prune_attribute_info(cf, mi->attributes[i], mi)) {
                mi->attributes[i] = null;
            }
        }
        int newIdx = 0;
        for (i = 0; i < mi->attributes_count; ++i) {
            if (mi->attributes[i] != null) {
                mi->attributes[newIdx++] = mi->attributes[i];
            }
        }
        mi->attributes_count = newIdx;
        return false;
    } else {
        return true;
    }
}
