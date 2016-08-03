int map_cp(ClassFile *cf, int idx);
bool gc_ClassFile(ClassFile *cf);
void gc_field_info(ClassFile *cf, field_info *fi);
void gc_method_info(ClassFile *cf, method_info *mi);
void gc_cp_info(ClassFile *cf, cp_info *cp);
void gc_attribute_info(ClassFile *cf, attribute_info *ai);
void gc_annotation(ClassFile *cf, annotation *ann);
void gc_element_value(ClassFile *cf, element_value *elem);
