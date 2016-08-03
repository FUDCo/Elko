#include "defs.h"
#include "gc.h"

  int
map_cp(ClassFile *cf, int idx)
{
    return cf->constant_pool_map[idx];
}

#define MAP(cf, idx) idx = map_cp(cf, idx)

  bool
gc_ClassFile(ClassFile *cf)
{
    int i;
    int *map = TYPE_ALLOC_MULTI(int, cf->constant_pool_count);
    cf->constant_pool_map = map;

    int newCount = 1;
    int oldCount = cf->constant_pool_count;
    for (i = 1; i < oldCount; ++i) {
        bool isLong = false;
        if (cf->constant_pool[i]->tag == CONSTANT_Long ||
                cf->constant_pool[i]->tag == CONSTANT_Double) {
            isLong = true;
        }
        if (cf->constant_pool[i]->refCount != 0) {
            cf->constant_pool[newCount] = cf->constant_pool[i];
            map[i] = newCount++;
            if (isLong) {
                ++i;
                cf->constant_pool[newCount] = cf->constant_pool[i];
                map[i] = newCount++;
            }
        } else {
            if (isLong) {
                ++i;
            }
        }
    }
    cf->constant_pool_count = newCount;
    MAP(cf, cf->this_class);
    MAP(cf, cf->super_class);
    for (i = 0; i < cf->constant_pool_count; ++i) {
        gc_cp_info(cf, cf->constant_pool[i]);
    }
    for (i = 0; i < cf->interfaces_count; ++i) {
        MAP(cf, cf->interfaces[i]);
    }
    for (i = 0; i < cf->fields_count; ++i) {
        gc_field_info(cf, cf->fields[i]);
    }
    for (i = 0; i < cf->methods_count; ++i) {
        gc_method_info(cf, cf->methods[i]);
    }
    for (i = 0; i < cf->attributes_count; ++i) {
        gc_attribute_info(cf, cf->attributes[i]);
    }
    return oldCount == newCount;
}

  void
gc_field_info(ClassFile *cf, field_info *fi)
{
    int i;
    MAP(cf, fi->name_index);
    MAP(cf, fi->descriptor_index);
    for (i = 0; i < fi->attributes_count; ++i) {
        gc_attribute_info(cf, fi->attributes[i]);
    }
}

  void
gc_method_info(ClassFile *cf, method_info *mi)
{
    int i;
    MAP(cf, mi->name_index);
    MAP(cf, mi->descriptor_index);
    for (i = 0; i < mi->attributes_count; ++i) {
        gc_attribute_info(cf, mi->attributes[i]);
    }
}

  void
gc_cp_info(ClassFile *cf, cp_info *cp)
{
    if (cp == null) {
        return;
    }
    switch (cp->tag) {
        case CONSTANT_Class: {
            constant_class_info *info = (constant_class_info *) cp;
            MAP(cf, info->name_index);
            break;
        }
        case CONSTANT_Fieldref: {
            constant_fieldref_info *info = (constant_fieldref_info *) cp;
            MAP(cf, info->class_index);
            MAP(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_Methodref: {
            constant_methodref_info *info = (constant_methodref_info *) cp;
            MAP(cf, info->class_index);
            MAP(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_InterfaceMethodref: {
            constant_interfaceMethodref_info *info =
                (constant_interfaceMethodref_info *) cp;
            MAP(cf, info->class_index);
            MAP(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_String: {
            constant_string *info = (constant_string *) cp;
            MAP(cf, info->string_index);
            break;
        }
        case CONSTANT_Integer:
            break;
        case CONSTANT_Float:
            break;
        case CONSTANT_Long:
            break;
        case CONSTANT_Double:
            break;
        case CONSTANT_NameAndType: {
            constant_nameAndType_info *info =
                (constant_nameAndType_info *) cp;
            MAP(cf, info->name_index);
            MAP(cf, info->descriptor_index);
            break;
        }
        case CONSTANT_Utf8:
            break;
    }
}

  void
gc_attribute_info(ClassFile *cf, attribute_info *ai)
{
    int i;
    MAP(cf, ai->attribute_name_index);
    switch (ai->value->attribute_type) {
        case ATT_UNKNOWN:
            break;
        case ATT_CONSTANT_VALUE: {
            attribute_value_ConstantValue *value =
                (attribute_value_ConstantValue *) ai->value;
            MAP(cf, value->constantvalue_index);
            break;
        }
        case ATT_CODE: {
            attribute_value_Code *value =
                (attribute_value_Code *) ai->value;
            for (i = 0; i < value->attributes_count; ++i) {
                gc_attribute_info(cf, value->attributes[i]);
            }
            break;
        }
        case ATT_EXCEPTIONS: {
            attribute_value_Exceptions *value =
                (attribute_value_Exceptions *) ai->value;
            for (i = 0; i < value->number_of_exceptions; ++i) {
                MAP(cf, value->exception_index_table[i]);
            }
            break;
        }
        case ATT_INNER_CLASSES: {
            attribute_value_InnerClasses *value =
                (attribute_value_InnerClasses *) ai->value;
            for (i = 0; i < value->number_of_classes; ++i) {
                innerclasses_table_entry *entry = &value->classes[i];
                MAP(cf, entry->inner_class_info_index);
                if (entry->outer_class_info_index != 0) {
                    MAP(cf, entry->outer_class_info_index);
                }
                if (entry->inner_name_index != 0) {
                    MAP(cf, entry->inner_name_index);
                }
            }
            break;
        }
        case ATT_SYNTHETIC:
            break;
        case ATT_SOURCE_FILE: {
            attribute_value_SourceFile *value =
                (attribute_value_SourceFile *) ai->value;
            MAP(cf, value->sourcefile_index);
            break;
        }
        case ATT_LINE_NUMBER_TABLE:
            break;
        case ATT_LOCAL_VARIABLE_TABLE: {
            attribute_value_LocalVariableTable *value =
                (attribute_value_LocalVariableTable *) ai->value;
            for (i = 0; i < value->local_variable_table_length; ++i) {
                local_variable_table_entry *entry =
                    &value->local_variable_table[i];
                MAP(cf, entry->name_index);
                MAP(cf, entry->descriptor_index);
            }
            break;
        }
        case ATT_DEPRECATED:
            break;
        case ATT_RUNTIME_VISIBLE_ANNOTATIONS: {
            attribute_value_RuntimeVisibleAnnotations *value =
                (attribute_value_RuntimeVisibleAnnotations *) ai->value;
            for (i = 0; i < value->num_annotations; ++i) {
                gc_annotation(cf, value->annotations[i]);
            }
            break;
        }
        case ATT_ENCLOSING_METHOD: {
            attribute_value_EnclosingMethod *value =
                (attribute_value_EnclosingMethod *) ai->value;
            MAP(cf, value->class_index);
            if (value->method_index != 0) {
                MAP(cf, value->method_index);
            }
            break;
        }
        case ATT_STACK_MAP_TABLE:
            break;
        case ATT_SIGNATURE: {
            attribute_value_Signature *value =
                (attribute_value_Signature *) ai->value;
            MAP(cf, value->signature_index);
            break;
        }
        case ATT_LOCAL_VARIABLE_TYPE_TABLE: {
            attribute_value_LocalVariableTypeTable *value =
                (attribute_value_LocalVariableTypeTable *) ai->value;
            for (i = 0; i < value->local_variable_type_table_length; ++i) {
                local_variable_type_table_entry *entry =
                    &value->local_variable_type_table[i];
                MAP(cf, entry->name_index);
                MAP(cf, entry->signature_index);
            }
            break;
        }
        case ATT_ANNOTATION_DEFAULT: {
            attribute_value_AnnotationDefault *value =
                (attribute_value_AnnotationDefault *) ai->value;
            gc_element_value(cf, value->default_value);
            break;
        }
    }
}

  void
gc_annotation(ClassFile *cf, annotation *ann)
{
    MAP(cf, ann->type_index);
    int i;
    for (i = 0; i < ann->num_element_value_pairs; ++i) {
        element_value_pair *pair = &ann->element_value_pairs[i];
        MAP(cf, pair->element_name_index);
        gc_element_value(cf, pair->value);
    }
}

  void
gc_element_value(ClassFile *cf, element_value *elem)
{
    int i;
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
            element_value_const *value = (element_value_const *) elem;
            MAP(cf, value->const_value_index);
            break;
        }
        case 'e': {
            element_value_enum_const *value =
                (element_value_enum_const *) elem;
            MAP(cf, value->type_name_index);
            MAP(cf, value->const_name_index);
            break;
        }
        case 'c': {
            element_value_class_info *value =
                (element_value_class_info *) elem;
            MAP(cf, value->class_info_index);
            break;
        }
        case '@': {
            element_value_annotation *value =
                (element_value_annotation *) elem;
            gc_annotation(cf, value->annotation_value);
            break;
        }
        case '[': {
            element_value_array *value = (element_value_array *) elem;
            for (i = 0; i < value->num_values; ++i) {
                gc_element_value(cf, value->values[i]);
            }
            break;
        }
    }
}
