cmake_minimum_required(VERSION 3.6)

set(FILAMENT_DIR ${FILAMENT_DIST_DIR})

set(DISABLE_FILAMENT_JNI TRUE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../filament-android ${CMAKE_CURRENT_BINARY_DIR}/filament-android)

add_library(filament STATIC IMPORTED)
set_target_properties(filament PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libfilament.a)

add_library(backend STATIC IMPORTED)
set_target_properties(backend PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libbackend.a)

add_library(utils STATIC IMPORTED)
set_target_properties(utils PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libutils.a)

add_library(filaflat STATIC IMPORTED)
set_target_properties(filaflat PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libfilaflat.a)

add_library(image STATIC IMPORTED)
set_target_properties(image PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libimage.a)

add_library(ibl STATIC IMPORTED)
set_target_properties(ibl PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libibl.a)

add_library(geometry STATIC IMPORTED)
set_target_properties(geometry PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libgeometry.a)

add_library(filabridge STATIC IMPORTED)
set_target_properties(filabridge PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libfilabridge.a)

add_library(gltfio STATIC IMPORTED)
set_target_properties(gltfio PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libgltfio_core.a)

add_library(gltfio_resources STATIC IMPORTED)
set_target_properties(gltfio_resources PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libgltfio_resources.a)

add_library(bluevk STATIC IMPORTED)
set_target_properties(bluevk PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libbluevk.a)

add_library(smol-v STATIC IMPORTED)
set_target_properties(smol-v PROPERTIES IMPORTED_LOCATION
        ${FILAMENT_DIR}/lib/${ANDROID_ABI}/libsmol-v.a)

include_directories(${FILAMENT_DIR}/include
        ..
        ../../libs/utils/include)

set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--version-script=${CMAKE_SOURCE_DIR}/libgltfio-jni.map")

add_library(gltfio-jni SHARED
        src/main/cpp/Animator.cpp
        src/main/cpp/AssetLoader.cpp
        src/main/cpp/FilamentAsset.cpp
        src/main/cpp/KtxLoader.cpp
        src/main/cpp/MaterialProvider.cpp
        src/main/cpp/ResourceLoader.cpp

        ../common/CallbackUtils.cpp
        ../common/NioUtils.cpp

        $<TARGET_OBJECTS:filament-jni-obj>
)

set_target_properties(gltfio-jni PROPERTIES LINK_DEPENDS
        ${CMAKE_CURRENT_SOURCE_DIR}/libgltfio-jni.symbols)

# The ordering in the following list is important because CMake does not have dependency information.
target_link_libraries(gltfio-jni
        gltfio
        filament
        backend
        filaflat
        filabridge
        geometry
        image
        ibl
        utils
        log
        GLESv3
        EGL
        android
        jnigraphics
        gltfio_resources
        m
)

if (FILAMENT_SUPPORTS_VULKAN)
    target_link_libraries(gltfio-jni bluevk smol-v)
endif()
