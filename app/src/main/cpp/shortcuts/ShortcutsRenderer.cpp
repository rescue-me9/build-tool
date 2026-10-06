#include "ShortcutsRenderer.h"
#include "ShortcutsRuntime.h"
#include "../build_import/BuildProjectionRenderer.h"
#include "../main.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <deque>
#include <vector>

#define LOG_TAG "Infinitecz_ShortcutsRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace shortcuts {

static const char* kVertexSource = R"GLSL(#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos;
uniform mat4 uMvp;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
}
)GLSL";

static const char* kFragmentSource = R"GLSL(#version 300 es
precision mediump float;
uniform vec4 uColor;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = uColor;
}
)GLSL";

static uint32_t compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    if (!s) return 0;
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (ok == GL_TRUE) return s;
    char log[512] = {};
    GLsizei len = 0;
    glGetShaderInfoLog(s, sizeof(log), &len, log);
    LOGE("shader compile failed: %.*s", len, log);
    glDeleteShader(s);
    return 0;
}

ShortcutsRenderer& ShortcutsRenderer::instance() {
    static ShortcutsRenderer inst;
    return inst;
}

bool ShortcutsRenderer::initializeGl() {
    if (gl_ready_ && program_ && vao_ && vbo_) return true;
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return false;

    if (!program_) {
        GLuint vs = compileShader(GL_VERTEX_SHADER, kVertexSource);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFragmentSource);
        if (!vs || !fs) {
            if (vs) glDeleteShader(vs);
            if (fs) glDeleteShader(fs);
            return false;
        }
        GLuint prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint linked = 0;
        glGetProgramiv(prog, GL_LINK_STATUS, &linked);
        if (linked != GL_TRUE) {
            char log[512] = {};
            GLsizei len = 0;
            glGetProgramInfoLog(prog, sizeof(log), &len, log);
            LOGE("program link failed: %.*s", len, log);
            glDeleteProgram(prog);
            return false;
        }
        program_ = prog;
        mvp_loc_ = glGetUniformLocation(prog, "uMvp");
        color_loc_ = glGetUniformLocation(prog, "uColor");
    }

    if (!vao_) {
        glGenVertexArrays(1, &vao_);
        glGenBuffers(1, &vbo_);
        if (!vao_ || !vbo_) return false;
        glBindVertexArray(vao_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, 65536 * sizeof(float), nullptr, GL_DYNAMIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, nullptr);
        glBindVertexArray(0);
    }

    gl_ready_ = true;
    return true;
}

void ShortcutsRenderer::drawLines(const float* vertices, int32_t count, float r, float g, float b, float a) {
    if (!vertices || count <= 0) return;
    GLint max_vertices = 0;
    glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &max_vertices);
    (void)max_vertices;
    GLint max_buffer = 65536 / 3;
    if (count > max_buffer) count = max_buffer;

    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferSubData(GL_ARRAY_BUFFER, 0, count * 3 * sizeof(float), vertices);
    glUniform4f(color_loc_, r, g, b, a);
    glDrawArrays(GL_LINES, 0, count);
    glBindVertexArray(0);
}

void ShortcutsRenderer::render() {
    ShortcutsRuntime& rt = ShortcutsRuntime::instance();

    bool any_render = false;
    any_render = rt.isFeatureEnabled(FeatureId::Trail) || any_render;
    any_render = rt.isFeatureEnabled(FeatureId::HomeBeacon) || any_render;
    any_render = rt.isFeatureEnabled(FeatureId::ChunkBorder) || any_render;
    any_render = rt.isFeatureEnabled(FeatureId::SafeZone) || any_render;
    if (!any_render) return;

    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;
    if (!initializeGl()) return;

    float mvp[16] = {};
    if (!build_import::GetLastRenderMvp(mvp)) return;

    PlayerInfo info;
    if (!rt.getPlayerInfo(&info)) return;

    std::deque<std::array<float, 3>> trail_points;
    int32_t hx = 0, hy = 0, hz = 0;
    bool home_valid = false;
    int32_t sx0 = 0, sy0 = 0, sz0 = 0, sx1 = 0, sy1 = 0, sz1 = 0;
    bool safe_valid = false;
    rt.getRenderData(trail_points, hx, hy, hz, home_valid, sx0, sy0, sz0, sx1, sy1, sz1, safe_valid);

    GLint prev_program = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &prev_program);
    GLint prev_vao = 0;
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &prev_vao);
    GLboolean prev_blend = glIsEnabled(GL_BLEND);
    GLboolean prev_depth = glIsEnabled(GL_DEPTH_TEST);
    GLint prev_depth_func = 0;
    glGetIntegerv(GL_DEPTH_FUNC, &prev_depth_func);
    GLboolean prev_cull = glIsEnabled(GL_CULL_FACE);

    glUseProgram(program_);
    glUniformMatrix4fv(mvp_loc_, 1, GL_FALSE, mvp);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_CULL_FACE);
    glDepthFunc(GL_LEQUAL);
    glEnable(GL_DEPTH_TEST);

    std::vector<float> verts;

    if (rt.isFeatureEnabled(FeatureId::Trail) && trail_points.size() >= 2) {
        auto& cfg = ShortcutsRuntime::instance();
        float r = cfg.getFeatureFloatParam(FeatureId::Trail, 0);
        float g = cfg.getFeatureFloatParam(FeatureId::Trail, 1);
        float b = cfg.getFeatureFloatParam(FeatureId::Trail, 2);
        float a = cfg.getFeatureFloatParam(FeatureId::Trail, 3);
        verts.clear();
        for (size_t i = 1; i < trail_points.size(); ++i) {
            verts.push_back(trail_points[i - 1][0]);
            verts.push_back(trail_points[i - 1][1]);
            verts.push_back(trail_points[i - 1][2]);
            verts.push_back(trail_points[i][0]);
            verts.push_back(trail_points[i][1]);
            verts.push_back(trail_points[i][2]);
        }
        drawLines(verts.data(), static_cast<int32_t>(verts.size() / 3), r, g, b, a);
    }

    if (rt.isFeatureEnabled(FeatureId::HomeBeacon) && home_valid) {
        float r = rt.getFeatureFloatParam(FeatureId::HomeBeacon, 0);
        float g = rt.getFeatureFloatParam(FeatureId::HomeBeacon, 1);
        float b = rt.getFeatureFloatParam(FeatureId::HomeBeacon, 2);
        float a = rt.getFeatureFloatParam(FeatureId::HomeBeacon, 3);
        float px = static_cast<float>(info.block_x) + 0.5f;
        float py = static_cast<float>(info.block_y) + 1.0f;
        float pz = static_cast<float>(info.block_z) + 0.5f;
        verts.clear();
        verts.push_back(px); verts.push_back(py); verts.push_back(pz);
        verts.push_back(static_cast<float>(hx) + 0.5f);
        verts.push_back(static_cast<float>(hy) + 1.0f);
        verts.push_back(static_cast<float>(hz) + 0.5f);
        drawLines(verts.data(), 2, r, g, b, a);
    }

    if (rt.isFeatureEnabled(FeatureId::ChunkBorder)) {
        float r = rt.getFeatureFloatParam(FeatureId::ChunkBorder, 0);
        float g = rt.getFeatureFloatParam(FeatureId::ChunkBorder, 1);
        float b = rt.getFeatureFloatParam(FeatureId::ChunkBorder, 2);
        float a = rt.getFeatureFloatParam(FeatureId::ChunkBorder, 3);
        int32_t range = rt.getFeatureIntParam(FeatureId::ChunkBorder, 0);
        if (range < 1) range = 1;
        if (range > 16) range = 16;
        int32_t pcx = info.block_x / 16;
        int32_t pcz = info.block_z / 16;
        if (info.block_x < 0 && info.block_x % 16 != 0) --pcx;
        if (info.block_z < 0 && info.block_z % 16 != 0) --pcz;
        int32_t y0 = info.block_y;
        verts.clear();
        for (int32_t cx = pcx - range; cx <= pcx + range + 1; ++cx) {
            float x = static_cast<float>(cx * 16);
            verts.push_back(x); verts.push_back(static_cast<float>(y0));
            verts.push_back(static_cast<float>((pcz - range) * 16));
            verts.push_back(x); verts.push_back(static_cast<float>(y0));
            verts.push_back(static_cast<float>((pcz + range + 1) * 16));
        }
        for (int32_t cz = pcz - range; cz <= pcz + range + 1; ++cz) {
            float z = static_cast<float>(cz * 16);
            verts.push_back(static_cast<float>((pcx - range) * 16));
            verts.push_back(static_cast<float>(y0));
            verts.push_back(z);
            verts.push_back(static_cast<float>((pcx + range + 1) * 16));
            verts.push_back(static_cast<float>(y0));
            verts.push_back(z);
        }
        drawLines(verts.data(), static_cast<int32_t>(verts.size() / 3), r, g, b, a);
    }

    if (rt.isFeatureEnabled(FeatureId::SafeZone) && safe_valid) {
        float r = rt.getFeatureFloatParam(FeatureId::SafeZone, 0);
        float g = rt.getFeatureFloatParam(FeatureId::SafeZone, 1);
        float b = rt.getFeatureFloatParam(FeatureId::SafeZone, 2);
        float a = rt.getFeatureFloatParam(FeatureId::SafeZone, 3);
        float x0 = static_cast<float>(sx0), y0 = static_cast<float>(sy0), z0 = static_cast<float>(sz0);
        float x1 = static_cast<float>(sx1), y1 = static_cast<float>(sy1), z1 = static_cast<float>(sz1);
        verts.clear();
        auto addLine = [&](float ax, float ay, float az, float bx, float by, float bz) {
            verts.push_back(ax); verts.push_back(ay); verts.push_back(az);
            verts.push_back(bx); verts.push_back(by); verts.push_back(bz);
        };
        addLine(x0, y0, z0, x1, y0, z0); addLine(x0, y0, z1, x1, y0, z1);
        addLine(x0, y0, z0, x0, y0, z1); addLine(x1, y0, z0, x1, y0, z1);
        addLine(x0, y1, z0, x1, y1, z0); addLine(x0, y1, z1, x1, y1, z1);
        addLine(x0, y1, z0, x0, y1, z1); addLine(x1, y1, z0, x1, y1, z1);
        addLine(x0, y0, z0, x0, y1, z0); addLine(x1, y0, z0, x1, y1, z0);
        addLine(x0, y0, z1, x0, y1, z1); addLine(x1, y0, z1, x1, y1, z1);
        drawLines(verts.data(), static_cast<int32_t>(verts.size() / 3), r, g, b, a);
    }

    if (prev_cull == GL_TRUE) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
    if (prev_blend == GL_TRUE) glEnable(GL_BLEND); else glDisable(GL_BLEND);
    if (prev_depth == GL_TRUE) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
    glDepthFunc(prev_depth_func);
    glUseProgram(prev_program);
    glBindVertexArray(static_cast<GLuint>(prev_vao));
}

}  // namespace shortcuts
