# Makefile for ZeroMQ communications support for Elko
#
# Requires GNU Make

# Instructions:
# "make"               - Make elkozmq.jar (and all .class files as needed)
# "make clean"         - Remove intermediate files
# "make javadoc"       - Make Javadoc files
# "make release"       - Build elkozmq.jar and Javadoc files
# "make sdk"           - Build a distribution package for developers (includes docs)
# "make installable"   - Build a distribution package for installation
# "make dist"          - Build elkozmq.jar and export it to the distribution directory

SDK_NAME = ElkoZMQSDK
INSTALLABLE_NAME = ElkoZMQInstall

# Java compiler
JAVAC = javac

# Compile flags
#JFLAGS = -g -Xlint:unchecked -Xlint:deprecation
JFLAGS = -g -Xlint:unchecked

BUILD_DATE ?= $(shell date '+%e-%b-%Y %T %Z')
BUILD_NAME ?= Release build
VERSION_NAME ?= Elko ZeroMQ Extension 1.0

VERSION_STRING ?= $(VERSION_NAME) $(BUILD_NAME) $(BUILD_DATE)

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
EXT_JARS = $(INT_IMPORT_DIR)/elkoserver.jar$(D)$(EXT_IMPORT_DIR)/zmq.jar

ifeq ($(CLASSPATH),)
    CLASSPATH = $(CLA_DIR)$(D)$(EXT_JARS)
else
    CLASSPATH := $(CLASSPATH)$(D)$(CLA_DIR)$(D)$(EXT_JARS)
endif

CLA_ROOT = org/elkoserver
PKG_ROOT = org.elkoserver

ALL_JSRC = $(shell cd $(JAV_DIR); find org -name '[^_]*.java')
ALL_TSRC = $(shell cd $(JAV_DIR); find org -name '*.java.tmpl')
ALL_JAV = $(ALL_JSRC:%.java=$(JAV_DIR)/%.java)
ALL_CLA = $(ALL_JSRC:%.java=$(CLA_DIR)/%.class) $(ALL_TSRC:%.java.tmpl=$(CLA_DIR)/%.class) 
ALL_DEP = $(ALL_JSRC:%.java=$(DEP_DIR)/%.d) $(ALL_TSRC:%.java.tmpl=$(DEP_DIR)/%.d)

DIRS = $(DEP_DIR) $(CLA_DIR) $(JAR_DIR) $(JAVADOC_DIR)

EXTERNAL_JAVADOC = -link http://java.sun.com/j2se/1.5/docs/api

JAVADOC_OPTS  = -sourcepath $(JAV_DIR) -classpath "$(CLASSPATH)"
JAVADOC_LINKS = ${JAVADOC_OPTS} \
  $(EXTERNAL_JAVADOC) \
  -tag internal:Xa:"Internal:" \
  -windowtitle "Elko ZeroMQ Extensions"

JAR_FILES = $(JAR_DIR)/elkozmq.jar

CLASS_BUILD_MARKER = $(BUILT_DIR)/classbuildmarker

all: base-build finish

base-package: clean base-build tempdir copy-core

sdk: clean-sdk base-package do-javadoc copy-docs $(SDK_NAME) finish-package

installable: clean-installable base-package copy-core $(INSTALLABLE_NAME) finish-package

copy-core:
	cp $(JAR_FILES) $(TEMP_DIR)

copy-docs:
	cp -rf $(JAVADOC_DIR) $(TEMP_DIR)

INSTALLABLE_NAME_TARGET = $(SHIP_DIR)/$(INSTALLABLE_NAME).zip

SDK_NAME_TARGET = $(SHIP_DIR)/$(SDK_NAME).zip

$(INSTALLABLE_NAME): $(INSTALLABLE_NAME_TARGET)

$(SDK_NAME): $(SDK_NAME_TARGET)

clean-installable:
	rm -rf $(INSTALLABLE_NAME_TARGET)

$(INSTALLABLE_NAME_TARGET):
	$(eval TGT := $(shell cd $(dir $(INSTALLABLE_NAME_TARGET)); pwd))
	cd $(TEMP_DIR) ; zip -r $(TGT)/$(notdir $(INSTALLABLE_NAME_TARGET)) .

clean-sdk:
	rm -rf $(SDK_NAME_TARGET)

$(SDK_NAME_TARGET):
	$(eval TGT := $(shell cd $(dir $(SDK_NAME_TARGET)); pwd))
	cd $(TEMP_DIR) ; zip -r $(TGT)/$(notdir $(SDK_NAME_TARGET)) .

javadoc: do-javadoc finish

release: base-build do-javadoc finish

dist: all
	cp $(JAR_FILES) $(INT_IMPORT_DIR)
	@echo
	@echo
	@echo '**************************************************************'
	@echo '*'
	@echo '*   Do not forget to commit changes to $(INT_IMPORT_DIR)'
	@echo '*'
	@echo '**************************************************************'

generated: $(DIRS)

finish-package: finish
	rm -rf $(TEMP_DIR)

finish:

$(JAR_DIR)/elkozmq.jar: $(CLASS_BUILD_MARKER)
	rm -rf $@
	cd $(CLA_DIR); jar cf ../$@ \
	    `find $(CLA_ROOT)/foundation -name '*.class'`

do-javadoc: generated
	rm -rf $(JAVADOC_DIR)/*
	javadoc -d $(JAVADOC_DIR) $(JAVADOC_LINKS) \
            @javadoc-package-list \
            -group "Elko Foundation Packages" \
                "$(PKG_ROOT).foundation.net.zmq"
	cp javadoc-package-list $(JAVADOC_DIR)/package-list

$(CLASS_BUILD_MARKER): $(ALL_CLA)
	$(JAVAC) $(JFLAGS) -d $(CLA_DIR) -classpath "$(CLASSPATH)" $(?:$(CLA_DIR)/%.class=$(JAV_DIR)/%.java)
	jdep -i org.elkoserver.foundation.net.zmq -c $(CLA_DIR) -j $(JAV_DIR) -d $(DEP_DIR) $?
	touch $@

base-build: generated $(JAR_FILES)

$(CLA_DIR):
	mkdir -p $(CLA_DIR)

$(DEP_DIR):
	mkdir -p $(DEP_DIR)

tempdir:
	$(eval TEMP_DIR := $(shell mktemp -d -t ssworkXXXXX))

$(JAVADOC_DIR):
	mkdir -p $(JAVADOC_DIR)

$(JAR_DIR):
	mkdir -p $(JAR_DIR)

clean:
	rm -rf $(BUILT_DIR) $(JAVADOC_DIR)

$(CLA_DIR)/%.class: $(JAV_DIR)/%.java
	touchp $@

.PHONY: all base-package sdk installable copy-core copy-docs clean-installable clean-sdk javadoc release dist generated finish-package finish do-javadoc base-build tempdir clean

-include $(ALL_DEP)
