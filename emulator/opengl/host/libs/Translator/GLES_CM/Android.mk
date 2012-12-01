LOCAL_PATH := $(call my-dir)

host_common_SRC_FILES := \
     GLEScmImp.cpp       \
     GLEScmUtils.cpp     \
     GLEScmContext.cpp   \
     GLEScmValidate.cpp

### GLES_CM host implementation (On top of OpenGL) ########################
$(call emugl-begin-host-shared-library,libGLES_CM_translator)

$(call emugl-import,libGLcommon)

LOCAL_SRC_FILES := $(host_common_SRC_FILES)

$(call emugl-end-module)


### GLES_CM host implementation, 64-bit ########################
$(call emugl-begin-host-shared-library,lib64GLES_CM_translator)

$(call emugl-import,lib64GLcommon)

LOCAL_LDLIBS += -m64
LOCAL_SRC_FILES := $(host_common_SRC_FILES)

$(call emugl-export,CFLAGS,-m64)

ifeq ($(HOST_OS),windows)
LOCAL_CC = /usr/bin/amd64-mingw32msvc-gcc 
LOCAL_CXX = /usr/bin/amd64-mingw32msvc-g++
LOCAL_LDLIBS += -L/usr/amd64-mingw32msvc/lib -lmsvcrt
LOCAL_NO_DEFAULT_LD_DIRS = 1
endif

$(call emugl-end-module)
