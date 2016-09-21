# Makefile Elko
#
# Requires GNU Make

# Instructions:
# "make"               - Make elkoserver.jar (and all .class files as needed)
# "make clean"         - Remove intermediate files
# "make javadoc"       - Make Javadoc files
# "make release"       - Build elkoserver.jar and Javadoc files
# "make sdk"           - Build a distribution package for developers (includes docs and extras)
# "make installable"   - Build a distribution package for installation
# "make dist"          - Build elkoserver.jar and export it to the distribution directory

SDK_NAME = ElkoServerSDK
INSTALLABLE_NAME = ElkoServerInstall

# Java compiler
JAVAC = javac

# Command for removing stuff
RM = /bin/rm -rf

# Compile flags
#JFLAGS = -g -Xlint:unchecked -Xlint:deprecation
JFLAGS = -g -Xlint:unchecked

BUILD_DATE ?= $(shell date '+%e-%b-%Y %T %Z')
BUILD_NAME ?= Release build
VERSION_NAME ?= Elko 2.0.1

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
EXT_JARS = $(EXT_IMPORT_DIR)/mongo.jar$(D)$(EXT_IMPORT_DIR)/ScalableSSL.jar$(D)$(EXT_IMPORT_DIR)/commons-codec.jar$

ifeq ($(CLASSPATH),)
    CLASSPATH = $(CLA_DIR)$(D)$(EXT_JARS)
else
    CLASSPATH := $(CLASSPATH)$(D)$(CLA_DIR)$(D)$(EXT_JARS)
endif

CLA_ROOT = org/elkoserver
PKG_ROOT = org.elkoserver

VERSION_PACKAGE_SRC_DIR = $(JAV_DIR)/$(CLA_ROOT)/foundation/server

VERSION_DECL = $(VERSION_PACKAGE_SRC_DIR)/BuildVersion.java

ALL_JSRC = $(shell cd $(JAV_DIR); find org scalablessl -name '[^_]*.java')
ALL_TSRC = $(shell cd $(JAV_DIR); find org scalablessl -name '*.java.tmpl')
ALL_JAV = $(ALL_JSRC:%.java=$(JAV_DIR)/%.java)
ALL_CLA = $(ALL_JSRC:%.java=$(CLA_DIR)/%.class) $(ALL_TSRC:%.java.tmpl=$(CLA_DIR)/%.class) 
ALL_DEP = $(ALL_JSRC:%.java=$(DEP_DIR)/%.d) $(ALL_TSRC:%.java.tmpl=$(DEP_DIR)/%.d)

DIRS = $(DEP_DIR) $(CLA_DIR) $(JAR_DIR) $(JAVADOC_DIR)

EXTERNAL_JAVADOC = -link http://docs.oracle.com/javase/6/docs/api

JAVADOC_OPTS  = -sourcepath $(JAV_DIR) -classpath "$(CLASSPATH)"
JAVADOC_LINKS = $(JAVADOC_OPTS) \
  $(EXTERNAL_JAVADOC) \
  -tag internal:Xa:"Internal:" \
  -windowtitle "Elko Server Framework"

JAR_FILES = $(JAR_DIR)/elkoserver.jar $(JAR_DIR)/fakessl.jar

CLASS_BUILD_MARKER = $(BUILT_DIR)/classbuildmarker

all: base-build finish

base-package: clean base-build tempdir copy-core

sdk: clean-sdk base-package do-javadoc copy-docs copy-extras $(SDK_NAME) finish-package

installable: clean-installable base-package $(INSTALLABLE_NAME) finish-package

DB_FILES = ../run/db/mongo.jar \
           ../run/db/mongohelper.js \
           ../run/db/READ.ME \
           ../run/db/dump.clean.external.tar.gz

copy-core:
	cp $(JAR_FILES) $(TEMP_DIR)
	#cp ../run/start.* $(TEMP_DIR)
	#cp ../run/*.example $(TEMP_DIR)
	#cp ../run/setenvvars $(TEMP_DIR)
	echo "echo '#Replace this file with your customized version of setlocvars.example'; return 1" >$(TEMP_DIR)/setlocvars
	mkdir -p $(TEMP_DIR)/db
	#cp -r $(DB_FILES) $(TEMP_DIR)/db

copy-docs:
	cp -rf $(JAVADOC_DIR) $(TEMP_DIR)

TEST_FILES = ../test/READ.ME \
             ../test/helloServer.js \
             ../test/pingServer.js\
             ../test/moab.js \
             ../test/names.js \
             ../test/trace.js \
             ../test/users.js \
             ../test/shutdown.js \
             ../test/createAccount.js \
             ../run/cribsheet*
              
WEB_FILES = ../web/READ.ME \
            ../web/chat.html \
            ../web/chatjq.html \
            ../web/connection.html \
            ../web/connection.js \
            ../web/connectionjq.html \
            ../web/connectionjq.js \
            ../web/json.js \
            ../web/session.js \
            ../web/trace.js
            
copy-extras:
	mkdir -p $(TEMP_DIR)/test
	mkdir -p $(TEMP_DIR)/web
	cp $(TEST_FILES) $(TEMP_DIR)/test
	cp $(WEB_FILES)  $(TEMP_DIR)/web

INSTALLABLE_NAME_TARGET = $(SHIP_DIR)/$(INSTALLABLE_NAME).zip

SDK_NAME_TARGET = $(SHIP_DIR)/$(SDK_NAME).zip

$(INSTALLABLE_NAME): $(INSTALLABLE_NAME_TARGET)

$(SDK_NAME): $(SDK_NAME_TARGET)

clean-installable:
	$(RM) $(INSTALLABLE_NAME_TARGET) 

$(INSTALLABLE_NAME_TARGET):
	$(eval TGT := $(shell cd $(dir $(INSTALLABLE_NAME_TARGET)); pwd))
	cd $(TEMP_DIR) ; zip -r $(TGT)/$(notdir $(INSTALLABLE_NAME_TARGET)) .

clean-sdk:
	$(RM) $(SDK_NAME_TARGET) 

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
	@echo '*   Do not forget to svn commit $(INT_IMPORT_DIR)'
	@echo '*'
	@echo '**************************************************************'

generated: $(DIRS) $(VERSION_DECL)

finish-package: finish
	$(RM) $(TEMP_DIR)

finish:
	$(RM) $(VERSION_DECL)

$(JAR_DIR)/elkoserver.jar: $(CLASS_BUILD_MARKER)
	$(RM) $@
	cd $(CLA_DIR); jar cf ../$@ \
	    `find $(CLA_ROOT)/foundation -name '*.class'` \
	    `find $(CLA_ROOT)/json -name '*.class'` \
	    `find $(CLA_ROOT)/objdb -name '*.class'` \
	    `find $(CLA_ROOT)/server -name '*.class'` \
	    `find $(CLA_ROOT)/util -name '*.class'` \
	    `find scalablessl -name '*.class'`

$(JAR_DIR)/fakessl.jar: $(CLASS_BUILD_MARKER)
	$(RM) $@
	cd $(CLA_DIR); jar cf ../$@ \
	    `find scalablessl -name '*.class'`

do-javadoc: generated
	$(RM) $(JAVADOC_DIR)
	javadoc -d $(JAVADOC_DIR) $(JAVADOC_LINKS) \
            @javadoc-package-list \
            -group "Elko Servers" \
                "$(PKG_ROOT).server.broker:$(PKG_ROOT).server.context:$(PKG_ROOT).server.director:$(PKG_ROOT).server.gatekeeper:$(PKG_ROOT).server.presence:$(PKG_ROOT).server.repository:$(PKG_ROOT).server.workshop" \
            -group "Elko Application Support Packages" \
                "$(PKG_ROOT).server.context.*:$(PKG_ROOT).server.workshop.*" \
            -group "Elko Foundation Packages" \
                "$(PKG_ROOT).foundation.*" \
            -group "Implementation and Utility Packages" \
                "$(PKG_ROOT).json:$(PKG_ROOT).objdb*:$(PKG_ROOT).util*:$(PKG_ROOT).server.gatekeeper.*"
	cp javadoc-package-list $(JAVADOC_DIR)/package-list

$(CLASS_BUILD_MARKER): $(ALL_CLA)
	$(JAVAC) $(JFLAGS) -d $(CLA_DIR) -classpath "$(CLASSPATH)" $(?:$(CLA_DIR)/%.class=$(JAV_DIR)/%.java)
	jdep -i org.elkoserver -i scalablessl -c $(CLA_DIR) -j $(JAV_DIR) -d $(DEP_DIR) $?
	touch $@

base-build: generated $(JAR_FILES)

$(VERSION_DECL): $(VERSION_PACKAGE_SRC_DIR)/BuildVersion.java.tmpl
	sed -e "s/@BuildVersion@/$(VERSION_STRING)/" < $? > $@

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
	$(RM) $(BUILT_DIR) $(JAVADOC_DIR) $(VERSION_DECL)

$(CLA_DIR)/%.class: $(JAV_DIR)/%.java
	touchp $@

.PHONY: all base-package sdk installable copy-core copy-docs copy-extras clean-installable clean-sdk javadoc release dist generated finish-package finish do-javadoc base-build tempdir clean

-include $(ALL_DEP)
