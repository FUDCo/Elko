void prune_ClassFile(ClassFile *cf);
void prune_code(ClassFile *cf, char returnType, attribute_value_Code *value);
bool prune_attribute_info(ClassFile *cf, attribute_info *ai, method_info *mi);
bool prune_field_info(ClassFile *cf, field_info *fi);
bool prune_method_info(ClassFile *cf, method_info *mi);
