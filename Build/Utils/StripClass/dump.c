#include "defs.h"
#include "dump.h"
#include "util.h"
#include "code.h"

int g_tab = 0;
bool g_verbose = false;

  void
op_0(char *mnem)
{
    printf("    %4x %s\n", g_addr, mnem);
}

  void
op_1b(char *mnem)
{
    printf("    %4x %s %d\n", g_addr, mnem, argb());
}

  void
op_1bc(char *mnem)
{
    int opnd = argb();
    printf("    %4x %s c%d\n", g_addr, mnem, opnd);
}

  void
op_1bv(char *mnem)
{
    printf("    %4x %s v%d\n", g_addr, mnem, argb());
}

  void
op_1w(char *mnem)
{
    printf("    %4x %s %d\n", g_addr, mnem, argw());
}

  void
op_1ws(char *mnem)
{
    short opnd = argw();
    printf("    %4x %s %d\n", g_addr, mnem, opnd);
}

  void
op_1wc(char *mnem)
{
    int opnd = argw();
    printf("    %4x %s c%d\n", g_addr, mnem, opnd);
}

  void
op_1l(char *mnem)
{
    printf("    %4x %s %d\n", g_addr, mnem, argl());
}

  void
op_2bb(char *mnem)
{
    int opnd1 = argb();
    int opnd2 = argb();
    printf("    %4x %s %d %d\n", g_addr, mnem, opnd1, opnd2);
}

  void
op_2wb(char *mnem)
{
    int opnd1 = argw();
    int opnd2 = argb();
    printf("    %4x %s %d %d\n", g_addr, mnem, opnd1, opnd2);
}

  void
op_2wcb(char *mnem)
{
    int opnd1 = argw();
    int opnd2 = argb();
    printf("    %4x %s c%d %d\n", g_addr, mnem, opnd1, opnd2);
}

  void
op_3wcbx(char *mnem)
{
    int opnd1 = argw();
    int opnd2 = argb();
    int opnd3 = argb();
    printf("    %4x %s c%d %d\n", g_addr, mnem, opnd1, opnd2);
}

  void
op_lookupswitch()
{
    while (g_pc % 4) {
        ++g_pc;
    }
    int opndDef = argl();
    int opndPairCt = argl();
    printf("    %4x lookupswitch %d %d\n", g_addr, opndDef, opndPairCt);
    while (opndPairCt-- > 0) {
        int match = argl();
        int offset = argl();
        printf("            %d %d\n", match, offset);
    }
}

  void
op_tableswitch()
{
    while (g_pc % 4) {
        ++g_pc;
    }
    int opndDef = argl();
    int opndRangeLo = argl();
    int opndRangeHi = argl();
    printf("    %4x tableswitch %d %d %d\n", g_addr, opndDef, opndRangeLo, opndRangeHi);
    while (opndRangeLo < opndRangeHi) {
        int offset = argl();
        printf("            [%d] %d\n", opndRangeLo++, offset);
    }
}

  void
opw_1(char *mnem)
{
    int opnd1 = argw();
    printf("    %4x wide %s v%d\n", g_addr, mnem, opnd1);
}

  void
opw_2(char *mnem)
{
    int opnd1 = argw();
    short opnd2 = argw();
    printf("    %4x wide %s v%d %d\n", g_addr, mnem, opnd1, opnd2);
}

  void
op_wide()
{
    int modifOp = argb();
    switch (modifOp) {
        case OP_ALOAD:  opw_1("aload");  break;
        case OP_ASTORE: opw_1("astore"); break;
        case OP_DLOAD:  opw_1("dload");  break;
        case OP_DSTORE: opw_1("dstore"); break;
        case OP_FLOAD:  opw_1("fload");  break;
        case OP_FSTORE: opw_1("fstore"); break;
        case OP_IINC:   opw_2("iinc");   break;
        case OP_ILOAD:  opw_1("iload");  break;
        case OP_ISTORE: opw_1("istore"); break;
        case OP_LLOAD:  opw_1("lload");  break;
        case OP_LSTORE: opw_1("lstore"); break;
        case OP_RET:    opw_1("ret");    break;
        default:
            printf("illegal opcode 0x%02x in wide op\n", modifOp); break;
    }
}

  void
dump_hexData(u1 *bytes, int count)
{
    int i;
    for (i = 0; i < count; ++i) {
        if (i % 16 == 0) {
            printf("  %4x:", i);
        }
        printf(" %02x", bytes[i]);
        if (i % 16 == 15) {
            printf("\n");
        }
    }
    if (count % 16 != 0) {
        printf("\n");
    }
}

  void
dump_element_value(ClassFile *cf, element_value *elem)
{
    ++g_tab;
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
            switch (elem->tag) {
                case 'B':
                    printf(" (const byte) %d\n", value->const_value_index);
                    break;
                case 'C':
                    printf(" (const char) %d\n", value->const_value_index);
                    break;
                case 'D':
                    printf(" (const double) %d\n", value->const_value_index);
                    break;
                case 'F':
                    printf(" (const float) %d\n", value->const_value_index);
                    break;
                case 'I':
                    printf(" (const int) %d\n", value->const_value_index);
                    break;
                case 'J':
                    printf(" (const long) %d\n", value->const_value_index);
                    break;
                case 'S':
                    printf(" (const short) %d\n", value->const_value_index);
                    break;
                case 's':
                    printf(" (const str) %d (%s)\n", value->const_value_index,
                           pUtf8(cf, value->const_value_index));
                    break;
                case 'Z':
                    printf(" (const boolean) %d\n", value->const_value_index);
                    break;
            }
            break;
        }
        case 'e': {
            element_value_enum_const *value =
                (element_value_enum_const *) elem;
            printf(" (enum const) type: %d (%s)  const: %d (%s)\n",
                   value->type_name_index, pUtf8(cf, value->type_name_index),
                   value->const_name_index,
                   pUtf8(cf, value->const_name_index));
            break;
        }
        case 'c': {
            element_value_class_info *value =
                (element_value_class_info *) elem;
            printf(" (class) class: %d (%s)\n", value->class_info_index,
                   pUtf8(cf, value->class_info_index));
            break;
        }
        case '@': {
            element_value_annotation *value =
                (element_value_annotation *) elem;
            printf(" (annotation) annotion: ");
            dump_annotation(cf, value->annotation_value);
            break;
        }
        case '[': {
            element_value_array *value = (element_value_array *) elem;
            printf("  (array) %d elements:\n", value->num_values);
            int i;
            for (i = 0; i < value->num_values; ++i) {
                printf("%s  [%d]: '%c'", t(), i, value->values[i]->tag);
                dump_element_value(cf, value->values[i]);
            }
            break;
        }
    }
    --g_tab;
}

  void
dump_annotation(ClassFile *cf, annotation *ann)
{
    ++g_tab;
    printf("type: %d (%s)\n", ann->type_index, pUtf8(cf, ann->type_index));
    printf("%s%d element value pairs:\n", t(), ann->num_element_value_pairs);
    int i;
    for (i = 0; i < ann->num_element_value_pairs; ++i) {
        element_value_pair *pair = &ann->element_value_pairs[i];
        printf("%s  [%d]: index: %d (%s)\n%s    value: '%c'", t(), i,
               pair->element_name_index, pUtf8(cf, pair->element_name_index),
               t(), pair->value->tag);
        dump_element_value(cf, pair->value);
    }
    --g_tab;
}

  void
dump_code(ClassFile *cf, u1 *code, int length)
{
    int savePC = g_pc;
    u1 *saveCode = g_code;
    g_pc = 0;
    g_code = code;

    for (g_pc = 0; g_pc < length; ) {
        g_addr = g_pc;
        switch (g_code[g_pc++]) {
            case OP_AALOAD:          op_0("aaload");               break;
            case OP_AASTORE:         op_0("aastore");              break;
            case OP_ACONST_NULL:     op_0("aconst_null");          break;
            case OP_ALOAD:           op_1bv("aload");              break;
            case OP_ALOAD_0:         op_0("aload_0");              break;
            case OP_ALOAD_1:         op_0("aload_1");              break;
            case OP_ALOAD_2:         op_0("aload_2");              break;
            case OP_ALOAD_3:         op_0("aload_3");              break;
            case OP_ANEWARRAY:       op_1wc("anewarray");          break;
            case OP_ARETURN:         op_0("areturn");              break;
            case OP_ARRAYLENGTH:     op_0("arraylength");          break;
            case OP_ASTORE:          op_1bv("astore");             break;
            case OP_ASTORE_0:        op_0("astore_0");             break;
            case OP_ASTORE_1:        op_0("astore_1");             break;
            case OP_ASTORE_2:        op_0("astore_2");             break;
            case OP_ASTORE_3:        op_0("astore_3");             break;
            case OP_ATHROW:          op_0("athrow");               break;
            case OP_BALOAD:          op_0("baload");               break;
            case OP_BASTORE:         op_0("bastore");              break;
            case OP_BIPUSH:          op_1b("bipush");              break;
            case OP_CALOAD:          op_0("caload");               break;
            case OP_CASTORE:         op_0("castore");              break;
            case OP_CHECKCAST:       op_1wc("checkcast");          break;
            case OP_D2F:             op_0("d2f");                  break;
            case OP_D2I:             op_0("d2i");                  break;
            case OP_D2L:             op_0("d2l");                  break;
            case OP_DADD:            op_0("dadd");                 break;
            case OP_DALOAD:          op_0("daload");               break;
            case OP_DASTORE:         op_0("dastore");              break;
            case OP_DCMPG:           op_0("dcmpg");                break;
            case OP_DCMPL:           op_0("dcmpl");                break;
            case OP_DCONST_0:        op_0("dconst_0");             break;
            case OP_DCONST_1:        op_0("dconst_1");             break;
            case OP_DDIV:            op_0("ddiv");                 break;
            case OP_DLOAD:           op_1bv("dload");              break;
            case OP_DLOAD_0:         op_0("dload_0");              break;
            case OP_DLOAD_1:         op_0("dload_1");              break;
            case OP_DLOAD_2:         op_0("dload_2");              break;
            case OP_DLOAD_3:         op_0("dload_3");              break;
            case OP_DMUL:            op_0("dmul");                 break;
            case OP_DNEG:            op_0("dneg");                 break;
            case OP_DREM:            op_0("drem");                 break;
            case OP_DRETURN:         op_0("dreturn");              break;
            case OP_DSTORE:          op_1bv("dstore");             break;
            case OP_DSTORE_0:        op_0("dstore_0");             break;
            case OP_DSTORE_1:        op_0("dstore_1");             break;
            case OP_DSTORE_2:        op_0("dstore_2");             break;
            case OP_DSTORE_3:        op_0("dstore_3");             break;
            case OP_DSUB:            op_0("dsub");                 break;
            case OP_DUP:             op_0("dup");                  break;
            case OP_DUP_X1:          op_0("dup_x1");               break;
            case OP_DUP_X2:          op_0("dup_x2");               break;
            case OP_DUP2:            op_0("dup2");                 break;
            case OP_DUP2_X1:         op_0("dup2_x1");              break;
            case OP_DUP2_X2:         op_0("dup2_x2");              break;
            case OP_F2D:             op_0("f2d");                  break;
            case OP_F2I:             op_0("f2i");                  break;
            case OP_F2L:             op_0("f2l");                  break;
            case OP_FADD:            op_0("fadd");                 break;
            case OP_FALOAD:          op_0("faload");               break;
            case OP_FASTORE:         op_0("fastore");              break;
            case OP_FCMPG:           op_0("fcmpg");                break;
            case OP_FCMPL:           op_0("fcmpl");                break;
            case OP_FCONST_0:        op_0("fconst_0");             break;
            case OP_FCONST_1:        op_0("fconst_1");             break;
            case OP_FCONST_2:        op_0("fconst_2");             break;
            case OP_FDIV:            op_0("fdiv");                 break;
            case OP_FLOAD:           op_1bv("fload");              break;
            case OP_FLOAD_0:         op_0("fload_0");              break;
            case OP_FLOAD_1:         op_0("fload_1");              break;
            case OP_FLOAD_2:         op_0("fload_2");              break;
            case OP_FLOAD_3:         op_0("fload_3");              break;
            case OP_FMUL:            op_0("fmul");                 break;
            case OP_FNEG:            op_0("fneg");                 break;
            case OP_FREM:            op_0("frem");                 break;
            case OP_FRETURN:         op_0("freturn");              break;
            case OP_FSTORE:          op_1bv("fstore");             break;
            case OP_FSTORE_0:        op_0("fstore_0");             break;
            case OP_FSTORE_1:        op_0("fstore_1");             break;
            case OP_FSTORE_2:        op_0("fstore_2");             break;
            case OP_FSTORE_3:        op_0("fstore_3");             break;
            case OP_FSUB:            op_0("fsub");                 break;
            case OP_GETFIELD:        op_1wc("getfield");           break;
            case OP_GETSTATIC:       op_1wc("getstatic");          break;
            case OP_GOTO:            op_1ws("goto");               break;
            case OP_GOTO_W:          op_1l("goto_w");              break;
            case OP_I2B:             op_0("i2b");                  break;
            case OP_I2C:             op_0("i2c");                  break;
            case OP_I2D:             op_0("i2d");                  break;
            case OP_I2F:             op_0("i2f");                  break;
            case OP_I2L:             op_0("i2l");                  break;
            case OP_I2S:             op_0("i2s");                  break;
            case OP_IADD:            op_0("iadd");                 break;
            case OP_IALOAD:          op_0("iaload");               break;
            case OP_IAND:            op_0("iand");                 break;
            case OP_IASTORE:         op_0("iastore");              break;
            case OP_ICONST_M1:       op_0("iconst_m1");            break;
            case OP_ICONST_0:        op_0("iconst_0");             break;
            case OP_ICONST_1:        op_0("iconst_1");             break;
            case OP_ICONST_2:        op_0("iconst_2");             break;
            case OP_ICONST_3:        op_0("iconst_3");             break;
            case OP_ICONST_4:        op_0("iconst_4");             break;
            case OP_ICONST_5:        op_0("iconst_5");             break;
            case OP_IDIV:            op_0("idiv");                 break;
            case OP_IF_ACMPEQ:       op_1ws("if_acmpeq");          break;
            case OP_IF_ACMPNE:       op_1ws("if_acmpne");          break;
            case OP_IF_ICMPEQ:       op_1ws("if_icmpeq");          break;
            case OP_IF_ICMPGE:       op_1ws("if_icmpge");          break;
            case OP_IF_ICMPGT:       op_1ws("if_icmpgt");          break;
            case OP_IF_ICMPLE:       op_1ws("if_icmple");          break;
            case OP_IF_ICMPLT:       op_1ws("if_icmplt");          break;
            case OP_IF_ICMPNE:       op_1ws("if_icmpne");          break;
            case OP_IFEQ:            op_1ws("ifeq");               break;
            case OP_IFGE:            op_1ws("ifge");               break;
            case OP_IFGT:            op_1ws("ifgt");               break;
            case OP_IFLE:            op_1ws("ifle");               break;
            case OP_IFLT:            op_1ws("iflt");               break;
            case OP_IFNE:            op_1ws("ifne");               break;
            case OP_IFNONNULL:       op_1ws("ifnonnull");          break;
            case OP_IFNULL:          op_1ws("ifnull");             break;
            case OP_IINC:            op_2bb("iinc");               break;
            case OP_ILOAD:           op_1bv("iload");              break;
            case OP_ILOAD_0:         op_0("iload_0");              break;
            case OP_ILOAD_1:         op_0("iload_1");              break;
            case OP_ILOAD_2:         op_0("iload_2");              break;
            case OP_ILOAD_3:         op_0("iload_3");              break;
            case OP_IMUL:            op_0("imul");                 break;
            case OP_INEG:            op_0("ineg");                 break;
            case OP_INSTANCEOF:      op_1wc("instanceof");         break;
            case OP_INVOKEINTERFACE: op_3wcbx("invokeinterface");  break;
            case OP_INVOKESPECIAL:   op_1wc("invokespecial");      break;
            case OP_INVOKESTATIC:    op_1wc("invokestatic");       break;
            case OP_INVOKEVIRTUAL:   op_1wc("invokevirtual");      break;
            case OP_IOR:             op_0("ior");                  break;
            case OP_IREM:            op_0("irem");                 break;
            case OP_IRETURN:         op_0("ireturn");              break;
            case OP_ISHL:            op_0("ishl");                 break;
            case OP_ISHR:            op_0("ishr");                 break;
            case OP_ISTORE:          op_1bv("istore");             break;
            case OP_ISTORE_0:        op_0("istore_0");             break;
            case OP_ISTORE_1:        op_0("istore_1");             break;
            case OP_ISTORE_2:        op_0("istore_2");             break;
            case OP_ISTORE_3:        op_0("istore_3");             break;
            case OP_ISUB:            op_0("isub");                 break;
            case OP_IUSHR:           op_0("iushr");                break;
            case OP_IXOR:            op_0("ixor");                 break;
            case OP_JSR:             op_1ws("jsr");                break;
            case OP_JSR_W:           op_1l("jsr_w");               break;
            case OP_L2D:             op_0("l2d");                  break;
            case OP_L2F:             op_0("l2f");                  break;
            case OP_L2I:             op_0("l2i");                  break;
            case OP_LADD:            op_0("ladd");                 break;
            case OP_LALOAD:          op_0("laload");               break;
            case OP_LAND:            op_0("land");                 break;
            case OP_LASTORE:         op_0("lastore");              break;
            case OP_LCMP:            op_0("lcmp");                 break;
            case OP_LCONST_0:        op_0("lconst_0");             break;
            case OP_LCONST_1:        op_0("lconst_1");             break;
            case OP_LDC:             op_1bc("ldc");                break;
            case OP_LDC_W:           op_1wc("ldc_w");              break;
            case OP_LDC2_W:          op_1wc("ldc2_w");             break;
            case OP_LDIV:            op_0("ldiv");                 break;
            case OP_LLOAD:           op_1bv("lload");              break;
            case OP_LLOAD_0:         op_0("lload_0");              break;
            case OP_LLOAD_1:         op_0("lload_1");              break;
            case OP_LLOAD_2:         op_0("lload_2");              break;
            case OP_LLOAD_3:         op_0("lload_3");              break;
            case OP_LMUL:            op_0("lmul");                 break;
            case OP_LNEG:            op_0("lneg");                 break;
            case OP_LOOKUPSWITCH:    op_lookupswitch();            break;
            case OP_LOR:             op_0("lor");                  break;
            case OP_LREM:            op_0("lrem");                 break;
            case OP_LRETURN:         op_0("lreturn");              break;
            case OP_LSHL:            op_0("lshl");                 break;
            case OP_LSHR:            op_0("lshr");                 break;
            case OP_LSTORE:          op_1bv("lstore");             break;
            case OP_LSTORE_0:        op_0("lstore_0");             break;
            case OP_LSTORE_1:        op_0("lstore_1");             break;
            case OP_LSTORE_2:        op_0("lstore_2");             break;
            case OP_LSTORE_3:        op_0("lstore_3");             break;
            case OP_LSUB:            op_0("lsub");                 break;
            case OP_LUSHR:           op_0("lushr");                break;
            case OP_LXOR:            op_0("lxor");                 break;
            case OP_MONITORENTER:    op_0("monitorenter");         break;
            case OP_MONITOREXIT:     op_0("monitorexit");          break;
            case OP_MULTINEWARRAY:   op_2wcb("multinewarray");     break;
            case OP_NEW:             op_1wc("new");                break;
            case OP_NEWARRAY:        op_1b("newarray");            break;
            case OP_NOP:             op_0("nop");                  break;
            case OP_POP:             op_0("pop");                  break;
            case OP_POP2:            op_0("pop2");                 break;
            case OP_PUTFIELD:        op_1wc("putfield");           break;
            case OP_PUTSTATIC:       op_1wc("putstatic");          break;
            case OP_RET:             op_1bv("ret");                break;
            case OP_RETURN:          op_0("return");               break;
            case OP_SALOAD:          op_0("saload");               break;
            case OP_SASTORE:         op_0("sastore");              break;
            case OP_SIPUSH:          op_1ws("sipush");             break;
            case OP_SWAP:            op_0("swap");                 break;
            case OP_TABLESWITCH:     op_tableswitch();             break;
            case OP_WIDE:            op_wide();                    break;
        }
    }
    g_code = saveCode;
    g_pc = savePC;
}

  void
dump_attribute_info(ClassFile *cf, attribute_info *ai)
{
    int i;

    ++g_tab;
    if (g_verbose) {
        char *name = pUtf8(cf, ai->attribute_name_index);
        printf("name: %d (%s)  length: %d\n",
               ai->attribute_name_index, name, ai->attribute_length);
        switch (ai->value->attribute_type) {
            case ATT_UNKNOWN:
                printf("%sunknown attribute type:\n", t());
                dump_hexData(ai->info, ai->attribute_length);
                break;
            case ATT_CONSTANT_VALUE: {
                attribute_value_ConstantValue *value =
                    (attribute_value_ConstantValue *) ai->value;
                printf("%sconstant: %d\n", t(), value->constantvalue_index);
                break;
            }
            case ATT_CODE: {
                attribute_value_Code *value =
                    (attribute_value_Code *) ai->value;
                printf("%smax_stack: %d  max_locals: %d\n", t(),
                       value->max_stack, value->max_locals);
                printf("%s%d code bytes:\n", t(), value->code_length);
                dump_hexData(value->code, value->code_length);
                dump_code(cf, value->code, value->code_length);
                printf("%s%d catches:\n", t(), value->exception_table_length);
                for (i = 0; i < value->exception_table_length; ++i) {
                    printf("%s  [%d]: start: %d  end: %d  handler: %d  type: %d (%s)\n",
                           t(), i, value->exception_table[i].start_pc,
                           value->exception_table[i].end_pc,
                           value->exception_table[i].handler_pc,
                           value->exception_table[i].catch_type,
                           value->exception_table[i].catch_type ?
                               pClassName(cf,
                                          value->exception_table[i].catch_type)
                               : "finally");
                        
                }
                printf("%s%d attributes:\n", t(), value->attributes_count);
                for (i = 0; i < value->attributes_count; ++i) {
                    printf("%s  [%d]: ", t(), i);
                    dump_attribute_info(cf, value->attributes[i]);
                }
                break;
            }
            case ATT_EXCEPTIONS: {
                attribute_value_Exceptions *value =
                    (attribute_value_Exceptions *) ai->value;
                printf("%s%d exceptions:\n", t(), value->number_of_exceptions);
                for (i = 0; i < value->number_of_exceptions; ++i) {
                    printf("%s  [%d]: %d (%s)\n",
                           t(), i, value->exception_index_table[i],
                           pClassName(cf, value->exception_index_table[i]));
                }
                break;
            }
            case ATT_INNER_CLASSES: {
                attribute_value_InnerClasses *value =
                    (attribute_value_InnerClasses *) ai->value;
                printf("%s%d inner classes:\n", t(), value->number_of_classes);
                for (i = 0; i < value->number_of_classes; ++i) {
                    innerclasses_table_entry *entry = &value->classes[i];
                    printf("%s  [%d]: inner %d (%s)  outer: %d (%s)  name: %d (%s)  flags: 0x%04x (%s)\n",
                           t(), i, entry->inner_class_info_index,
                           pClassName(cf, entry->inner_class_info_index),
                           entry->outer_class_info_index,
                           entry->outer_class_info_index ?
                              pClassName(cf, entry->outer_class_info_index) :
                              "<none>",
                           entry->inner_name_index,
                           entry->inner_name_index ?
                               pUtf8(cf, entry->inner_name_index) : "<anon>",
                           entry->inner_class_access_flags,
                           pClassAccessFlags(entry->inner_class_access_flags));
                }
                break;
            }
            case ATT_SYNTHETIC:
                break;
            case ATT_SOURCE_FILE: {
                attribute_value_SourceFile *value =
                    (attribute_value_SourceFile *) ai->value;
                printf("%ssource file: %d (%s)\n", t(),
                       value->sourcefile_index,
                       pUtf8(cf, value->sourcefile_index));
                break;
            }
            case ATT_LINE_NUMBER_TABLE: {
                attribute_value_LineNumberTable *value =
                    (attribute_value_LineNumberTable *) ai->value;
                printf("%s%d line number entries:\n", t(),
                       value->line_number_table_length);
                for (i = 0; i < value->line_number_table_length; ++i) {
                    printf("%s  [%d]: start pc: %d  line number: %d\n",
                           t(), i, value->line_number_table[i].start_pc,
                           value->line_number_table[i].line_number);
                }
                break;
            }
            case ATT_LOCAL_VARIABLE_TABLE: {
                attribute_value_LocalVariableTable *value =
                    (attribute_value_LocalVariableTable *) ai->value;
                printf("%s%d local variables:\n", t(),
                       value->local_variable_table_length);
                for (i = 0; i < value->local_variable_table_length; ++i) {
                    local_variable_table_entry *entry =
                        &value->local_variable_table[i];
                    printf("%s  [%d] start pc: %d  length: %d  name: %d (%s)  descriptor: %d (%s)  index: %d\n",
                           t(), i, entry->start_pc, entry->length,
                           entry->name_index, pUtf8(cf, entry->name_index),
                           entry->descriptor_index,
                           pUtf8(cf, entry->descriptor_index), entry->index);
                }
                break;
            }
            case ATT_DEPRECATED:
                break;
            case ATT_RUNTIME_VISIBLE_ANNOTATIONS: {
                attribute_value_RuntimeVisibleAnnotations *value =
                    (attribute_value_RuntimeVisibleAnnotations *) ai->value;
                printf("%s%d annotations:\n", t(), value->num_annotations);
                for (i = 0; i < value->num_annotations; ++i) {
                    printf("%s[%d]: ", t(), i);
                    dump_annotation(cf, value->annotations[i]);
                }
                break;
            }
            case ATT_ENCLOSING_METHOD: {
                attribute_value_EnclosingMethod *value =
                    (attribute_value_EnclosingMethod *) ai->value;
                printf("%sclass: %d (%s)  method: %d (%s)\n", t(),
                       value->class_index, pClassName(cf, value->class_index),
                       value->method_index, value->method_index ?
                           pNameAndType(cf, value->method_index) : "<none>");
                break;
            }
            case ATT_STACK_MAP_TABLE: {
                dump_hexData(ai->info, ai->attribute_length);
                break;
            }
            case ATT_SIGNATURE: {
                attribute_value_Signature *value =
                    (attribute_value_Signature *) ai->value;
                printf("%ssignature: %d (%s)\n", t(), value->signature_index,
                       pUtf8(cf, value->signature_index));
                break;
            }
            case ATT_LOCAL_VARIABLE_TYPE_TABLE: {
                attribute_value_LocalVariableTypeTable *value =
                    (attribute_value_LocalVariableTypeTable *) ai->value;
                printf("%s%d local variable types:\n", t(),
                       value->local_variable_type_table_length);
                for (i = 0; i < value->local_variable_type_table_length; ++i) {
                    local_variable_type_table_entry *entry =
                        &value->local_variable_type_table[i];
                    printf("%s  [%d] start pc: %d  length: %d  name: %d (%s)  signature: %d (%s)  index: %d\n",
                           t(), i, entry->start_pc, entry->length,
                           entry->name_index, pUtf8(cf, entry->name_index),
                           entry->signature_index,
                           pUtf8(cf, entry->signature_index), entry->index);
                }
                break;
            }
            case ATT_ANNOTATION_DEFAULT: {
                attribute_value_AnnotationDefault *value =
                    (attribute_value_AnnotationDefault *) ai->value;
                printf("%svalue:", t());
                dump_element_value(cf, value->default_value);
                break;
            }
            default:
                printf("%sinvalid attribute type code %d\n", t(),
                       ai->value->attribute_type);
                break;
        }
    } else {
        printf("name: %d  length: %d\n",
               ai->attribute_name_index,
               ai->attribute_length);
    }
    --g_tab;
}

  void
dump_ClassFile(ClassFile *cf)
{
    int i;
    printf("magic: %x  version: %d/%d\n",
           cf->magic, cf->minor_version, cf->major_version);
    if (g_verbose) {
        printf("flags: %04x (%s)\n",
               cf->access_flags, pClassAccessFlags(cf->access_flags));
        printf("thisClass: %d (%s)\n",
               cf->this_class, pClassName(cf, cf->this_class));
        printf("superClass: %d (%s)\n",
               cf->super_class, pClassName(cf, cf->super_class));
    } else {
        printf("flags: %04x  thisClass: %d  superClass: %d\n",
               cf->access_flags, cf->this_class, cf->super_class);
    }
    printf("%d constants:\n", cf->constant_pool_count);
    for (i = 0; i < cf->constant_pool_count; ++i) {
        printf("  [%d]:", i);
        dump_cp_info(cf, cf->constant_pool[i]);
    }
    printf("%d interfaces:\n", cf->interfaces_count);
    for (i = 0; i < cf->interfaces_count; ++i) {
        if (g_verbose) {
            printf("  [%d]: %d (%s)\n",
                   i, cf->interfaces[i], pClassName(cf, cf->interfaces[i]));
        } else {
            printf("  [%d]: %d\n", i, cf->interfaces[i]);
        }
    }
    printf("%d fields:\n", cf->fields_count);
    for (i = 0; i < cf->fields_count; ++i) {
        printf("  [%d]:", i);
        dump_field_info(cf, cf->fields[i]);
    }
    printf("%d methods:\n", cf->methods_count);
    for (i = 0; i < cf->methods_count; ++i) {
        printf("  [%d]:", i);
        dump_method_info(cf, cf->methods[i]);
    }
    printf("%d attributes:\n", cf->attributes_count);
    for (i = 0; i < cf->attributes_count; ++i) {
        printf("  [%d]: ", i);
        dump_attribute_info(cf, cf->attributes[i]);
    }
}

  void
dump_cp_info(ClassFile *cf, cp_info *cp)
{
    if (cp == null) {
        printf("<null>\n");
        return;
    }
    printf(" {%d} ", cp->refCount);
    switch (cp->tag) {
        case CONSTANT_Class: {
            constant_class_info *info = (constant_class_info *) cp;
            if (g_verbose) {
                printf("class:: name: %d (%s)\n",
                       info->name_index, pUtf8(cf, info->name_index));
            } else {
                printf("class:: name: %d\n", info->name_index);
            }
            break;
        }
        case CONSTANT_Fieldref: {
            constant_fieldref_info *info = (constant_fieldref_info *) cp;
            if (g_verbose) {
                printf("fieldref:: class: %d (%s)  nameAndType: %d (%s)\n",
                       info->class_index, pClassName(cf, info->class_index),
                       info->name_and_type_index,
                       pNameAndType(cf, info->name_and_type_index));
            } else {
                printf("fieldref:: class: %d  nameAndType: %d\n",
                       info->class_index, info->name_and_type_index);
            }
            break;
        }
        case CONSTANT_Methodref: {
            constant_methodref_info *info = (constant_methodref_info *) cp;
            if (g_verbose) {
                printf("methodref:: class: %d (%s)  nameAndType: %d (%s)\n",
                       info->class_index, pClassName(cf, info->class_index),
                       info->name_and_type_index,
                       pNameAndType(cf, info->name_and_type_index));
            } else {
                printf("methodref:: class: %d  nameAndType: %d\n",
                       info->class_index, info->name_and_type_index);
            }
            break;
        }
        case CONSTANT_InterfaceMethodref: {
            constant_interfaceMethodref_info *info =
                (constant_interfaceMethodref_info *) cp;
            if (g_verbose) {
                printf("interfaceMethodref:: class: %d (%s)  nameAndType: %d (%s)\n",
                       info->class_index, pClassName(cf, info->class_index),
                       info->name_and_type_index,
                       pNameAndType(cf, info->name_and_type_index));
            } else {
                printf("interfaceMethodref:: class: %d  nameAndType: %d\n",
                       info->class_index, info->name_and_type_index);
            }
            break;
        }
        case CONSTANT_String: {
            constant_string *info = (constant_string *) cp;
            if (g_verbose) {
                printf("string:: index: %d (%s)\n",
                       info->string_index, pUtf8(cf, info->string_index));
            } else {
                printf("string:: index: %d\n", info->string_index);
            }
            break;
        }
        case CONSTANT_Integer: {
            constant_integer *info = (constant_integer *) cp;
            printf("integer:: bytes: %08x (%d)\n", info->bytes,
                   info->bytes);
            break;
        }
        case CONSTANT_Float: {
            constant_float *info = (constant_float *) cp;
            printf("float:: bytes: %08x (%f)\n",
                   info->bytes, (double) *(float *) &info->bytes);
            break;
        }
        case CONSTANT_Long: {
            constant_long *info = (constant_long *) cp;
            long hi = info->high_bytes;
            long value = (hi << 32) | info->low_bytes;
            printf("long:: bytes: %08x%08x (%ld)\n",
                   info->high_bytes, info->low_bytes, value);
            break;
        }
        case CONSTANT_Double: {
            constant_double *info = (constant_double *) cp;
            long hi = info->high_bytes;
            long value = (hi << 32) | info->low_bytes;
            printf("double:: bytes: %08x%08x (%f)\n",
                   info->high_bytes, info->low_bytes,
                   *(double *) &value);
            break;
        }
        case CONSTANT_NameAndType: {
            constant_nameAndType_info *info =
                (constant_nameAndType_info *) cp;
            if (g_verbose) {
                printf("nameAndType:: name: %d (%s)  descriptor: %d (%s)\n",
                       info->name_index, pUtf8(cf, info->name_index),
                       info->descriptor_index,
                       pUtf8(cf, info->descriptor_index));
            } else {
                printf("nameAndType:: name: %d  descriptor: %d\n",
                       info->name_index, info->descriptor_index);
            }
            break;
        }
        case CONSTANT_Utf8: {
            constant_utf8_info *info = (constant_utf8_info *) cp;
            int i;
            printf("utf8:: '");
            for (i = 0; i < info->length; ++i) {
                printf("%c", info->bytes[i]);
            }
            printf("'\n");
            break;
        }
        default:
            fprintf(stderr, "invalid constant pool tag %d", cp->tag);
    }
}

  void
dump_field_info(ClassFile *cf, field_info *fi)
{
    int i;
    if (g_verbose) {
        printf("\n    flags: %04x (%s)\n",
               fi->access_flags, pFieldAccessFlags(fi->access_flags));
        printf("    name: %d (%s)\n",
               fi->name_index, pUtf8(cf, fi->name_index));
        printf("    descriptor: %d (%s)\n",
               fi->descriptor_index, pUtf8(cf, fi->descriptor_index));
        printf("    %d attributes\n", fi->attributes_count);
    } else {
        printf("flags: %04x  name: %d  descriptor: %d,  %d attributes\n",
               fi->access_flags, fi->name_index, fi->descriptor_index,
               fi->attributes_count);
    }
    ++g_tab;
    for (i = 0; i < fi->attributes_count; ++i) {
        printf("      [%d]: ", i);
        dump_attribute_info(cf, fi->attributes[i]);
    }
    --g_tab;
}

  void
dump_method_info(ClassFile *cf, method_info *mi)
{
    int i;
    if (g_verbose) {
        printf(" flags: %04x (%s)\n",
               mi->access_flags, pMethodAccessFlags(mi->access_flags));
        printf("    name: %d (%s)\n",
               mi->name_index, pUtf8(cf, mi->name_index));
        printf("    descriptor: %d (%s)\n",
               mi->descriptor_index, pUtf8(cf, mi->descriptor_index));
        printf("    %d attributes\n", mi->attributes_count);
    } else {
        printf("flags: %04x  name: %d  descriptor: %d,  %d attributes:\n",
               mi->access_flags, mi->name_index, mi->descriptor_index,
               mi->attributes_count);
    }
    ++g_tab;
    for (i = 0; i < mi->attributes_count; ++i) {
        printf("      [%d]: ", i);
        dump_attribute_info(cf, mi->attributes[i]);
    }
    --g_tab;
}

  char *
t()
{
    static char *tabs[] = {
        "    ",
        "        ",
        "            ",
        "                ",
        "                    ",
        "                        ",
        "                            ",
        "                                "
    };
    return tabs[g_tab];
}
