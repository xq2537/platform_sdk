LOCAL_PATH := $(call my-dir)

host_OS_SRCS :=
host_common_LDLIBS :=

ifeq ($(HOST_OS),linux)
    host_OS_SRCS = NativeLinuxSubWindow.cpp
    host_common_LDLIBS += -lX11
endif

ifeq ($(HOST_OS),darwin)
    host_OS_SRCS = NativeMacSubWindow.m
    host_common_LDLIBS += -Wl,-framework,AppKit
endif

ifeq ($(HOST_OS),windows)
    host_OS_SRCS = NativeWindowsSubWindow.cpp
    host_common_LDLIBS += -Wl,--out-implib,--no-undefined,--enable-runtime-pseudo-reloc
endif

host_common_SRC_FILES := \
    $(host_OS_SRCS) \
    render_api.cpp \
    ColorBuffer.cpp \
    EGLDispatch.cpp \
    FBConfig.cpp \
    FrameBuffer.cpp \
    GLDispatch.cpp \
    GL2Dispatch.cpp \
    RenderContext.cpp \
    WindowSurface.cpp \
    RenderControl.cpp \
    ThreadInfo.cpp \
    RenderThread.cpp \
    ReadBuffer.cpp \
    RenderServer.cpp \
    AndroVM.cpp

host_common_CFLAGS :=

#For gl debbuging
#host_common_CFLAGS += -DCHECK_GL_ERROR


### host libOpenglRender #################################################
$(call emugl-begin-host-shared-library,libOpenglRender)

$(call emugl-import,libGLESv1_dec libGLESv2_dec lib_renderControl_dec libOpenglCodecCommon libOpenglOsUtils)

LOCAL_LDLIBS += $(host_common_LDLIBS)

LOCAL_SRC_FILES := $(host_common_SRC_FILES)
$(call emugl-export,C_INCLUDES,$(EMUGL_PATH)/host/include)
$(call emugl-export,C_INCLUDES,$(LOCAL_PATH))

# use Translator's egl/gles headers
LOCAL_C_INCLUDES += $(EMUGL_PATH)/host/libs/Translator/include

LOCAL_STATIC_LIBRARIES += libutils liblog

$(call emugl-export,CFLAGS,$(host_common_CFLAGS))

$(call emugl-end-module)


### host libOpenglRender, 64-bit #########################################
$(call emugl-begin-host-shared-library,lib64OpenglRender)

$(call emugl-import,lib64GLESv1_dec lib64GLESv2_dec lib64_renderControl_dec lib64OpenglCodecCommon lib64OpenglOsUtils)

#LOCAL_LDFLAGS += -m64  # adding -m64 here doesn't work, because it somehow appear BEFORE -m32 in command-line.
LOCAL_LDLIBS += $(host_common_LDLIBS) -m64  # Put -m64 it in LOCAL_LDLIBS instead.

LOCAL_SRC_FILES := $(host_common_SRC_FILES)
$(call emugl-export,C_INCLUDES,$(EMUGL_PATH)/host/include)
$(call emugl-export,C_INCLUDES,$(LOCAL_PATH))

# use Translator's egl/gles headers
LOCAL_C_INCLUDES += $(EMUGL_PATH)/host/libs/Translator/include

LOCAL_STATIC_LIBRARIES += lib64utils lib64log

$(call emugl-export,CFLAGS,$(host_commont_CFLAGS) -m64)

ifeq ($(HOST_OS),windows)
LOCAL_CC = /usr/bin/amd64-mingw32msvc-gcc 
LOCAL_CXX = /usr/bin/amd64-mingw32msvc-g++
LOCAL_LDLIBS += -L/usr/amd64-mingw32msvc/lib -lmsvcrt
LOCAL_NO_DEFAULT_LD_DIRS = 1
endif

$(call emugl-end-module)

### host libOpenglRenderStatic #################################################
$(call emugl-begin-host-static-library,libOpenglRenderStatic)

$(call emugl-import,libGLESv1_dec libGLESv2_dec lib_renderControl_dec libOpenglCodecCommon libOpenglOsUtils)

LOCAL_LDLIBS += $(host_common_LDLIBS)

LOCAL_SRC_FILES := $(host_common_SRC_FILES)
$(call emugl-export,C_INCLUDES,$(EMUGL_PATH)/host/include)
$(call emugl-export,C_INCLUDES,$(LOCAL_PATH))

# use Translator's egl/gles headers
LOCAL_C_INCLUDES += $(EMUGL_PATH)/host/libs/Translator/include

LOCAL_STATIC_LIBRARIES += libutils liblog

$(call emugl-export,CFLAGS,$(host_common_CFLAGS))

$(call emugl-end-module)


