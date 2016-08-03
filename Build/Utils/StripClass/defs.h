#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TYPE_ALLOC(type)          ((type *) malloc(sizeof(type)))
#define TYPE_ALLOC_MULTI(type, n) ((type *) malloc(sizeof(type) * (n)))

typedef int bool;
#define true    1
#define false   0

#define null 0

typedef unsigned int   u4;
typedef unsigned short u2;
typedef unsigned char  u1;

#define BUFLEN 1000

#define CONSTANT_Class                   7
#define CONSTANT_Double                  6
#define CONSTANT_Fieldref                9
#define CONSTANT_Float                   4
#define CONSTANT_Integer                 3
#define CONSTANT_InterfaceMethodref     11
#define CONSTANT_Long                    5
#define CONSTANT_Methodref              10
#define CONSTANT_NameAndType            12
#define CONSTANT_String                  8
#define CONSTANT_Utf8                    1

#define ACC_PUBLIC       0x0001
#define ACC_PRIVATE      0x0002
#define ACC_PROTECTED    0x0004
#define ACC_STATIC       0x0008
#define ACC_FINAL        0x0010
#define ACC_SUPER        0x0020
#define ACC_SYNCHRONIZED 0x0020
#define ACC_VOLATILE     0x0040
#define ACC_TRANSIENT    0x0080
#define ACC_NATIVE       0x0100
#define ACC_INTERFACE    0x0200
#define ACC_ABSTRACT     0x0400
#define ACC_STRICT       0x0800

typedef struct ClassFile ClassFile;
typedef struct annotation annotation;
typedef struct attribute_info attribute_info;
typedef struct attribute_value attribute_value;
typedef struct attribute_value_AnnotationDefault
    attribute_value_AnnotationDefault;
typedef struct attribute_value_Code attribute_value_Code;
typedef struct attribute_value_ConstantValue attribute_value_ConstantValue;
typedef struct attribute_value_Deprecated attribute_value_Deprecated;
typedef struct attribute_value_EnclosingMethod attribute_value_EnclosingMethod;
typedef struct attribute_value_Exceptions attribute_value_Exceptions;
typedef struct attribute_value_InnerClasses attribute_value_InnerClasses;
typedef struct attribute_value_LineNumberTable attribute_value_LineNumberTable;
typedef struct attribute_value_LocalVariableTable
    attribute_value_LocalVariableTable;
typedef struct attribute_value_LocalVariableTypeTable
    attribute_value_LocalVariableTypeTable;
typedef struct attribute_value_RuntimeVisibleAnnotations
    attribute_value_RuntimeVisibleAnnotations;
typedef struct attribute_value_Signature attribute_value_Signature;
typedef struct attribute_value_SourceFile attribute_value_SourceFile;
typedef struct attribute_value_StackMapTable attribute_value_StackMapTable;
typedef struct attribute_value_Synthetic attribute_value_Synthetic;
typedef struct constant_class_info constant_class_info;
typedef struct constant_double constant_double;
typedef struct constant_fieldref_info constant_fieldref_info;
typedef struct constant_float constant_float;
typedef struct constant_integer constant_integer;
typedef struct constant_interfaceMethodref_info
    constant_interfaceMethodref_info;
typedef struct constant_long constant_long;
typedef struct constant_methodref_info constant_methodref_info;
typedef struct constant_nameAndType_info constant_nameAndType_info;
typedef struct constant_string constant_string;
typedef struct constant_utf8_info constant_utf8_info;
typedef struct cp_info cp_info;
typedef struct element_value element_value;
typedef struct element_value_annotation element_value_annotation;
typedef struct element_value_array element_value_array;
typedef struct element_value_class_info element_value_class_info;
typedef struct element_value_const element_value_const;
typedef struct element_value_enum_const element_value_enum_const;
typedef struct element_value_pair element_value_pair;
typedef struct exception_table_entry exception_table_entry;
typedef struct field_info field_info;
typedef struct innerclasses_table_entry innerclasses_table_entry;
typedef struct line_number_table_entry line_number_table_entry;
typedef struct local_variable_table_entry local_variable_table_entry;
typedef struct local_variable_type_table_entry local_variable_type_table_entry;
typedef struct method_info method_info;

struct ClassFile {
    u4 magic;
    u2 minor_version;
    u2 major_version;
    u2 constant_pool_count;
    cp_info **constant_pool;
    u2 access_flags;
    u2 this_class;
    u2 super_class;
    u2 interfaces_count;
    u2 *interfaces;
    u2 fields_count;
    field_info **fields;
    u2 methods_count;
    method_info **methods;
    u2 attributes_count;
    attribute_info **attributes;
    int *constant_pool_map;
};

struct cp_info {
    int refCount;
    u1 tag;            /* CONSTANT_xxxx */
};

struct constant_class_info {
    int refCount;
    u1 tag;            /* CONSTANT_Class */
    u2 name_index;
};

struct constant_fieldref_info {
    int refCount;
    u1 tag;            /* CONSTANT_Fieldref */
    u2 class_index;
    u2 name_and_type_index;
};

struct constant_methodref_info {
    int refCount;
    u1 tag;            /* CONSTANT_Methodref */
    u2 class_index;
    u2 name_and_type_index;
};

struct constant_interfaceMethodref_info {
    int refCount;
    u1 tag;            /* CONSTANT_InterfaceMethodref */
    u2 class_index;
    u2 name_and_type_index;
};

struct constant_string {
    int refCount;
    u1 tag;            /* CONSTANT_String */
    u2 string_index;
};

struct constant_integer {
    int refCount;
    u1 tag;            /* CONSTANT_Integer */
    u4 bytes;
};

struct constant_float {
    int refCount;
    u1 tag;            /* CONSTANT_Float */
    u4 bytes;
};

struct constant_long {
    int refCount;
    u1 tag;            /* CONSTANT_Long */
    u4 high_bytes;
    u4 low_bytes;
};

struct constant_double {
    int refCount;
    u1 tag;            /* CONSTANT_Double */
    u4 high_bytes;
    u4 low_bytes;
};

struct constant_nameAndType_info {
    int refCount;
    u1 tag;            /* CONSTANT_NameAndType */
    u2 name_index;
    u2 descriptor_index;
};

struct constant_utf8_info {
    int refCount;
    u1 tag;            /* CONSTANT_Utf8 */
    u2 length;
    u1 *bytes;
};


struct field_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info **attributes;
};

struct method_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info **attributes;
};

struct attribute_info {
    u2 attribute_name_index;
    u4 attribute_length;
    u1 *info;
    attribute_value *value;
};

#define ATT_UNKNOWN 0
struct attribute_value {
    int attribute_type;
};

#define ATT_CONSTANT_VALUE 1
struct attribute_value_ConstantValue {
    int attribute_type;
    u2 constantvalue_index;
};

struct exception_table_entry {
    u2 start_pc;
    u2 end_pc;
    u2 handler_pc;
    u2 catch_type;
};

#define ATT_CODE 2
struct attribute_value_Code {
    int attribute_type;
    u2 max_stack;
    u2 max_locals;
    u4 code_length;
    u1 *code;
    u2 exception_table_length;
    exception_table_entry *exception_table;
    u2 attributes_count;
    attribute_info **attributes;
};

#define ATT_EXCEPTIONS 3
struct attribute_value_Exceptions {
    int attribute_type;
    u2 number_of_exceptions;
    u2 *exception_index_table;
};

struct innerclasses_table_entry {
    u2 inner_class_info_index;            
    u2 outer_class_info_index;           
    u2 inner_name_index;         
    u2 inner_class_access_flags;         
};

#define ATT_INNER_CLASSES 4
struct attribute_value_InnerClasses {
    int attribute_type;
    u2 number_of_classes;
    innerclasses_table_entry *classes;
};

#define ATT_SYNTHETIC 5
struct attribute_value_Synthetic {
    int attribute_type;
};

#define ATT_SOURCE_FILE 6
struct attribute_value_SourceFile {
    int attribute_type;
    u2 sourcefile_index;
};

struct line_number_table_entry {
    u2 start_pc;          
    u2 line_number;      
};

#define ATT_LINE_NUMBER_TABLE 7
struct attribute_value_LineNumberTable {
    int attribute_type;
    u2 line_number_table_length;
    line_number_table_entry *line_number_table;
};

struct local_variable_table_entry {
    u2 start_pc;
    u2 length;
    u2 name_index;
    u2 descriptor_index;
    u2 index;
};

#define ATT_LOCAL_VARIABLE_TABLE 8
struct attribute_value_LocalVariableTable {
    int attribute_type;
    u2 local_variable_table_length;
    local_variable_table_entry *local_variable_table;
};

#define ATT_DEPRECATED 9
struct attribute_value_Deprecated {
    int attribute_type;
};

struct annotation {
    u2 type_index;
    u2 num_element_value_pairs;
    element_value_pair *element_value_pairs;
};

struct element_value {
    u1 tag;
};

struct element_value_const {
    u1 tag;
    u2 const_value_index;
};

struct element_value_enum_const {
    u1 tag;
    u2 type_name_index;
    u2 const_name_index;
};

struct element_value_class_info {
    u1 tag;
    u2 class_info_index;
};

struct element_value_annotation {
    u1 tag;
    annotation *annotation_value;
};

struct element_value_array {
    u1 tag;
    u2 num_values;
    element_value **values;
};

struct element_value_pair {
    u2 element_name_index;
    element_value *value;
};

#define ATT_RUNTIME_VISIBLE_ANNOTATIONS 10
struct attribute_value_RuntimeVisibleAnnotations {
    int attribute_type;
    u2 num_annotations;
    annotation **annotations;
};

#define ATT_ENCLOSING_METHOD 11
struct attribute_value_EnclosingMethod {
    int attribute_type;
    u2 class_index;
    u2 method_index;
};

#define ATT_STACK_MAP_TABLE 12
struct attribute_value_StackMapTable {
    int attribute_type;
    /* Not even gonna try... */
};

#define ATT_SIGNATURE 13
struct attribute_value_Signature {
    int attribute_type;
    u2 signature_index;
};

struct local_variable_type_table_entry {
    u2 start_pc;
    u2 length;
    u2 name_index;
    u2 signature_index;
    u2 index;
};

#define ATT_LOCAL_VARIABLE_TYPE_TABLE 14
struct attribute_value_LocalVariableTypeTable {
    int attribute_type;
    u2 local_variable_type_table_length;
    local_variable_type_table_entry *local_variable_type_table;
};

#define ATT_ANNOTATION_DEFAULT 15
struct attribute_value_AnnotationDefault {
    int attribute_type;
    element_value *default_value;
};



extern bool g_littleEndian;
extern bool g_verbose;
