void dump_ClassFile(ClassFile *cf);
void dump_cp_info(ClassFile *cf, cp_info *cp);
void dump_field_info(ClassFile *cf, field_info *fi);
void dump_method_info(ClassFile *cf, method_info *mi);
void dump_attribute_info(ClassFile *cf, attribute_info *ai);
void dump_annotation(ClassFile *cf, annotation *ann);
void dump_element_value(ClassFile *cf, element_value *elem);
void dump_hexData(u1 *bytes, int count);
char *t();
