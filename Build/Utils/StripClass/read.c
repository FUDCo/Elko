#include "defs.h"
#include "read.h"
#include "util.h"

  ClassFile *
read_ClassFile(FILE *fyle)
{
    ClassFile *cf = TYPE_ALLOC(ClassFile);
    cf->magic = read_u4(fyle);
    cf->minor_version = read_u2(fyle);
    cf->major_version = read_u2(fyle);
    cf->constant_pool_count = read_u2(fyle);
    cf->constant_pool = read_constant_pool(fyle, cf->constant_pool_count);
    cf->access_flags = read_u2(fyle);
    cf->this_class = read_u2(fyle);
    cf->super_class = read_u2(fyle);
    cf->interfaces_count = read_u2(fyle);
    cf->interfaces = read_u2_array(fyle, cf->interfaces_count);
    cf->fields_count = read_u2(fyle);
    cf->fields = read_fields(fyle, cf, cf->fields_count);
    cf->methods_count = read_u2(fyle);
    cf->methods = read_methods(fyle, cf, cf->methods_count);
    cf->attributes_count = read_u2(fyle);
    cf->attributes = read_attributes(fyle, cf, cf->attributes_count);
    return cf;
}

  cp_info **
read_constant_pool(FILE *fyle, int count)
{
    cp_info **result = TYPE_ALLOC_MULTI(cp_info *, count);
    int i;
    result[0] = null;
    for (i = 1; i < count; ++i) {
        result[i] = read_cp_info(fyle);
        if (result[i]->tag == CONSTANT_Long ||
                result[i]->tag == CONSTANT_Double) {
            result[++i] = null;
        }
    }
    return result;
}

  cp_info *
read_cp_info(FILE *fyle)
{
    u1 tag = read_u1(fyle);
    switch (tag) {
        case CONSTANT_Class: {
            constant_class_info *result = TYPE_ALLOC(constant_class_info);
            result->tag = CONSTANT_Class;
            result->refCount = 0;
            result->name_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Fieldref: {
            constant_fieldref_info *result =
                TYPE_ALLOC(constant_fieldref_info);
            result->tag = CONSTANT_Fieldref;
            result->refCount = 0;
            result->class_index = read_u2(fyle);
            result->name_and_type_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Methodref: {
            constant_methodref_info *result =
                TYPE_ALLOC(constant_methodref_info);
            result->tag = CONSTANT_Methodref;
            result->refCount = 0;
            result->class_index = read_u2(fyle);
            result->name_and_type_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_InterfaceMethodref: {
            constant_interfaceMethodref_info *result =
                TYPE_ALLOC(constant_interfaceMethodref_info);
            result->tag = CONSTANT_InterfaceMethodref;
            result->refCount = 0;
            result->class_index = read_u2(fyle);
            result->name_and_type_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_String: {
            constant_string *result = TYPE_ALLOC(constant_string);
            result->tag = CONSTANT_String;
            result->refCount = 0;
            result->string_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Integer: {
            constant_integer *result = TYPE_ALLOC(constant_integer);
            result->tag = CONSTANT_Integer;
            result->refCount = 0;
            result->bytes = read_u4(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Float: {
            constant_float *result = TYPE_ALLOC(constant_float);
            result->tag = CONSTANT_Float;
            result->refCount = 0;
            result->bytes = read_u4(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Long: {
            constant_long *result = TYPE_ALLOC(constant_long);
            result->tag = CONSTANT_Long;
            result->refCount = 0;
            result->high_bytes = read_u4(fyle);
            result->low_bytes = read_u4(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Double: {
            constant_double *result = TYPE_ALLOC(constant_double);
            result->tag = CONSTANT_Double;
            result->refCount = 0;
            result->high_bytes = read_u4(fyle);
            result->low_bytes = read_u4(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_NameAndType: {
            constant_nameAndType_info *result =
                TYPE_ALLOC(constant_nameAndType_info);
            result->tag = CONSTANT_NameAndType;
            result->refCount = 0;
            result->name_index = read_u2(fyle);
            result->descriptor_index = read_u2(fyle);
            return (cp_info *) result;
        }
        case CONSTANT_Utf8: {
            constant_utf8_info *result = TYPE_ALLOC(constant_utf8_info);
            result->tag = CONSTANT_Utf8;
            result->refCount = 0;
            result->length = read_u2(fyle);
            result->bytes = read_u1_array(fyle, result->length);
            return (cp_info *) result;
        }
        default:
            fprintf(stderr, "invalid constant pool tag %d", tag);
    }
    return null;
}

  field_info *
read_field_info(FILE *fyle, ClassFile *cf)
{
    field_info *result = TYPE_ALLOC(field_info);
    result->access_flags = read_u2(fyle);
    result->name_index = read_u2(fyle);
    result->descriptor_index = read_u2(fyle);
    result->attributes_count = read_u2(fyle);
    result->attributes = read_attributes(fyle, cf, result->attributes_count);
    return result;
}

  field_info **
read_fields(FILE *fyle, ClassFile *cf, int count)
{
    field_info **result = TYPE_ALLOC_MULTI(field_info *, count);
    int i;
    for (i = 0; i < count; ++i) {
        result[i] = read_field_info(fyle, cf);
    }
    return result;
}

  method_info *
read_method_info(FILE *fyle, ClassFile *cf)
{
    method_info *result = TYPE_ALLOC(method_info);
    result->access_flags = read_u2(fyle);
    result->name_index = read_u2(fyle);
    result->descriptor_index = read_u2(fyle);
    result->attributes_count = read_u2(fyle);
    result->attributes = read_attributes(fyle, cf, result->attributes_count);
    return result;
}

  method_info **
read_methods(FILE *fyle, ClassFile *cf, int count)
{
    method_info **result = TYPE_ALLOC_MULTI(method_info *, count);
    int i;
    for (i = 0; i < count; ++i) {
        result[i] = read_method_info(fyle, cf);
    }
    return result;
}

  attribute_info **
read_attributes(FILE *fyle, ClassFile *cf, int count)
{
    attribute_info **result = TYPE_ALLOC_MULTI(attribute_info *, count);
    int i;
    for (i = 0; i < count; ++i) {
        result[i] = read_attribute_info(fyle, cf);
    }
    return result;
}

  attribute_info *
read_attribute_info(FILE *fyle, ClassFile *cf)
{
    attribute_info *result = TYPE_ALLOC(attribute_info);
    result->attribute_name_index = read_u2(fyle);
    result->attribute_length = read_u4(fyle);
    result->info = read_u1_array(fyle, result->attribute_length);
    result->value = decode_attribute_value(cf, result);
    return result;
}

  u1
read_u1(FILE *fyle)
{
    u1 result;
    fread((char *) &result, 1, 1, fyle);
    return result;
}

  u1 *
read_u1_array(FILE *fyle, int length)
{
    u1 *result = TYPE_ALLOC_MULTI(u1, length);
    fread((char *) result, 1, length, fyle);
    return result;
}

  u2
read_u2(FILE *fyle)
{
    u2 result;
    fread((char *) &result, 2, 1, fyle);
    if (g_littleEndian) {
        reverseBytes((char *) &result, 2);
    }
    return result;
}

  u2 *
read_u2_array(FILE *fyle, int length)
{
    u2 *result = TYPE_ALLOC_MULTI(u2, length);
    fread((char *) result, 2, length, fyle);
    if (g_littleEndian) {
        int i;
        for (i = 0; i < length; ++i) {
            reverseBytes((char *) &result[i], 2);
        }
    }
    return result;
}

  u4
read_u4(FILE *fyle)
{
    u4 result;
    fread((char *) &result, 4, 1, fyle);
    if (g_littleEndian) {
        reverseBytes((char *) &result, 4);
    }
    return result;
}

  attribute_value *
decode_attribute_value(ClassFile *cf, attribute_info *att)
{
    char *name = pUtf8(cf, att->attribute_name_index);
    u1 *scanptr = att->info;
    if (strcmp(name, "ConstantValue") == 0) {
        attribute_value_ConstantValue *result =
            TYPE_ALLOC(attribute_value_ConstantValue);
        result->attribute_type = ATT_CONSTANT_VALUE;
        result->constantvalue_index = scan_u2(&scanptr);
        return (attribute_value *) result;
    } else if (strcmp(name, "Code") == 0) {
        attribute_value_Code *result = TYPE_ALLOC(attribute_value_Code);
        result->attribute_type = ATT_CODE;
        result->max_stack = scan_u2(&scanptr);
        result->max_locals = scan_u2(&scanptr);
        result->code_length = scan_u4(&scanptr);
        result->code = scan_u1_array(&scanptr, result->code_length);
        result->exception_table_length = scan_u2(&scanptr);
        result->exception_table =
            TYPE_ALLOC_MULTI(exception_table_entry,
                             result->exception_table_length);
        int i;
        for (i = 0; i < result->exception_table_length; ++i) {
            result->exception_table[i].start_pc = scan_u2(&scanptr);
            result->exception_table[i].end_pc = scan_u2(&scanptr);
            result->exception_table[i].handler_pc = scan_u2(&scanptr);
            result->exception_table[i].catch_type = scan_u2(&scanptr);
        }
        result->attributes_count = scan_u2(&scanptr);
        result->attributes =
            scan_attributes(&scanptr, cf, result->attributes_count);
        return (attribute_value *) result;
    } else if (strcmp(name, "Exceptions") == 0) {
        attribute_value_Exceptions *result =
            TYPE_ALLOC(attribute_value_Exceptions);
        result->attribute_type = ATT_EXCEPTIONS;
        result->number_of_exceptions = scan_u2(&scanptr);
        result->exception_index_table =
            scan_u2_array(&scanptr, result->number_of_exceptions);
        return (attribute_value *) result;
    } else if (strcmp(name, "InnerClasses") == 0) {
        attribute_value_InnerClasses *result =
            TYPE_ALLOC(attribute_value_InnerClasses);
        result->attribute_type = ATT_INNER_CLASSES;
        result->number_of_classes = scan_u2(&scanptr);
        result->classes =
            TYPE_ALLOC_MULTI(innerclasses_table_entry,
                             result->number_of_classes);
        int i;
        for (i = 0; i < result->number_of_classes; ++i) {
            result->classes[i].inner_class_info_index = scan_u2(&scanptr);
            result->classes[i].outer_class_info_index = scan_u2(&scanptr);
            result->classes[i].inner_name_index = scan_u2(&scanptr);
            result->classes[i].inner_class_access_flags = scan_u2(&scanptr);
        }
        return (attribute_value *) result;
    } else if (strcmp(name, "Synthetic") == 0) {
        attribute_value_Synthetic *result =
            TYPE_ALLOC(attribute_value_Synthetic);
        result->attribute_type = ATT_SYNTHETIC;
        return (attribute_value *) result;
    } else if (strcmp(name, "SourceFile") == 0) {
        attribute_value_SourceFile *result =
            TYPE_ALLOC(attribute_value_SourceFile);
        result->attribute_type = ATT_SOURCE_FILE;
        result->sourcefile_index = scan_u2(&scanptr);
        return (attribute_value *) result;
    } else if (strcmp(name, "LineNumberTable") == 0) {
        attribute_value_LineNumberTable *result =
            TYPE_ALLOC(attribute_value_LineNumberTable);
        result->attribute_type = ATT_LINE_NUMBER_TABLE;
        result->line_number_table_length = scan_u2(&scanptr);
        result->line_number_table =
            TYPE_ALLOC_MULTI(line_number_table_entry,
                             result->line_number_table_length);
        int i;
        for (i = 0; i < result->line_number_table_length; ++i) {
            result->line_number_table[i].start_pc = scan_u2(&scanptr);
            result->line_number_table[i].line_number = scan_u2(&scanptr);
        }
        return (attribute_value *) result;
    } else if (strcmp(name, "LocalVariableTable") == 0) {
        attribute_value_LocalVariableTable *result =
            TYPE_ALLOC(attribute_value_LocalVariableTable);
        result->attribute_type = ATT_LOCAL_VARIABLE_TABLE;
        result->local_variable_table_length = scan_u2(&scanptr);
        result->local_variable_table =
            TYPE_ALLOC_MULTI(local_variable_table_entry,
                             result->local_variable_table_length);
        int i;
        for (i = 0; i < result->local_variable_table_length; ++i) {
            result->local_variable_table[i].start_pc = scan_u2(&scanptr);
            result->local_variable_table[i].length = scan_u2(&scanptr);
            result->local_variable_table[i].name_index = scan_u2(&scanptr);
            result->local_variable_table[i].descriptor_index = scan_u2(&scanptr);
            result->local_variable_table[i].index = scan_u2(&scanptr);
        }
        return (attribute_value *) result;
    } else if (strcmp(name, "Deprecated") == 0) {
        attribute_value_Deprecated *result =
            TYPE_ALLOC(attribute_value_Deprecated);
        result->attribute_type = ATT_DEPRECATED;
        return (attribute_value *) result;
    } else if (strcmp(name, "RuntimeVisibleAnnotations") == 0) {
        attribute_value_RuntimeVisibleAnnotations *result =
            TYPE_ALLOC(attribute_value_RuntimeVisibleAnnotations);
        result->attribute_type = ATT_RUNTIME_VISIBLE_ANNOTATIONS;
        result->num_annotations = scan_u2(&scanptr);
        result->annotations =
            scan_annotations(&scanptr, result->num_annotations);
        return (attribute_value *) result;
    } else if (strcmp(name, "EnclosingMethod") == 0) {
        attribute_value_EnclosingMethod *result =
            TYPE_ALLOC(attribute_value_EnclosingMethod);
        result->attribute_type = ATT_ENCLOSING_METHOD;
        result->class_index = scan_u2(&scanptr);
        result->method_index = scan_u2(&scanptr);
        return (attribute_value *) result;
    } else if (strcmp(name, "StackMapTable") == 0) {
        attribute_value_StackMapTable *result =
            TYPE_ALLOC(attribute_value_StackMapTable);
        result->attribute_type = ATT_STACK_MAP_TABLE;
        return (attribute_value *) result;
    } else if (strcmp(name, "Signature") == 0) {
        attribute_value_Signature *result =
            TYPE_ALLOC(attribute_value_Signature);
        result->attribute_type = ATT_SIGNATURE;
        result->signature_index = scan_u2(&scanptr);
        return (attribute_value *) result;
    } else if (strcmp(name, "LocalVariableTypeTable") == 0) {
        attribute_value_LocalVariableTypeTable *result =
            TYPE_ALLOC(attribute_value_LocalVariableTypeTable);
        result->attribute_type = ATT_LOCAL_VARIABLE_TYPE_TABLE;
        result->local_variable_type_table_length = scan_u2(&scanptr);
        result->local_variable_type_table =
            TYPE_ALLOC_MULTI(local_variable_type_table_entry,
                             result->local_variable_type_table_length);
        int i;
        for (i = 0; i < result->local_variable_type_table_length; ++i) {
            result->local_variable_type_table[i].start_pc = scan_u2(&scanptr);
            result->local_variable_type_table[i].length = scan_u2(&scanptr);
            result->local_variable_type_table[i].name_index =
                scan_u2(&scanptr);
            result->local_variable_type_table[i].signature_index =
                scan_u2(&scanptr);
            result->local_variable_type_table[i].index = scan_u2(&scanptr);
        }
        return (attribute_value *) result;
    } else if (strcmp(name, "AnnotationDefault") == 0) {
        attribute_value_AnnotationDefault *result =
            TYPE_ALLOC(attribute_value_AnnotationDefault);
        result->attribute_type = ATT_ANNOTATION_DEFAULT;
        result->default_value =
            scan_element_value(&scanptr);
        return (attribute_value *) result;
    } else {
        attribute_value *result = TYPE_ALLOC(attribute_value);
        result->attribute_type = ATT_UNKNOWN;
        return result;
    }
}

  attribute_info *
scan_attribute_info(u1 **scanptr, ClassFile *cf)
{
    attribute_info *result = TYPE_ALLOC(attribute_info);
    result->attribute_name_index = scan_u2(scanptr);
    result->attribute_length = scan_u4(scanptr);
    result->info = scan_u1_array(scanptr, result->attribute_length);
    result->value = decode_attribute_value(cf, result);
    return result;
}

  element_value *
scan_element_value(u1 **scanptr)
{
    u1 tag = scan_u1(scanptr);
    switch (tag) {
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'S':
        case 's':
        case 'Z': {
            element_value_const *result = TYPE_ALLOC(element_value_const);
            result->tag = tag;
            result->const_value_index = scan_u2(scanptr);
            return (element_value *) result;
        }
        case 'e': {
            element_value_enum_const *result =
                TYPE_ALLOC(element_value_enum_const);
            result->tag = tag;
            result->type_name_index = scan_u2(scanptr);
            result->const_name_index = scan_u2(scanptr);
            return (element_value *) result;
        }
        case 'c': {
            element_value_class_info *result =
                TYPE_ALLOC(element_value_class_info);
            result->tag = tag;
            result->class_info_index = scan_u2(scanptr);
            return (element_value *) result;
        }
        case '@': {
            element_value_annotation *result =
                TYPE_ALLOC(element_value_annotation);
            result->tag = tag;
            result->annotation_value = scan_annotation(scanptr);
            return (element_value *) result;
        }
        case '[': {
            element_value_array *result = TYPE_ALLOC(element_value_array);
            result->tag = tag;
            result->num_values = scan_u2(scanptr);
            result->values =
                TYPE_ALLOC_MULTI(element_value *, result->num_values);
            int i;
            for (i = 0; i < result->num_values; ++i) {
                result->values[i] = scan_element_value(scanptr);
            }
            return (element_value *) result;
        }
    }
}

  annotation *
scan_annotation(u1 **scanptr)
{
    annotation *result = TYPE_ALLOC(annotation);
    result->type_index = scan_u2(scanptr);
    result->num_element_value_pairs = scan_u2(scanptr);
    result->element_value_pairs =
        TYPE_ALLOC_MULTI(element_value_pair, result->num_element_value_pairs);
    int i;
    for (i = 0; i < result->num_element_value_pairs; ++i) {
        result->element_value_pairs[i].element_name_index = scan_u2(scanptr);
        result->element_value_pairs[i].value = scan_element_value(scanptr);
    }
    return result;
}

  annotation **
scan_annotations(u1 **scanptr, int count)
{
    annotation **result = TYPE_ALLOC_MULTI(annotation *, count);
    int i;
    for (i = 0; i < count; ++i) {
        result[i] = scan_annotation(scanptr);
    }
    return result;
}

  attribute_info **
scan_attributes(u1 **scanptr, ClassFile *cf, int count)
{
    attribute_info **result = TYPE_ALLOC_MULTI(attribute_info *, count);
    int i;
    for (i = 0; i < count; ++i) {
        result[i] = scan_attribute_info(scanptr, cf);
    }
    return result;
}

  void
scanBytes(u1 **scanptr, u1 *result, int length)
{
    int i;
    for (i = 0; i < length; ++i) {
        *result++ = **scanptr;
        *scanptr += 1;
    }
}

  u1
scan_u1(u1 **scanptr)
{
    u1 result;
    scanBytes(scanptr, &result, 1);
    return result;
}

  u1 *
scan_u1_array(u1 **scanptr, int length)
{
    u1 *result = TYPE_ALLOC_MULTI(u1, length);
    scanBytes(scanptr, result, length);
    return result;
}

  u2
scan_u2(u1 **scanptr)
{
    u2 result;
    scanBytes(scanptr, (u1 *) &result, 2);
    if (g_littleEndian) {
        reverseBytes((char *) &result, 2);
    }
    return result;
}

  u2 *
scan_u2_array(u1 **scanptr, int length)
{
    u2 *result = TYPE_ALLOC_MULTI(u2, length);
    scanBytes(scanptr, (u1 *) result, 2 * length);
    if (g_littleEndian) {
        int i;
        for (i = 0; i < length; ++i) {
            reverseBytes((char *) &result[i], 2);
        }
    }
    return result;
}

  u4
scan_u4(u1 **scanptr)
{
    u4 result;
    scanBytes(scanptr, (u1 *) &result, 4);
    if (g_littleEndian) {
        reverseBytes((char *) &result, 4);
    }
    return result;
}
