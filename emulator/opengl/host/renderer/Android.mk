LOCAL_PATH:=$(call my-dir)

# host renderer process ###########################
$(call emugl-begin-host-executable,emulator_renderer)
$(call emugl-import,libOpenglRender)
LOCAL_SRC_FILES := main.cpp
LOCAL_CFLAGS    += -O0 -g

#ifeq ($(HOST_OS),windows)
#LOCAL_LDLIBS += -lws2_32
#endif

$(call emugl-end-module)

# host renderer process ###########################
$(call emugl-begin-host-executable,emulator64_renderer)
$(call emugl-import,lib64OpenglRender)
LOCAL_SRC_FILES := main.cpp
LOCAL_CFLAGS    += -O0 -g
LOCAL_LDLIBS += -m64

#ifeq ($(HOST_OS),windows)
#LOCAL_LDLIBS += -lws2_32
#endif

$(call emugl-end-module)
