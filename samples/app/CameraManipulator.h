/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TNT_FILAMENT_SAMPLE_CAMERA_MANIPULATOR_H
#define TNT_FILAMENT_SAMPLE_CAMERA_MANIPULATOR_H

#include <math/vec2.h>
#include <math/vec3.h>
#include <math/vec4.h>
#include <math/mat4.h>

class CameraManipulator {
public:
    CameraManipulator();
    CameraManipulator(size_t width, size_t height);

    void setViewport(size_t w, size_t h);

    void lookAt(const filament::math::double3& eye, const filament::math::double3& at);
    void track(const filament::math::double2& delta);
    void dolly(double delta, double dollySpeed = 5.0);
    void rotate(const filament::math::double2& delta, double rotateSpeed = 7.0);

    filament::math::mat4f getCameraTransform() const;

private:
    filament::math::double3 mRotation;
    filament::math::double3 mTranslation;

    double mCenterOfInterest = 10.0;
    double mFovx = 65.0;
    size_t mWidth;
    size_t mHeight;
};

#endif // TNT_FILAMENT_SAMPLE_CAMERA_MANIPULATOR_H
