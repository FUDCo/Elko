# Makefile for Elko fake placeholder for ScalableSSL
#
# Requires GNU Make

# Instructions:
# "make"               - Make fakessl.jar (and all .class files as needed)
# "make clean"         - Remove intermediate files
# "make release"       - Build fakessl.jar
# "make installable"   - Build a distribution package for installation
# "make dist"          - Build fakessl.jar and export it to distribution directory

INSTALLABLE_NAME = FakeScalableSSLInstall

# Java compiler
JAVAC = javac

# Compile flags
#JFLAGS = -g -Xlint:unchecked -Xlint:deprecation
JFLAGS = -g -Xlint:unchecked

BUILD_DATE ?= $(shell date '+%e-%b-%Y %T %Z')
BUILD_NAME ?= Release build
VERSION_NAME ?= Elko fake substitute for ScalableSSL 1.0

VERSION_STRING ?= $(VERSION_NAME) $(BUILD_NAME) $(BUILD_DATE)

# Directory for all built stuff
BUILT_DIR = ../built

# Directory where compiled .class files go
CLA_DIR = $(BUILT_DIR)/classes

# Directory where generated .d files go
DEP_DIR = $(BUILT_DIR)/depend

# Directory where binaries go
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

CLA_ROOT = scalablessl
PKG_ROOT = scalablessl

ALL_JSRC = $(shell cd $(JAV_DIR); find . -name '[^_]*.java')
ALL_TSRC = $(shell cd $(JAV_DIR); find . -name '*.java.tmpl')
ALL_JAV = $(ALL_JSRC:%.java=$(JAV_DIR)/%.java)
ALL_CLA = $(ALL_JSRC:%.java=$(CLA_DIR)/%.class) $(ALL_TSRC:%.java.tmpl=$(CLA_DIR)/%.class) 
ALL_DEP = $(ALL_JSRC:%.java=$(DEP_DIR)/%.d) $(ALL_TSRC:%.java.tmpl=$(DEP_DIR)/%.d)

DIRS = $(DEP_DIR) $(CLA_DIR) $(JAR_DIR)

JAR_FILES = $(JAR_DIR)/fakessl.jar

CLASS_BUILD_MARKER = $(BUILT_DIR)/classbuildmarker

all: base-build finish

installable: clean clean-installable base-build tempdir copy-core $(INSTALLABLE_NAME) finish

copy-core:
	cp $(JAR_FILES) $(TEMP_DIR)

INSTALLABLE_NAME_TARGET = $(SHIP_DIR)/$(INSTALLABLE_NAME).zip

$(INSTALLABLE_NAME): $(INSTALLABLE_NAME_TARGET)

clean-installable:
	rm -rf $(INSTALLABLE_NAME_TARGET)

$(INSTALLABLE_NAME_TARGET):
	$(eval TGT := $(shell cd $(dir $(INSTALLABLE_NAME_TARGET)); pwd))
	cd $(TEMP_DIR) ; zip -r $(TGT)/$(notdir $(INSTALLABLE_NAME_TARGET)) .

release: base-build finish

dist: all
	cp $(JAR_FILES) $(INT_IMPORT_DIR)
	@echo
	@echo
	@echo '**************************************************************'
	@echo '*'
	@echo '*   Do not forget to svn commit $(INT_IMPORT_DIR)'
	@echo '*'
	@echo '**************************************************************'

generated: $(DIRS)

finish-package: finish
	rm -rf $(TEMP_DIR)

finish:

$(JAR_DIR)/fakessl.jar: $(CLASS_BUILD_MARKER)
	rm -rf $@
	cd $(CLA_DIR); jar cf ../$@ \
	    `find $(CLA_ROOT) -name '*.class'`

$(CLASS_BUILD_MARKER): $(ALL_CLA)
	$(JAVAC) $(JFLAGS) -d $(CLA_DIR) -classpath "$(CLASSPATH)" $(?:$(CLA_DIR)/%.class=$(JAV_DIR)/%.java)
	jdep -i scalablessl -c $(CLA_DIR) -j $(JAV_DIR) -d $(DEP_DIR) $?
	touch $@

base-build: generated $(JAR_FILES)

$(CLA_DIR):
	mkdir -p $(CLA_DIR)

$(DEP_DIR):
	mkdir -p $(DEP_DIR)

tempdir:
	$(eval TEMP_DIR := $(shell mktemp -d -t ssworkXXXXX))


$(JAR_DIR):
	mkdir -p $(JAR_DIR)

clean:
	rm -rf $(BUILT_DIR)

$(CLA_DIR)/%.class: $(JAV_DIR)/%.java
	touchp $@

.PHONY: all installable copy-core clean-installable release dist generated finish-package finish base-build tempdir clean

-include $(ALL_DEP)
