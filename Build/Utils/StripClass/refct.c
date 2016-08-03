#include "defs.h"
#include "refct.h"
#include "dump.h"
#include "code.h"

  void
ref(ClassFile *cf, int idx)
{
    cf->constant_pool[idx]->refCount++;
}

  void
refct_ClassFile(ClassFile *cf)
{
    int i;
    for (i = 0; i < cf->constant_pool_count; ++i) {
        if (cf->constant_pool[i] != null) {
            cf->constant_pool[i]->refCount = 0;
        }
    }
    ref(cf, cf->this_class);
    ref(cf, cf->super_class);
    for (i = 0; i < cf->constant_pool_count; ++i) {
        refct_cp_info(cf, cf->constant_pool[i]);
    }
    for (i = 0; i < cf->interfaces_count; ++i) {
        ref(cf, cf->interfaces[i]);
    }
    for (i = 0; i < cf->fields_count; ++i) {
        refct_field_info(cf, cf->fields[i]);
    }
    for (i = 0; i < cf->methods_count; ++i) {
        refct_method_info(cf, cf->methods[i]);
    }
    for (i = 0; i < cf->attributes_count; ++i) {
        refct_attribute_info(cf, cf->attributes[i]);
    }
}

  void
refct_field_info(ClassFile *cf, field_info *fi)
{
    int i;
    ref(cf, fi->name_index);
    ref(cf, fi->descriptor_index);
    for (i = 0; i < fi->attributes_count; ++i) {
        refct_attribute_info(cf, fi->attributes[i]);
    }
}

  void
refct_method_info(ClassFile *cf, method_info *mi)
{
    int i;
    ref(cf, mi->name_index);
    ref(cf, mi->descriptor_index);
    for (i = 0; i < mi->attributes_count; ++i) {
        refct_attribute_info(cf, mi->attributes[i]);
    }
}

  void
refct_cp_info(ClassFile *cf, cp_info *cp)
{
    if (cp == null) {
        return;
    }
    switch (cp->tag) {
        case CONSTANT_Class: {
            constant_class_info *info = (constant_class_info *) cp;
            ref(cf, info->name_index);
            break;
        }
        case CONSTANT_Fieldref: {
            constant_fieldref_info *info = (constant_fieldref_info *) cp;
            ref(cf, info->class_index);
            ref(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_Methodref: {
            constant_methodref_info *info = (constant_methodref_info *) cp;
            ref(cf, info->class_index);
            ref(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_InterfaceMethodref: {
            constant_interfaceMethodref_info *info =
                (constant_interfaceMethodref_info *) cp;
            ref(cf, info->class_index);
            ref(cf, info->name_and_type_index);
            break;
        }
        case CONSTANT_String: {
            constant_string *info = (constant_string *) cp;
            ref(cf, info->string_index);
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
            ref(cf, info->name_index);
            ref(cf, info->descriptor_index);
            break;
        }
        case CONSTANT_Utf8:
            break;
    }
}

  void
refct_attribute_info(ClassFile *cf, attribute_info *ai)
{
    int i;
    ref(cf, ai->attribute_name_index);
    switch (ai->value->attribute_type) {
        case ATT_UNKNOWN:
            break;
        case ATT_CONSTANT_VALUE: {
            attribute_value_ConstantValue *value =
                (attribute_value_ConstantValue *) ai->value;
            ref(cf, value->constantvalue_index);
            break;
        }
        case ATT_CODE: {
            attribute_value_Code *value =
                (attribute_value_Code *) ai->value;
            refct_code(cf, value->code, value->code_length);
            for (i = 0; i < value->exception_table_length; ++i) {
                if (value->exception_table[i].catch_type != 0) {
                    ref(cf, value->exception_table[i].catch_type);
                }
            }
            for (i = 0; i < value->attributes_count; ++i) {
                refct_attribute_info(cf, value->attributes[i]);
            }
            break;
        }
        case ATT_EXCEPTIONS: {
            attribute_value_Exceptions *value =
                (attribute_value_Exceptions *) ai->value;
            for (i = 0; i < value->number_of_exceptions; ++i) {
                ref(cf, value->exception_index_table[i]);
            }
            break;
        }
        case ATT_INNER_CLASSES: {
            attribute_value_InnerClasses *value =
                (attribute_value_InnerClasses *) ai->value;
            for (i = 0; i < value->number_of_classes; ++i) {
                innerclasses_table_entry *entry = &value->classes[i];
                ref(cf, entry->inner_class_info_index);
                if (entry->outer_class_info_index != 0) {
                    ref(cf, entry->outer_class_info_index);
                }
                if (entry->inner_name_index != 0) {
                    ref(cf, entry->inner_name_index);
                }
            }
            break;
        }
        case ATT_SYNTHETIC:
            break;
        case ATT_SOURCE_FILE: {
            attribute_value_SourceFile *value =
                (attribute_value_SourceFile *) ai->value;
            ref(cf, value->sourcefile_index);
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
                ref(cf, entry->name_index);
                ref(cf, entry->descriptor_index);
            }
            break;
        }
        case ATT_DEPRECATED:
            break;
        case ATT_RUNTIME_VISIBLE_ANNOTATIONS: {
            attribute_value_RuntimeVisibleAnnotations *value =
                (attribute_value_RuntimeVisibleAnnotations *) ai->value;
            for (i = 0; i < value->num_annotations; ++i) {
                refct_annotation(cf, value->annotations[i]);
            }
            break;
        }
        case ATT_ENCLOSING_METHOD: {
            attribute_value_EnclosingMethod *value =
                (attribute_value_EnclosingMethod *) ai->value;
            ref(cf, value->class_index);
            if (value->method_index != 0) {
                ref(cf, value->method_index);
            }
            break;
        }
        case ATT_STACK_MAP_TABLE:
            break;
        case ATT_SIGNATURE: {
            attribute_value_Signature *value =
                (attribute_value_Signature *) ai->value;
            ref(cf, value->signature_index);
            break;
        }
        case ATT_LOCAL_VARIABLE_TYPE_TABLE: {
            attribute_value_LocalVariableTypeTable *value =
                (attribute_value_LocalVariableTypeTable *) ai->value;
            for (i = 0; i < value->local_variable_type_table_length; ++i) {
                local_variable_type_table_entry *entry =
                    &value->local_variable_type_table[i];
                ref(cf, entry->name_index);
                ref(cf, entry->signature_index);
            }
            break;
        }
        case ATT_ANNOTATION_DEFAULT: {
            attribute_value_AnnotationDefault *value =
                (attribute_value_AnnotationDefault *) ai->value;
            refct_element_value(cf, value->default_value);
            break;
        }
    }
}

  void
refct_code(ClassFile *cf, u1 *code, int length)
{
    int savePC = g_pc;
    u1 *saveCode = g_code;
    g_pc = 0;
    g_code = code;

    for (g_pc = 0; g_pc < length; ) {
        g_addr = g_pc;
        switch (g_code[g_pc++]) {
            case OP_AALOAD:          rop_0();            break;
            case OP_AASTORE:         rop_0();            break;
            case OP_ACONST_NULL:     rop_0();            break;
            case OP_ALOAD:           rop_1b();           break;
            case OP_ALOAD_0:         rop_0();            break;
            case OP_ALOAD_1:         rop_0();            break;
            case OP_ALOAD_2:         rop_0();            break;
            case OP_ALOAD_3:         rop_0();            break;
            case OP_ANEWARRAY:       rop_1wc(cf);        break;
            case OP_ARETURN:         rop_0();            break;
            case OP_ARRAYLENGTH:     rop_0();            break;
            case OP_ASTORE:          rop_1b();           break;
            case OP_ASTORE_0:        rop_0();            break;
            case OP_ASTORE_1:        rop_0();            break;
            case OP_ASTORE_2:        rop_0();            break;
            case OP_ASTORE_3:        rop_0();            break;
            case OP_ATHROW:          rop_0();            break;
            case OP_BALOAD:          rop_0();            break;
            case OP_BASTORE:         rop_0();            break;
            case OP_BIPUSH:          rop_1b();           break;
            case OP_CALOAD:          rop_0();            break;
            case OP_CASTORE:         rop_0();            break;
            case OP_CHECKCAST:       rop_1wc(cf);        break;
            case OP_D2F:             rop_0();            break;
            case OP_D2I:             rop_0();            break;
            case OP_D2L:             rop_0();            break;
            case OP_DADD:            rop_0();            break;
            case OP_DALOAD:          rop_0();            break;
            case OP_DASTORE:         rop_0();            break;
            case OP_DCMPG:           rop_0();            break;
            case OP_DCMPL:           rop_0();            break;
            case OP_DCONST_0:        rop_0();            break;
            case OP_DCONST_1:        rop_0();            break;
            case OP_DDIV:            rop_0();            break;
            case OP_DLOAD:           rop_1b();           break;
            case OP_DLOAD_0:         rop_0();            break;
            case OP_DLOAD_1:         rop_0();            break;
            case OP_DLOAD_2:         rop_0();            break;
            case OP_DLOAD_3:         rop_0();            break;
            case OP_DMUL:            rop_0();            break;
            case OP_DNEG:            rop_0();            break;
            case OP_DREM:            rop_0();            break;
            case OP_DRETURN:         rop_0();            break;
            case OP_DSTORE:          rop_1b();           break;
            case OP_DSTORE_0:        rop_0();            break;
            case OP_DSTORE_1:        rop_0();            break;
            case OP_DSTORE_2:        rop_0();            break;
            case OP_DSTORE_3:        rop_0();            break;
            case OP_DSUB:            rop_0();            break;
            case OP_DUP:             rop_0();            break;
            case OP_DUP_X1:          rop_0();            break;
            case OP_DUP_X2:          rop_0();            break;
            case OP_DUP2:            rop_0();            break;
            case OP_DUP2_X1:         rop_0();            break;
            case OP_DUP2_X2:         rop_0();            break;
            case OP_F2D:             rop_0();            break;
            case OP_F2I:             rop_0();            break;
            case OP_F2L:             rop_0();            break;
            case OP_FADD:            rop_0();            break;
            case OP_FALOAD:          rop_0();            break;
            case OP_FASTORE:         rop_0();            break;
            case OP_FCMPG:           rop_0();            break;
            case OP_FCMPL:           rop_0();            break;
            case OP_FCONST_0:        rop_0();            break;
            case OP_FCONST_1:        rop_0();            break;
            case OP_FCONST_2:        rop_0();            break;
            case OP_FDIV:            rop_0();            break;
            case OP_FLOAD:           rop_1b();           break;
            case OP_FLOAD_0:         rop_0();            break;
            case OP_FLOAD_1:         rop_0();            break;
            case OP_FLOAD_2:         rop_0();            break;
            case OP_FLOAD_3:         rop_0();            break;
            case OP_FMUL:            rop_0();            break;
            case OP_FNEG:            rop_0();            break;
            case OP_FREM:            rop_0();            break;
            case OP_FRETURN:         rop_0();            break;
            case OP_FSTORE:          rop_1b();           break;
            case OP_FSTORE_0:        rop_0();            break;
            case OP_FSTORE_1:        rop_0();            break;
            case OP_FSTORE_2:        rop_0();            break;
            case OP_FSTORE_3:        rop_0();            break;
            case OP_FSUB:            rop_0();            break;
            case OP_GETFIELD:        rop_1wc(cf);        break;
            case OP_GETSTATIC:       rop_1wc(cf);        break;
            case OP_GOTO:            rop_1w();           break;
            case OP_GOTO_W:          rop_1l();           break;
            case OP_I2B:             rop_0();            break;
            case OP_I2C:             rop_0();            break;
            case OP_I2D:             rop_0();            break;
            case OP_I2F:             rop_0();            break;
            case OP_I2L:             rop_0();            break;
            case OP_I2S:             rop_0();            break;
            case OP_IADD:            rop_0();            break;
            case OP_IALOAD:          rop_0();            break;
            case OP_IAND:            rop_0();            break;
            case OP_IASTORE:         rop_0();            break;
            case OP_ICONST_M1:       rop_0();            break;
            case OP_ICONST_0:        rop_0();            break;
            case OP_ICONST_1:        rop_0();            break;
            case OP_ICONST_2:        rop_0();            break;
            case OP_ICONST_3:        rop_0();            break;
            case OP_ICONST_4:        rop_0();            break;
            case OP_ICONST_5:        rop_0();            break;
            case OP_IDIV:            rop_0();            break;
            case OP_IF_ACMPEQ:       rop_1w();           break;
            case OP_IF_ACMPNE:       rop_1w();           break;
            case OP_IF_ICMPEQ:       rop_1w();           break;
            case OP_IF_ICMPGE:       rop_1w();           break;
            case OP_IF_ICMPGT:       rop_1w();           break;
            case OP_IF_ICMPLE:       rop_1w();           break;
            case OP_IF_ICMPLT:       rop_1w();           break;
            case OP_IF_ICMPNE:       rop_1w();           break;
            case OP_IFEQ:            rop_1w();           break;
            case OP_IFGE:            rop_1w();           break;
            case OP_IFGT:            rop_1w();           break;
            case OP_IFLE:            rop_1w();           break;
            case OP_IFLT:            rop_1w();           break;
            case OP_IFNE:            rop_1w();           break;
            case OP_IFNONNULL:       rop_1w();           break;
            case OP_IFNULL:          rop_1w();           break;
            case OP_IINC:            rop_2bb();          break;
            case OP_ILOAD:           rop_1b();           break;
            case OP_ILOAD_0:         rop_0();            break;
            case OP_ILOAD_1:         rop_0();            break;
            case OP_ILOAD_2:         rop_0();            break;
            case OP_ILOAD_3:         rop_0();            break;
            case OP_IMUL:            rop_0();            break;
            case OP_INEG:            rop_0();            break;
            case OP_INSTANCEOF:      rop_1wc(cf);        break;
            case OP_INVOKEINTERFACE: rop_3wcbx(cf);      break;
            case OP_INVOKESPECIAL:   rop_1wc(cf);        break;
            case OP_INVOKESTATIC:    rop_1wc(cf);        break;
            case OP_INVOKEVIRTUAL:   rop_1wc(cf);        break;
            case OP_IOR:             rop_0();            break;
            case OP_IREM:            rop_0();            break;
            case OP_IRETURN:         rop_0();            break;
            case OP_ISHL:            rop_0();            break;
            case OP_ISHR:            rop_0();            break;
            case OP_ISTORE:          rop_1b();           break;
            case OP_ISTORE_0:        rop_0();            break;
            case OP_ISTORE_1:        rop_0();            break;
            case OP_ISTORE_2:        rop_0();            break;
            case OP_ISTORE_3:        rop_0();            break;
            case OP_ISUB:            rop_0();            break;
            case OP_IUSHR:           rop_0();            break;
            case OP_IXOR:            rop_0();            break;
            case OP_JSR:             rop_1w();           break;
            case OP_JSR_W:           rop_1l();           break;
            case OP_L2D:             rop_0();            break;
            case OP_L2F:             rop_0();            break;
            case OP_L2I:             rop_0();            break;
            case OP_LADD:            rop_0();            break;
            case OP_LALOAD:          rop_0();            break;
            case OP_LAND:            rop_0();            break;
            case OP_LASTORE:         rop_0();            break;
            case OP_LCMP:            rop_0();            break;
            case OP_LCONST_0:        rop_0();            break;
            case OP_LCONST_1:        rop_0();            break;
            case OP_LDC:             rop_1bc(cf);        break;
            case OP_LDC_W:           rop_1wc(cf);        break;
            case OP_LDC2_W:          rop_1wc(cf);        break;
            case OP_LDIV:            rop_0();            break;
            case OP_LLOAD:           rop_1b();           break;
            case OP_LLOAD_0:         rop_0();            break;
            case OP_LLOAD_1:         rop_0();            break;
            case OP_LLOAD_2:         rop_0();            break;
            case OP_LLOAD_3:         rop_0();            break;
            case OP_LMUL:            rop_0();            break;
            case OP_LNEG:            rop_0();            break;
            case OP_LOOKUPSWITCH:    rop_lookupswitch(); break;
            case OP_LOR:             rop_0();            break;
            case OP_LREM:            rop_0();            break;
            case OP_LRETURN:         rop_0();            break;
            case OP_LSHL:            rop_0();            break;
            case OP_LSHR:            rop_0();            break;
            case OP_LSTORE:          rop_1b();           break;
            case OP_LSTORE_0:        rop_0();            break;
            case OP_LSTORE_1:        rop_0();            break;
            case OP_LSTORE_2:        rop_0();            break;
            case OP_LSTORE_3:        rop_0();            break;
            case OP_LSUB:            rop_0();            break;
            case OP_LUSHR:           rop_0();            break;
            case OP_LXOR:            rop_0();            break;
            case OP_MONITORENTER:    rop_0();            break;
            case OP_MONITOREXIT:     rop_0();            break;
            case OP_MULTINEWARRAY:   rop_2wcb(cf);       break;
            case OP_NEW:             rop_1wc(cf);        break;
            case OP_NEWARRAY:        rop_1b();           break;
            case OP_NOP:             rop_0();            break;
            case OP_POP:             rop_0();            break;
            case OP_POP2:            rop_0();            break;
            case OP_PUTFIELD:        rop_1wc(cf);        break;
            case OP_PUTSTATIC:       rop_1wc(cf);        break;
            case OP_RET:             rop_1b();           break;
            case OP_RETURN:          rop_0();            break;
            case OP_SALOAD:          rop_0();            break;
            case OP_SASTORE:         rop_0();            break;
            case OP_SIPUSH:          rop_1w();           break;
            case OP_SWAP:            rop_0();            break;
            case OP_TABLESWITCH:     rop_tableswitch();  break;
            case OP_WIDE:            rop_wide();         break;
        }
    }
    g_code = saveCode;
    g_pc = savePC;
}

  void
refct_annotation(ClassFile *cf, annotation *ann)
{
    ref(cf, ann->type_index);
    int i;
    for (i = 0; i < ann->num_element_value_pairs; ++i) {
        element_value_pair *pair = &ann->element_value_pairs[i];
        ref(cf, pair->element_name_index);
        refct_element_value(cf, pair->value);
    }
}

  void
refct_element_value(ClassFile *cf, element_value *elem)
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
            ref(cf, value->const_value_index);
            break;
        }
        case 'e': {
            element_value_enum_const *value =
                (element_value_enum_const *) elem;
            ref(cf, value->type_name_index);
            ref(cf, value->const_name_index);
            break;
        }
        case 'c': {
            element_value_class_info *value =
                (element_value_class_info *) elem;
            ref(cf, value->class_info_index);
            break;
        }
        case '@': {
            element_value_annotation *value =
                (element_value_annotation *) elem;
            refct_annotation(cf, value->annotation_value);
            break;
        }
        case '[': {
            element_value_array *value = (element_value_array *) elem;
            for (i = 0; i < value->num_values; ++i) {
                refct_element_value(cf, value->values[i]);
            }
            break;
        }
    }
}

  void
rop_0()
{
}

  void
rop_1b()
{
    argb();
}

  void
rop_1bc(ClassFile *cf)
{
    ref(cf, argb());
}

  void
rop_1w()
{
    argw();
}

  void
rop_1wc(ClassFile *cf)
{
    ref(cf, argw());
}

  void
rop_1l()
{
    argl();
}

  void
rop_2bb()
{
    argb();
    argb();
}

  void
rop_2wb()
{
    argw();
    argb();
}

  void
rop_2wcb(ClassFile *cf)
{
    ref(cf, argw());
    argb();
}

  void
rop_2ww()
{
    argw();
    argw();
}

  void
rop_3wcbx(ClassFile *cf)
{
    ref(cf, argw());
    argb();
    argb();
}

  void
rop_lookupswitch()
{
    while (g_pc % 4) {
        ++g_pc;
    }
    argl();
    int opndPairCt = argl();
    while (opndPairCt-- > 0) {
        argl();
        argl();
    }
}

  void
rop_tableswitch()
{
    while (g_pc % 4) {
        ++g_pc;
    }
    argl();
    int opndRangeLo = argl();
    int opndRangeHi = argl();
    while (opndRangeLo++ < opndRangeHi) {
        argl();
    }
}

  void
rop_wide()
{
    int modifOp = argb();
    switch (modifOp) {
        case OP_ALOAD:  rop_1w();  break;
        case OP_ASTORE: rop_1w(); break;
        case OP_DLOAD:  rop_1w();  break;
        case OP_DSTORE: rop_1w(); break;
        case OP_FLOAD:  rop_1w();  break;
        case OP_FSTORE: rop_1w(); break;
        case OP_IINC:   rop_2ww();   break;
        case OP_ILOAD:  rop_1w();  break;
        case OP_ISTORE: rop_1w(); break;
        case OP_LLOAD:  rop_1w();  break;
        case OP_LSTORE: rop_1w(); break;
        case OP_RET:    rop_1w();    break;
        default:
            printf("illegal opcode 0x%02x in wide op\n", modifOp); break;
    }
}
