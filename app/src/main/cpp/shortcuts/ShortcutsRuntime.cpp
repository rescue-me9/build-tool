#include "ShortcutsRuntime.h"
#include "../tp/PythonUtils.h"
#include "../tp/MinecraftUpdateHook.h"
#include "../build_import/NativeWorldAccess.h"
#include "../build_import/BuildProjectionRenderer.h"
#include "../main.h"

#include <android/log.h>
#include <cmath>
#include <chrono>
#include <cstdlib>
#include <cstring>

#define LOG_TAG "Infinitecz_Shortcuts"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace shortcuts {

static int64_t monotonicMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

static void facingFromYaw(float yaw, int32_t* dx, int32_t* dz) {
    while (yaw < 0.0f) yaw += 360.0f;
    while (yaw >= 360.0f) yaw -= 360.0f;
    if (yaw >= 315.0f || yaw < 45.0f) { *dx = 0; *dz = 1; }
    else if (yaw < 135.0f) { *dx = -1; *dz = 0; }
    else if (yaw < 225.0f) { *dx = 0; *dz = -1; }
    else { *dx = 1; *dz = 0; }
}

static std::string intStr(int32_t v) {
    return std::to_string(v);
}

ShortcutsRuntime::ShortcutsRuntime() {
    configs_[static_cast<size_t>(FeatureId::Scaffold)].int_params = {2, 0, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Scaffold)].string_param = "minecraft:stone";

    configs_[static_cast<size_t>(FeatureId::Tower)].int_params = {2, 256, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Tower)].string_param = "minecraft:stone";

    configs_[static_cast<size_t>(FeatureId::Stairs)].int_params = {3, 0, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Stairs)].string_param = "minecraft:stone";

    configs_[static_cast<size_t>(FeatureId::Clear)].int_params = {3, 2, 0, 0};

    configs_[static_cast<size_t>(FeatureId::Floor)].int_params = {5, 3, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Floor)].string_param = "minecraft:stone";

    configs_[static_cast<size_t>(FeatureId::Wall)].int_params = {3, 3, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Wall)].string_param = "minecraft:stone";

    configs_[static_cast<size_t>(FeatureId::Level)].int_params = {10, 4, 0, 0};

    configs_[static_cast<size_t>(FeatureId::WaterBridge)].int_params = {5, 0, 0, 0};

    configs_[static_cast<size_t>(FeatureId::TNTClear)].int_params = {40, 2, 0, 0};

    configs_[static_cast<size_t>(FeatureId::AutoSpawn)].int_params = {200, 0, 0, 0};

    configs_[static_cast<size_t>(FeatureId::AutoRepair)].int_params = {20, 3, 0, 0};

    configs_[static_cast<size_t>(FeatureId::AutoFeed)].int_params = {100, 10, 0, 0};

    configs_[static_cast<size_t>(FeatureId::AutoPickup)].int_params = {10, 8, 0, 0};

    configs_[static_cast<size_t>(FeatureId::SafetyDome)].int_params = {10, 10, 5, 0};
    configs_[static_cast<size_t>(FeatureId::SafetyDome)].string_param = "minecraft:glass";

    configs_[static_cast<size_t>(FeatureId::HeightColor)].int_params = {0, 0, 0, 0};

    configs_[static_cast<size_t>(FeatureId::Trail)].int_params = {100, 0, 0, 0};
    configs_[static_cast<size_t>(FeatureId::Trail)].float_params = {0.4f, 0.8f, 1.0f, 0.6f};

    configs_[static_cast<size_t>(FeatureId::HomeBeacon)].float_params = {1.0f, 0.8f, 0.0f, 0.8f};

    configs_[static_cast<size_t>(FeatureId::ChunkBorder)].int_params = {4, 0, 0, 0};
    configs_[static_cast<size_t>(FeatureId::ChunkBorder)].float_params = {0.5f, 0.5f, 1.0f, 0.4f};

    configs_[static_cast<size_t>(FeatureId::SafeZone)].int_params = {10, 0, 0, 0};
    configs_[static_cast<size_t>(FeatureId::SafeZone)].float_params = {1.0f, 0.2f, 0.2f, 0.5f};
}

ShortcutsRuntime& ShortcutsRuntime::instance() {
    static ShortcutsRuntime inst;
    return inst;
}

bool ShortcutsRuntime::sendGameCommand(const std::string& command) {
    if (command.empty()) return false;
    setenv("INFINITECZ_SC_CMD", command.c_str(), 1);
    std::string code =
        "import os, msgpack, _pynetmodule\n"
        "import mod.client.extraClientApi as _sc_api\n"
        "def _sc_tuple(value):\n"
        "  return {'__type__': 'tuple', 'value': list(value)} if isinstance(value, tuple) else value\n"
        "_sc_player = _sc_api.GetLocalPlayerId()\n"
        "if _sc_player is None or str(_sc_player) in ('', '-1'):\n"
        "  raise RuntimeError('local player is unavailable')\n"
        "_sc_cmd = os.environ.get('INFINITECZ_SC_CMD', '')\n"
        "if not _sc_cmd:\n"
        "  raise RuntimeError('command is empty')\n"
        "_sc_packet = msgpack.packb(('ModEventC2S', "
        "('Minecraft', 'aiCommand', 'ExecuteCommandEvent', "
        "{'playerId': _sc_player, 'cmd': _sc_cmd, 'uuid': 'sc_auto', 'aiModel': '-1'}), None), "
        "use_bin_type=True, strict_types=True, default=_sc_tuple)\n"
        "_pynetmodule.send2server(98247598, _sc_packet, len(_sc_packet))\n";
    return PythonUtils::PyExecChecked(code, true);
}

bool ShortcutsRuntime::queryPlayerRotation(float* yaw) {
    if (!yaw) return false;
    std::string statements =
        "import mod.client.extraClientApi as _sc_rot_api\n"
        "_sc_rot_player = _sc_rot_api.GetLocalPlayerId()\n"
        "if _sc_rot_player is None or str(_sc_rot_player) in ('', '-1'):\n"
        "  raise RuntimeError('local player is unavailable')\n"
        "_sc_rot_value = _sc_rot_api.GetEngineCompFactory().CreateRot(_sc_rot_player).GetRot()\n"
        "if _sc_rot_value is None or len(_sc_rot_value) < 2:\n"
        "  raise RuntimeError('rotation is unavailable')\n";
    std::string expression =
        "'%.6f,%.6f' % (float(_sc_rot_value[0]), float(_sc_rot_value[1]))";
    std::string result;
    if (!PythonUtils::PyEvalUtf8(statements, expression, &result)) return false;
    float y_val = 0.0f;
    if (std::sscanf(result.c_str(), "%f,%*f", &y_val) != 1) return false;
    *yaw = y_val;
    return true;
}

bool ShortcutsRuntime::queryHunger(int32_t* hunger) {
    if (!hunger) return false;
    std::string statements =
        "import mod.client.extraClientApi as _sc_hunger_api\n"
        "_sc_hunger_player = _sc_hunger_api.GetLocalPlayerId()\n"
        "if _sc_hunger_player is None or str(_sc_hunger_player) in ('', '-1'):\n"
        "  raise RuntimeError('local player is unavailable')\n"
        "_sc_hunger_value = _sc_hunger_api.GetEngineCompFactory().CreateHunger(_sc_hunger_player).GetPlayerHunger()\n"
        "if _sc_hunger_value is None:\n"
        "  raise RuntimeError('hunger is unavailable')\n";
    std::string expression = "str(int(_sc_hunger_value))";
    std::string result;
    if (!PythonUtils::PyEvalUtf8(statements, expression, &result)) return false;
    *hunger = std::atoi(result.c_str());
    return true;
}

bool ShortcutsRuntime::queryHealth(float* health) {
    if (!health) return false;
    std::string statements =
        "import mod.client.extraClientApi as _sc_hp_api\n"
        "_sc_hp_player = _sc_hp_api.GetLocalPlayerId()\n"
        "if _sc_hp_player is None or str(_sc_hp_player) in ('', '-1'):\n"
        "  raise RuntimeError('local player is unavailable')\n"
        "_sc_hp_value = _sc_hp_api.GetEngineCompFactory().CreateHealth(_sc_hp_player).GetHealth()\n"
        "if _sc_hp_value is None:\n"
        "  raise RuntimeError('health is unavailable')\n";
    std::string expression = "str(float(_sc_hp_value))";
    std::string result;
    if (!PythonUtils::PyEvalUtf8(statements, expression, &result)) return false;
    *health = static_cast<float>(std::atof(result.c_str()));
    return true;
}

bool ShortcutsRuntime::isAirBlock(int32_t x, int32_t y, int32_t z) {
    build_import::NativeBlockInfo info;
    if (!build_import::NativeWorldAccess::getBlock(x, y, z, &info)) return true;
    return info.name == "minecraft:air" || info.name == "air" || info.name.empty();
}

void ShortcutsRuntime::tickScaffold(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Scaffold)];
    const std::string& block = cfg.string_param.empty() ? "minecraft:stone" : cfg.string_param;
    int32_t dx = 0, dz = 0;
    if (cfg.int_params[1] == 1) {
        facingFromYaw(player_info_.yaw, &dx, &dz);
    }
    int32_t bx = px + dx;
    int32_t by = py - 1;
    int32_t bz = pz + dz;
    if (isAirBlock(bx, by, bz)) {
        sendGameCommand("/setblock " + intStr(bx) + " " + intStr(by) + " " + intStr(bz) + " " + block + " replace");
    }
}

void ShortcutsRuntime::tickTower(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Tower)];
    const std::string& block = cfg.string_param.empty() ? "minecraft:stone" : cfg.string_param;
    sendGameCommand("/setblock " + intStr(px) + " " + intStr(py) + " " + intStr(pz) + " " + block + " replace");
    sendGameCommand("/tp @s " + intStr(px) + " " + intStr(py + 1) + " " + intStr(pz));
}

void ShortcutsRuntime::tickStairs(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Stairs)];
    const std::string& block = cfg.string_param.empty() ? "minecraft:stone" : cfg.string_param;
    int32_t dx = 0, dz = 0;
    facingFromYaw(player_info_.yaw, &dx, &dz);
    if (cfg.int_params[1] == 1) { dx = 0; dz = 1; }
    else if (cfg.int_params[1] == 2) { dx = -1; dz = 0; }
    else if (cfg.int_params[1] == 3) { dx = 1; dz = 0; }
    else if (cfg.int_params[1] == 4) { dx = 0; dz = -1; }
    int32_t bx = px + dx;
    int32_t bz = pz + dz;
    sendGameCommand("/setblock " + intStr(bx) + " " + intStr(py) + " " + intStr(bz) + " " + block + " replace");
    sendGameCommand("/tp @s " + intStr(bx) + " " + intStr(py + 1) + " " + intStr(bz));
}

void ShortcutsRuntime::tickClear(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Clear)];
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 5) r = 5;
    sendGameCommand("/fill " + intStr(px - r) + " " + intStr(py) + " " + intStr(pz - r) +
                    " " + intStr(px + r) + " " + intStr(py + 2) + " " + intStr(pz + r) + " air replace");
}

void ShortcutsRuntime::tickFloor(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Floor)];
    const std::string& block = cfg.string_param.empty() ? "minecraft:stone" : cfg.string_param;
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 10) r = 10;
    sendGameCommand("/fill " + intStr(px - r) + " " + intStr(py - 1) + " " + intStr(pz - r) +
                    " " + intStr(px + r) + " " + intStr(py - 1) + " " + intStr(pz + r) + " " + block + " replace");
}

void ShortcutsRuntime::tickWall(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Wall)];
    const std::string& block = cfg.string_param.empty() ? "minecraft:stone" : cfg.string_param;
    int32_t h = cfg.int_params[1];
    if (h < 1) h = 1;
    if (h > 5) h = 5;
    int32_t fdx = 0, fdz = 0;
    facingFromYaw(player_info_.yaw, &fdx, &fdz);
    int32_t ldx = -fdz, ldz = fdx;
    for (int32_t i = 0; i < h; ++i) {
        sendGameCommand("/setblock " + intStr(px + ldx) + " " + intStr(py + i) + " " + intStr(pz + ldz) + " " + block + " replace");
        sendGameCommand("/setblock " + intStr(px - ldx) + " " + intStr(py + i) + " " + intStr(pz - ldz) + " " + block + " replace");
    }
}

void ShortcutsRuntime::tickLevel(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Level)];
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 8) r = 8;
    sendGameCommand("/fill " + intStr(px - r) + " " + intStr(py - 1) + " " + intStr(pz - r) +
                    " " + intStr(px + r) + " " + intStr(py - 1) + " " + intStr(pz + r) + " minecraft:stone replace");
    sendGameCommand("/fill " + intStr(px - r) + " " + intStr(py) + " " + intStr(pz - r) +
                    " " + intStr(px + r) + " " + intStr(py + 3) + " " + intStr(pz + r) + " air replace");
}

void ShortcutsRuntime::tickWaterBridge(int32_t px, int32_t py, int32_t pz) {
    sendGameCommand("/setblock " + intStr(px) + " " + intStr(py - 1) + " " + intStr(pz) + " minecraft:water replace");
}

void ShortcutsRuntime::tickTNTClear(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::TNTClear)];
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 4) r = 4;
    int32_t fdx = 0, fdz = 0;
    facingFromYaw(player_info_.yaw, &fdx, &fdz);
    int32_t fx = px + fdx * 2;
    int32_t fz = pz + fdz * 2;
    sendGameCommand("/fill " + intStr(fx - r) + " " + intStr(py) + " " + intStr(fz - r) +
                    " " + intStr(fx + r) + " " + intStr(py + 1) + " " + intStr(fz + r) + " minecraft:tnt replace");
    sendGameCommand("/fill " + intStr(fx - r) + " " + intStr(py) + " " + intStr(fz - r) +
                    " " + intStr(fx + r) + " " + intStr(py + 1) + " " + intStr(fz + r) + " minecraft:fire replace");
}

void ShortcutsRuntime::tickAutoSpawn(int32_t px, int32_t py, int32_t pz) {
    sendGameCommand("/spawnpoint " + intStr(px) + " " + intStr(py) + " " + intStr(pz));
}

void ShortcutsRuntime::tickAutoRepair(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::AutoRepair)];
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 4) r = 4;
    if (!repair_initialized_) {
        repair_snapshot_.clear();
        for (int32_t x = px - r; x <= px + r; ++x) {
            for (int32_t y = py - 1; y <= py + 2; ++y) {
                for (int32_t z = pz - r; z <= pz + r; ++z) {
                    build_import::NativeBlockInfo info;
                    if (build_import::NativeWorldAccess::getBlock(x, y, z, &info) && !info.name.empty() && info.name != "air") {
                        repair_snapshot_.push_back({x, y, z, info.name, info.aux});
                    }
                }
            }
        }
        repair_initialized_ = true;
        LOGI("auto-repair snapshot: %zu blocks", repair_snapshot_.size());
        return;
    }
    for (const auto& blk : repair_snapshot_) {
        build_import::NativeBlockInfo info;
        if (build_import::NativeWorldAccess::getBlock(blk.x, blk.y, blk.z, &info)) {
            if (info.name != blk.name) {
                sendGameCommand("/setblock " + intStr(blk.x) + " " + intStr(blk.y) + " " + intStr(blk.z) + " " + blk.name + " replace");
            }
        }
    }
}

void ShortcutsRuntime::tickAutoFeed() {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::AutoFeed)];
    int32_t threshold = cfg.int_params[1];
    int32_t hunger = 20;
    if (queryHunger(&hunger) && hunger < threshold) {
        sendGameCommand("/effect @s saturation 1 5");
    } else if (!queryHunger(&hunger)) {
        sendGameCommand("/effect @s saturation 1 5");
    }
}

void ShortcutsRuntime::tickAutoPickup(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::AutoPickup)];
    int32_t r = cfg.int_params[1];
    if (r < 1) r = 1;
    if (r > 32) r = 32;
    sendGameCommand("/tp @e[type=item,r=" + intStr(r) + "] " + intStr(px) + " " + intStr(py) + " " + intStr(pz));
}

void ShortcutsRuntime::tickSafetyDome(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::SafetyDome)];
    float health = 20.0f;
    bool need_dome = false;
    if (queryHealth(&health)) {
        need_dome = health < static_cast<float>(cfg.int_params[1]);
    }
    if (!need_dome) return;
    const std::string& block = cfg.string_param.empty() ? "minecraft:glass" : cfg.string_param;
    int32_t r = cfg.int_params[2];
    if (r < 1) r = 1;
    if (r > 10) r = 10;
    sendGameCommand("/fill " + intStr(px - r) + " " + intStr(py) + " " + intStr(pz - r) +
                    " " + intStr(px + r) + " " + intStr(py + 3) + " " + intStr(pz + r) + " " + block + " replace");
    sendGameCommand("/fill " + intStr(px - r + 1) + " " + intStr(py + 1) + " " + intStr(pz - r + 1) +
                    " " + intStr(px + r - 1) + " " + intStr(py + 2) + " " + intStr(pz + r - 1) + " air replace");
    LOGI("safety dome deployed at (%d, %d, %d) health=%.1f", px, py, pz, health);
}

void ShortcutsRuntime::tickCoordHUD(int32_t px, int32_t py, int32_t pz) {
    (void)px; (void)py; (void)pz;
}

void ShortcutsRuntime::tickTrail(int32_t px, int32_t py, int32_t pz) {
    auto& cfg = configs_[static_cast<size_t>(FeatureId::Trail)];
    int32_t max_len = cfg.int_params[0];
    if (max_len < 10) max_len = 10;
    if (max_len > 500) max_len = 500;
    float fx = static_cast<float>(px) + 0.5f;
    float fy = static_cast<float>(py);
    float fz = static_cast<float>(pz) + 0.5f;
    if (trail_points_.empty() ||
        std::fabs(trail_points_.back()[0] - fx) > 0.01f ||
        std::fabs(trail_points_.back()[1] - fy) > 0.01f ||
        std::fabs(trail_points_.back()[2] - fz) > 0.01f) {
        trail_points_.push_back({fx, fy, fz});
        while (static_cast<int32_t>(trail_points_.size()) > max_len) {
            trail_points_.pop_front();
        }
    }
}

void ShortcutsRuntime::onGameTick() {
    if (!IsMinecraftUpdateGameThread()) return;

    std::lock_guard<std::mutex> lock(mutex_);
    ++tick_counter_;

    int32_t px = 0, py = 0, pz = 0;
    if (!build_import::NativeWorldAccess::getLocalPlayerBlockPosition(&px, &py, &pz)) return;

    player_info_.block_x = px;
    player_info_.block_y = py;
    player_info_.block_z = pz;
    player_info_.tick_counter = tick_counter_;

    if (tick_counter_ % 10 == 0) {
        float yaw = player_info_.yaw;
        if (queryPlayerRotation(&yaw)) {
            player_info_.yaw = yaw;
        }
    }

    if (tick_counter_ % 5 == 0) {
        float cx = 0, cy = 0, cz = 0;
        if (build_import::GetLatestBuildRenderCameraPosition(&cx, &cy, &cz)) {
            player_info_.exact_x = cx;
            player_info_.exact_y = cy;
            player_info_.exact_z = cz;
        }
    }

    player_info_.dimension = static_cast<int32_t>(build_import::NativeWorldAccess::dimensionToken() & 0x7FFFFFFF);

    for (size_t i = 0; i < kFeatureCount; ++i) {
        if (!configs_[i].enabled) continue;
        auto id = static_cast<FeatureId>(i);
        int32_t interval = configs_[i].int_params[0];
        if (interval < 1) interval = 1;

        bool run_every_tick = (id == FeatureId::CoordHUD || id == FeatureId::Trail);
        if (!run_every_tick && tick_counter_ % interval != 0) continue;

        try {
            switch (id) {
                case FeatureId::Scaffold: tickScaffold(px, py, pz); break;
                case FeatureId::Tower: tickTower(px, py, pz); break;
                case FeatureId::Stairs: tickStairs(px, py, pz); break;
                case FeatureId::Clear: tickClear(px, py, pz); break;
                case FeatureId::Floor: tickFloor(px, py, pz); break;
                case FeatureId::Wall: tickWall(px, py, pz); break;
                case FeatureId::Level: tickLevel(px, py, pz); break;
                case FeatureId::WaterBridge: tickWaterBridge(px, py, pz); break;
                case FeatureId::TNTClear: tickTNTClear(px, py, pz); break;
                case FeatureId::AutoSpawn: tickAutoSpawn(px, py, pz); break;
                case FeatureId::AutoRepair: tickAutoRepair(px, py, pz); break;
                case FeatureId::AutoFeed: tickAutoFeed(); break;
                case FeatureId::AutoPickup: tickAutoPickup(px, py, pz); break;
                case FeatureId::SafetyDome: tickSafetyDome(px, py, pz); break;
                case FeatureId::CoordHUD: tickCoordHUD(px, py, pz); break;
                case FeatureId::Trail: tickTrail(px, py, pz); break;
                case FeatureId::HeightColor: break;
                case FeatureId::HomeBeacon: break;
                case FeatureId::ChunkBorder: break;
                case FeatureId::SafeZone: break;
                case FeatureId::Count: break;
            }
        } catch (const std::exception& e) {
            LOGE("shortcut tick %zu failed: %s", i, e.what());
        } catch (...) {
            LOGE("shortcut tick %zu failed: unknown", i);
        }
    }
}

void ShortcutsRuntime::setFeatureEnabled(FeatureId id, bool enabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount) return;
    configs_[idx].enabled = enabled;
    if (id == FeatureId::AutoRepair && !enabled) {
        repair_initialized_ = false;
        repair_snapshot_.clear();
    }
    if (id == FeatureId::Trail && !enabled) {
        trail_points_.clear();
    }
    LOGI("shortcut %zu %s", idx, enabled ? "enabled" : "disabled");
}

bool ShortcutsRuntime::isFeatureEnabled(FeatureId id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount) return false;
    return configs_[idx].enabled;
}

void ShortcutsRuntime::setFeatureIntParam(FeatureId id, int32_t index, int32_t value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount || index < 0 || index >= 4) return;
    configs_[idx].int_params[index] = value;
}

void ShortcutsRuntime::setFeatureFloatParam(FeatureId id, int32_t index, float value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount || index < 0 || index >= 4) return;
    configs_[idx].float_params[index] = value;
}

void ShortcutsRuntime::setFeatureStringParam(FeatureId id, const std::string& value) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount) return;
    configs_[idx].string_param = value;
}

int32_t ShortcutsRuntime::getFeatureIntParam(FeatureId id, int32_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount || index < 0 || index >= 4) return 0;
    return configs_[idx].int_params[index];
}

float ShortcutsRuntime::getFeatureFloatParam(FeatureId id, int32_t index) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount || index < 0 || index >= 4) return 0.0f;
    return configs_[idx].float_params[index];
}

std::string ShortcutsRuntime::getFeatureStringParam(FeatureId id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount) return {};
    return configs_[idx].string_param;
}

std::string ShortcutsRuntime::getFeatureStatus(FeatureId id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto idx = static_cast<size_t>(id);
    if (idx >= kFeatureCount) return {};
    if (!configs_[idx].enabled) return std::to_string(idx) + "\toff";
    return std::to_string(idx) + "\ton";
}

bool ShortcutsRuntime::getPlayerInfo(PlayerInfo* output) const {
    if (!output) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    *output = player_info_;
    return true;
}

bool ShortcutsRuntime::setHomePosition() {
    std::lock_guard<std::mutex> lock(mutex_);
    home_x_ = player_info_.block_x;
    home_y_ = player_info_.block_y;
    home_z_ = player_info_.block_z;
    home_set_ = true;
    LOGI("home set to (%d, %d, %d)", home_x_, home_y_, home_z_);
    return true;
}

bool ShortcutsRuntime::getHomePosition(int32_t* x, int32_t* y, int32_t* z) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!home_set_ || !x || !y || !z) return false;
    *x = home_x_; *y = home_y_; *z = home_z_;
    return true;
}

bool ShortcutsRuntime::setSafeZoneCenter() {
    std::lock_guard<std::mutex> lock(mutex_);
    auto& cfg = configs_[static_cast<size_t>(FeatureId::SafeZone)];
    int32_t r = cfg.int_params[0];
    if (r < 1) r = 1;
    if (r > 50) r = 50;
    safe_min_x_ = player_info_.block_x - r;
    safe_min_y_ = player_info_.block_y - 1;
    safe_min_z_ = player_info_.block_z - r;
    safe_max_x_ = player_info_.block_x + r;
    safe_max_y_ = player_info_.block_y + 4;
    safe_max_z_ = player_info_.block_z + r;
    safe_set_ = true;
    LOGI("safe zone set: (%d,%d,%d) to (%d,%d,%d)", safe_min_x_, safe_min_y_, safe_min_z_, safe_max_x_, safe_max_y_, safe_max_z_);
    return true;
}

void ShortcutsRuntime::getRenderData(
        std::deque<std::array<float, 3>>& trail_points,
        int32_t& home_x, int32_t& home_y, int32_t& home_z, bool& home_valid,
        int32_t& safe_min_x, int32_t& safe_min_y, int32_t& safe_min_z,
        int32_t& safe_max_x, int32_t& safe_max_y, int32_t& safe_max_z,
        bool& safe_valid) const {
    std::lock_guard<std::mutex> lock(mutex_);
    trail_points = trail_points_;
    home_x = home_x_; home_y = home_y_; home_z = home_z_; home_valid = home_set_;
    safe_min_x = safe_min_x_; safe_min_y = safe_min_y_; safe_min_z = safe_min_z_;
    safe_max_x = safe_max_x_; safe_max_y = safe_max_y_; safe_max_z = safe_max_z_;
    safe_valid = safe_set_;
}

bool ShortcutsRuntime::isHeightColorEnabled() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return configs_[static_cast<size_t>(FeatureId::HeightColor)].enabled;
}

int32_t ShortcutsRuntime::heightColorMode() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return configs_[static_cast<size_t>(FeatureId::HeightColor)].int_params[0];
}

}  // namespace shortcuts
