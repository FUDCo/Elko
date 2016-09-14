# Makefile for the Example game app
#
# Requires GNU Make

# Instructions:
# "make"               - Make example.jar (and all .class files as needed)
# "make clean"         - Remove intermediate files
# "make javadoc"       - Make Javadoc files
# "make dist"          - Build example.jar and export it to the distribution directory

# Java compiler
JAVAC = javac

# Compile flags
#JFLAGS = -g -Xlint:unchecked -Xlint:deprecation
JFLAGS = -g -Xlint:unchecked

# Directory for all built stuff
BUILT_DIR = ../built

# Directory where compiled .class files go
CLA_DIR = $(BUILT_DIR)/classes

# Directory where generated .d files go
DEP_DIR = $(BUILT_DIR)/depend

# Directory where javadoc output goes
JAVADOC_DIR = ../doc/javadoc

# Directory where built binaries go
BIN_DIR = $(BUILT_DIR)/bin

# Directory where built jar files go
JAR_DIR = $(BIN_DIR)

# Directory where Java source files live
JAV_DIR = ../java

# Directory where stuff not built here can be found
DIST_DIR = ../../Distrib

# Directory to obtain 3rd party .jars et al from
EXT_IMPORT_DIR = $(DIST_DIR)/External

# Directory to obtain other Elko .jars et al from
INT_IMPORT_DIR = $(DIST_DIR)/Internal

# Directory into which shippable distribution files should be put
SHIP_DIR = $(DIST_DIR)/Ship

# OS dependent classpath element delimiter
OS = $(shell uname -s)
ifeq ($(OS), Cygwin)
  D = ;
else
  D = :
endif

# External .jar files
EXT_JARS = $(INT_IMPORT_DIR)/elkoserver.jar

ifeq ($(CLASSPATH),)
    CLASSPATH = $(CLA_DIR)$(D)$(EXT_JARS)
else
    CLASSPATH := $(CLASSPATH)$(D)$(CLA_DIR)$(D)$(EXT_JARS)
endif

CLA_ROOT = com/example
PKG_ROOT = com.example

ALL_JSRC = $(shell cd $(JAV_DIR); find com -name '[^_]*.java')
ALL_TSRC = $(shell cd $(JAV_DIR); find com -name '*.java.tmpl')
ALL_JAV = $(ALL_JSRC:%.java=$(JAV_DIR)/%.java)
ALL_CLA = $(ALL_JSRC:%.java=$(CLA_DIR)/%.class) $(ALL_TSRC:%.java.tmpl=$(CLA_DIR)/%.class) 
ALL_DEP = $(ALL_JSRC:%.java=$(DEP_DIR)/%.d) $(ALL_TSRC:%.java.tmpl=$(DEP_DIR)/%.d)

DIRS = $(DEP_DIR) $(CLA_DIR) $(JAR_DIR) $(JAVADOC_DIR)

EXTERNAL_JAVADOC = -link http://java.sun.com/j2se/1.5/docs/api

JAVADOC_OPTS  = -sourcepath $(JAV_DIR) -classpath "$(CLASSPATH)"
JAVADOC_LINKS = ${JAVADOC_OPTS} \
  $(EXTERNAL_JAVADOC) \
  -tag internal:Xa:"Internal:" \
  -windowtitle "Example Game"

JAR_FILES = $(JAR_DIR)/example.jar

CLASS_BUILD_MARKER = $(BUILT_DIR)/classbuildmarker

all: base-build finish

installable:
	@echo

javadoc: do-javadoc finish

dist-terse: all
	cp $(JAR_FILES) $(INT_IMPORT_DIR)

dist: dist-terse
	@echo
	@echo '**************************************************************'
	@echo '*'
	@echo '*   Do not forget to svn commit $(INT_IMPORT_DIR)'
	@echo '*'
	@echo '**************************************************************'

generated: $(DIRS)

finish:

$(JAR_DIR)/example.jar: $(CLASS_BUILD_MARKER)
	rm -rf $@
	cd $(CLA_DIR); jar cf ../$@ \
	    `find $(CLA_ROOT) -name '*.class'`

do-javadoc: generated
	rm -rf $(JAVADOC_DIR)/*
	javadoc -d $(JAVADOC_DIR) $(JAVADOC_LINKS) \
            @javadoc-package-list \
            `cat javadoc-group-defs`
	cp javadoc-package-list $(JAVADOC_DIR)/package-list

$(CLASS_BUILD_MARKER): $(ALL_CLA)
	$(JAVAC) $(JFLAGS) -d $(CLA_DIR) -classpath "$(CLASSPATH)" $(?:$(CLA_DIR)/%.class=$(JAV_DIR)/%.java)
	jdep -i org.elkoserver.example -c $(CLA_DIR) -j $(JAV_DIR) -d $(DEP_DIR) $?
	touch $@

base-build: generated $(JAR_FILES)

$(CLA_DIR):
	mkdir -p $(CLA_DIR)

$(DEP_DIR):
	mkdir -p $(DEP_DIR)

$(JAVADOC_DIR):
	mkdir -p $(JAVADOC_DIR)

$(JAR_DIR):
	mkdir -p $(JAR_DIR)

clean:
	rm -rf $(BUILT_DIR) $(JAVADOC_DIR)

$(CLA_DIR)/%.class: $(JAV_DIR)/%.java
	touchp $@

.PHONY: all javadoc dist generated finish do-javadoc base-build clean installable

-include $(ALL_DEP)
