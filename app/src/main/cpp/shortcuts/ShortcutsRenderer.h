#ifndef INFINITE_SHORTCUTS_RENDERER_H
#define INFINITE_SHORTCUTS_RENDERER_H

#include <cstdint>

namespace shortcuts {

class ShortcutsRenderer {
public:
    static ShortcutsRenderer& instance();
    void render();

private:
    ShortcutsRenderer() = default;
    bool initializeGl();
    void drawLines(const float* vertices, int32_t count, float r, float g, float b, float a);

    uint32_t program_ = 0;
    uint32_t vao_ = 0;
    uint32_t vbo_ = 0;
    int32_t mvp_loc_ = -1;
    int32_t color_loc_ = -1;
    bool gl_ready_ = false;
};

}  // namespace shortcuts

#endif  // INFINITE_SHORTCUTS_RENDERER_H
