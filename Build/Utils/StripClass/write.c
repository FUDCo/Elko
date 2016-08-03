#include "defs.h"
#include "write.h"
#include "util.h"

  void
write_ClassFile(FILE *fyle, ClassFile *cf)
{
    write_u4(fyle, cf->magic);
    write_u2(fyle, cf->minor_version);
    write_u2(fyle, cf->major_version);
    write_u2(fyle, cf->constant_pool_count);
    write_constant_pool(fyle, cf->constant_pool_count, cf->constant_pool);
    write_u2(fyle, cf->access_flags);
    write_u2(fyle, cf->this_class);
    write_u2(fyle, cf->super_class);
    write_u2(fyle, cf->interfaces_count);
    write_u2_array(fyle, cf->interfaces_count, cf->interfaces);
    write_u2(fyle, cf->fields_count);
    write_fields(fyle, cf, cf->fields_count, cf->fields);
    write_u2(fyle, cf->methods_count);
    write_methods(fyle, cf, cf->methods_count, cf->methods);
    write_u2(fyle, cf->attributes_count);
    write_attributes(fyle, cf, cf->attributes_count, cf->attributes);
}

  void
write_constant_pool(FILE *fyle, int count, cp_info **pool)
{
    int i;
    pool[0] = null;
    for (i = 1; i < count; ++i) {
        write_cp_info(fyle, pool[i]);
        if (pool[i]->tag == CONSTANT_Long || pool[i]->tag == CONSTANT_Double) {
            ++i;
        }
    }
}

  void
write_cp_info(FILE *fyle, cp_info *cpi)
{
    write_u1(fyle, cpi->tag);
    switch (cpi->tag) {
        case CONSTANT_Class: {
            constant_class_info *cp = (constant_class_info *) cpi;
            write_u2(fyle, cp->name_index);
            break;
        }
        case CONSTANT_Fieldref: {
            constant_fieldref_info *cp = (constant_fieldref_info *) cpi;
            write_u2(fyle, cp->class_index);
            write_u2(fyle, cp->name_and_type_index);
            break;
        }
        case CONSTANT_Methodref: {
            constant_methodref_info *cp = (constant_methodref_info *) cpi;
            write_u2(fyle, cp->class_index);
            write_u2(fyle, cp->name_and_type_index);
            break;
        }
        case CONSTANT_InterfaceMethodref: {
            constant_interfaceMethodref_info *cp =
                (constant_interfaceMethodref_info *) cpi;
            write_u2(fyle, cp->class_index);
            write_u2(fyle, cp->name_and_type_index);
            break;
        }
        case CONSTANT_String: {
            constant_string *cp = (constant_string *) cpi;
            write_u2(fyle, cp->string_index);
            break;
        }
        case CONSTANT_Integer: {
            constant_integer *cp = (constant_integer *) cpi;
            write_u4(fyle, cp->bytes);
            break;
        }
        case CONSTANT_Float: {
            constant_float *cp = (constant_float *) cpi;
            write_u4(fyle, cp->bytes);
            break;
        }
        case CONSTANT_Long: {
            constant_long *cp = (constant_long *) cpi;
            write_u4(fyle, cp->high_bytes);
            write_u4(fyle, cp->low_bytes);
            break;
        }
        case CONSTANT_Double: {
            constant_double *cp = (constant_double *) cpi;
            write_u4(fyle, cp->high_bytes);
            write_u4(fyle, cp->low_bytes);
            break;
        }
        case CONSTANT_NameAndType: {
            constant_nameAndType_info *cp = (constant_nameAndType_info *) cpi;
            write_u2(fyle, cp->name_index);
            write_u2(fyle, cp->descriptor_index);
            break;
        }
        case CONSTANT_Utf8: {
            constant_utf8_info *cp = (constant_utf8_info *) cpi;
            write_u2(fyle, cp->length);
            write_u1_array(fyle, cp->length, cp->bytes);
            break;
        }
    }
}

  void
write_field_info(FILE *fyle, ClassFile *cf, field_info *fi)
{
    write_u2(fyle, fi->access_flags);
    write_u2(fyle, fi->name_index);
    write_u2(fyle, fi->descriptor_index);
    write_u2(fyle, fi->attributes_count);
    write_attributes(fyle, cf, fi->attributes_count, fi->attributes);
}

  void
write_fields(FILE *fyle, ClassFile *cf, int count, field_info **fields)
{
    int i;
    for (i = 0; i < count; ++i) {
        write_field_info(fyle, cf, fields[i]);
    }
}

  void
write_method_info(FILE *fyle, ClassFile *cf, method_info *mi)
{
    write_u2(fyle, mi->access_flags);
    write_u2(fyle, mi->name_index);
    write_u2(fyle, mi->descriptor_index);
    write_u2(fyle, mi->attributes_count);
    write_attributes(fyle, cf, mi->attributes_count, mi->attributes);
}

  void
write_methods(FILE *fyle, ClassFile *cf, int count, method_info **methods)
{
    int i;
    for (i = 0; i < count; ++i) {
        write_method_info(fyle, cf, methods[i]);
    }
}

  void
write_attribute_info(FILE *fyle, ClassFile *cf, attribute_info *ai)
{
    encode_attribute_value(cf, ai, ai->value);
    write_u2(fyle, ai->attribute_name_index);
    write_u4(fyle, ai->attribute_length);
    write_u1_array(fyle, ai->attribute_length, ai->info);
}

  void
write_attributes(FILE *fyle, ClassFile *cf, int count, attribute_info **atts)
{
    int i;
    for (i = 0; i < count; ++i) {
        write_attribute_info(fyle, cf, atts[i]);
    }
}

  void
write_u1(FILE *fyle, u1 val)
{
    fwrite((char *) &val, 1, 1, fyle);
}

  void
write_u1_array(FILE *fyle, int length, u1 *array)
{
    fwrite((char *) array, 1, length, fyle);
}

  void
write_u2(FILE *fyle, u2 val)
{
    if (g_littleEndian) {
        reverseBytes((char *) &val, 2);
    }
    fwrite((char *) &val, 2, 1, fyle);
}

  void
write_u2_array(FILE *fyle, int length, u2 *array)
{
    if (g_littleEndian) {
        int i;
        for (i = 0; i < length; ++i) {
            reverseBytes((char *) &array[i], 2);
        }
    }
    fwrite((char *) array, 2, length, fyle);
}

  void
write_u4(FILE *fyle, u4 val)
{
    if (g_littleEndian) {
        reverseBytes((char *) &val, 4);
    }
    fwrite((char *) &val, 4, 1, fyle);
}

  void
emit_attribute_info(u1 **emitptr, ClassFile *cf, attribute_info *att)
{
    emit_u2(emitptr, att->attribute_name_index);
    emit_u4(emitptr, att->attribute_length);
    emit_u1_array(emitptr, att->attribute_length, att->info);
    encode_attribute_value(cf, att, att->value);
}

  void
emit_element_value(u1 **emitptr, element_value *elem)
{
    emit_u1(emitptr, elem->tag);
    switch (elem->tag) {
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'S':
        case 's':
        case 'Z': {
            element_value_const *val = (element_value_const *) elem;
            emit_u2(emitptr, val->const_value_index);
            break;
        }
        case 'e': {
            element_value_enum_const *val =
                (element_value_enum_const *) elem;
            emit_u2(emitptr, val->type_name_index);
            emit_u2(emitptr, val->const_name_index);
            break;
        }
        case 'c': {
            element_value_class_info *val =
                (element_value_class_info *) elem;
            emit_u2(emitptr, val->class_info_index);
            break;
        }
        case '@': {
            element_value_annotation *val =
                (element_value_annotation *) elem;
            emit_annotation(emitptr, val->annotation_value);
            break;
        }
        case '[': {
            element_value_array *val = (element_value_array *) elem;
            emit_u2(emitptr, val->num_values);
            int i;
            for (i = 0; i < val->num_values; ++i) {
                emit_element_value(emitptr, val->values[i]);
            }
            break;
        }
    }
}

  void
emit_annotation(u1 **emitptr, annotation *ann)
{
    emit_u2(emitptr, ann->type_index);
    emit_u2(emitptr, ann->num_element_value_pairs);
    int i;
    for (i = 0; i < ann->num_element_value_pairs; ++i) {
        emit_u2(emitptr, ann->element_value_pairs[i].element_name_index);
        emit_element_value(emitptr, ann->element_value_pairs[i].value);
    }
}

  void
emit_annotations(u1 **emitptr, int count, annotation **anns)
{
    int i;
    for (i = 0; i < count; ++i) {
        emit_annotation(emitptr, anns[i]);
    }
}

  void
emit_attributes(u1 **emitptr, ClassFile *cf, int count, attribute_info **atts)
{
    int i;
    for (i = 0; i < count; ++i) {
        emit_attribute_info(emitptr, cf, atts[i]);
    }
}

  void
emitBytes(u1 **emitptr, u1 *val, int length)
{
    int i;
    for (i = 0; i < length; ++i) {
        **emitptr = *val++;
        *emitptr += 1;
    }
}

  void
emit_u1(u1 **emitptr, u1 val)
{
    emitBytes(emitptr, &val, 1);
}

  void
emit_u1_array(u1 **emitptr, int length, u1 *val)
{
    emitBytes(emitptr, val, length);
}

  void
emit_u2(u1 **emitptr, u2 val)
{
    if (g_littleEndian) {
        reverseBytes((char *) &val, 2);
    }
    emitBytes(emitptr, (u1 *) &val, 2);
}

  void
emit_u2_array(u1 **emitptr, int length, u2 *val)
{
    if (g_littleEndian) {
        int i;
        for (i = 0; i < length; ++i) {
            reverseBytes((char *) &val[i], 2);
        }
    }
    emitBytes(emitptr, (u1 *) val, 2 * length);
}

  void
emit_u4(u1 **emitptr, u4 val)
{
    if (g_littleEndian) {
        reverseBytes((char *) &val, 4);
    }
    emitBytes(emitptr, (u1 *) &val, 4);
}

  void
encode_attribute_value(ClassFile *cf, attribute_info *ai, attribute_value *aval)
{
    u1 buf[65000];

    u1 *emitptr = buf;
    switch (aval->attribute_type) {
        case ATT_CONSTANT_VALUE: {
            attribute_value_ConstantValue *val =
                (attribute_value_ConstantValue *) aval;
            emit_u2(&emitptr, val->constantvalue_index);
            break;
        }
        case ATT_CODE: {
            attribute_value_Code *val = (attribute_value_Code *) aval;
            emit_u2(&emitptr, val->max_stack);
            emit_u2(&emitptr, val->max_locals);
            emit_u4(&emitptr, val->code_length);
            emit_u1_array(&emitptr, val->code_length, val->code);
            emit_u2(&emitptr, val->exception_table_length);
            int i;
            for (i = 0; i < val->exception_table_length; ++i) {
                emit_u2(&emitptr, val->exception_table[i].start_pc);
                emit_u2(&emitptr, val->exception_table[i].end_pc);
                emit_u2(&emitptr, val->exception_table[i].handler_pc);
                emit_u2(&emitptr, val->exception_table[i].catch_type);
            }
            emit_u2(&emitptr, val->attributes_count);
            emit_attributes(&emitptr, cf, val->attributes_count,
                            val->attributes);
            break;
        }
        case ATT_EXCEPTIONS: {
            attribute_value_Exceptions *val =
                (attribute_value_Exceptions *) aval;
            emit_u2(&emitptr, val->number_of_exceptions);
            emit_u2_array(&emitptr, val->number_of_exceptions,
                          val->exception_index_table);
            break;
        }
        case ATT_INNER_CLASSES: {
            attribute_value_InnerClasses *val =
                (attribute_value_InnerClasses *) aval;
            emit_u2(&emitptr, val->number_of_classes);
            int i;
            for (i = 0; i < val->number_of_classes; ++i) {
                emit_u2(&emitptr, val->classes[i].inner_class_info_index);
                emit_u2(&emitptr, val->classes[i].outer_class_info_index);
                emit_u2(&emitptr, val->classes[i].inner_name_index);
                emit_u2(&emitptr, val->classes[i].inner_class_access_flags);
            }
            break;
        }
        case ATT_SYNTHETIC:
            break;
        case ATT_SOURCE_FILE: {
            attribute_value_SourceFile *val =
                (attribute_value_SourceFile *) aval;
            emit_u2(&emitptr, val->sourcefile_index);
            break;
        }
        case ATT_LINE_NUMBER_TABLE: {
            attribute_value_LineNumberTable *val =
                (attribute_value_LineNumberTable *) aval;
            emit_u2(&emitptr, val->line_number_table_length);
            int i;
            for (i = 0; i < val->line_number_table_length; ++i) {
                emit_u2(&emitptr, val->line_number_table[i].start_pc);
                emit_u2(&emitptr, val->line_number_table[i].line_number);
            }
            break;
        }
        case ATT_LOCAL_VARIABLE_TABLE: {
            attribute_value_LocalVariableTable *val =
                (attribute_value_LocalVariableTable *) aval;
            emit_u2(&emitptr, val->local_variable_table_length);
            int i;
            for (i = 0; i < val->local_variable_table_length; ++i) {
                emit_u2(&emitptr, val->local_variable_table[i].start_pc);
                emit_u2(&emitptr, val->local_variable_table[i].length);
                emit_u2(&emitptr, val->local_variable_table[i].name_index);
                emit_u2(&emitptr, val->local_variable_table[i].descriptor_index);
                emit_u2(&emitptr, val->local_variable_table[i].index);
            }
            break;
        }
        case ATT_DEPRECATED:
            break;
        case ATT_RUNTIME_VISIBLE_ANNOTATIONS: {
            attribute_value_RuntimeVisibleAnnotations *val =
                (attribute_value_RuntimeVisibleAnnotations *) aval;
            emit_u2(&emitptr, val->num_annotations);
            emit_annotations(&emitptr, val->num_annotations, val->annotations);
            break;
        }
        case ATT_ENCLOSING_METHOD: {
            attribute_value_EnclosingMethod *val =
                (attribute_value_EnclosingMethod *) aval;
            emit_u2(&emitptr, val->class_index);
            emit_u2(&emitptr, val->method_index);
            break;
        }
        case ATT_STACK_MAP_TABLE: {
            emit_u1_array(&emitptr, ai->attribute_length, ai->info);
            break;
        }
        case ATT_SIGNATURE: {
            attribute_value_Signature *val =
                (attribute_value_Signature *) aval;
            emit_u2(&emitptr, val->signature_index);
            break;
        }
        case ATT_LOCAL_VARIABLE_TYPE_TABLE: {
            attribute_value_LocalVariableTypeTable *val =
                (attribute_value_LocalVariableTypeTable *) aval;
            emit_u2(&emitptr, val->local_variable_type_table_length);
            int i;
            for (i = 0; i < val->local_variable_type_table_length; ++i) {
                emit_u2(&emitptr, val->local_variable_type_table[i].start_pc);
                emit_u2(&emitptr, val->local_variable_type_table[i].length);
                emit_u2(&emitptr,
                        val->local_variable_type_table[i].name_index);
                emit_u2(&emitptr,
                        val->local_variable_type_table[i].signature_index);
                emit_u2(&emitptr, val->local_variable_type_table[i].index);
            }
            break;
        }
        case ATT_ANNOTATION_DEFAULT: {
            attribute_value_AnnotationDefault *val =
                (attribute_value_AnnotationDefault *) aval;
            emit_element_value(&emitptr, val->default_value);
            break;
        }
        case ATT_UNKNOWN: {
            emit_u1_array(&emitptr, ai->attribute_length, ai->info);
            break;
        }
    }
    ai->attribute_length = emitptr - buf;
    ai->info = TYPE_ALLOC_MULTI(u1, ai->attribute_length);
    memcpy(ai->info, buf, ai->attribute_length);
}
