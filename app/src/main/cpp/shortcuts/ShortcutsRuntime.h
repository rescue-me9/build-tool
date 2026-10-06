#ifndef INFINITE_SHORTCUTS_RUNTIME_H
#define INFINITE_SHORTCUTS_RUNTIME_H

#include <array>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace shortcuts {

enum class FeatureId : uint8_t {
    Scaffold = 0,
    Tower = 1,
    Stairs = 2,
    Clear = 3,
    Floor = 4,
    Wall = 5,
    Level = 6,
    WaterBridge = 7,
    TNTClear = 8,
    AutoSpawn = 9,
    AutoRepair = 10,
    AutoFeed = 11,
    AutoPickup = 12,
    SafetyDome = 13,
    CoordHUD = 14,
    HeightColor = 15,
    Trail = 16,
    HomeBeacon = 17,
    ChunkBorder = 18,
    SafeZone = 19,
    Count,
};

constexpr size_t kFeatureCount = static_cast<size_t>(FeatureId::Count);

struct FeatureConfig {
    bool enabled = false;
    std::array<int32_t, 4> int_params{};
    std::array<float, 4> float_params{};
    std::string string_param;
};

struct PlayerInfo {
    int32_t block_x = 0;
    int32_t block_y = 0;
    int32_t block_z = 0;
    float exact_x = 0.0f;
    float exact_y = 0.0f;
    float exact_z = 0.0f;
    float yaw = 0.0f;
    int32_t dimension = 0;
    int64_t tick_counter = 0;
};

class ShortcutsRuntime {
public:
    static ShortcutsRuntime& instance();

    void onGameTick();

    void setFeatureEnabled(FeatureId id, bool enabled);
    bool isFeatureEnabled(FeatureId id) const;
    void setFeatureIntParam(FeatureId id, int32_t index, int32_t value);
    void setFeatureFloatParam(FeatureId id, int32_t index, float value);
    void setFeatureStringParam(FeatureId id, const std::string& value);
    int32_t getFeatureIntParam(FeatureId id, int32_t index) const;
    float getFeatureFloatParam(FeatureId id, int32_t index) const;
    std::string getFeatureStringParam(FeatureId id) const;
    std::string getFeatureStatus(FeatureId id) const;

    bool getPlayerInfo(PlayerInfo* output) const;

    bool setHomePosition();
    bool getHomePosition(int32_t* x, int32_t* y, int32_t* z) const;

    bool setSafeZoneCenter();

    void getRenderData(std::deque<std::array<float, 3>>& trail_points,
                       int32_t& home_x, int32_t& home_y, int32_t& home_z, bool& home_valid,
                       int32_t& safe_min_x, int32_t& safe_min_y, int32_t& safe_min_z,
                       int32_t& safe_max_x, int32_t& safe_max_y, int32_t& safe_max_z,
                       bool& safe_valid) const;

    bool isHeightColorEnabled() const;
    int32_t heightColorMode() const;

private:
    ShortcutsRuntime();

    bool sendGameCommand(const std::string& command);
    bool queryPlayerRotation(float* yaw);
    bool queryHunger(int32_t* hunger);
    bool queryHealth(float* health);

    void tickScaffold(int32_t px, int32_t py, int32_t pz);
    void tickTower(int32_t px, int32_t py, int32_t pz);
    void tickStairs(int32_t px, int32_t py, int32_t pz);
    void tickClear(int32_t px, int32_t py, int32_t pz);
    void tickFloor(int32_t px, int32_t py, int32_t pz);
    void tickWall(int32_t px, int32_t py, int32_t pz);
    void tickLevel(int32_t px, int32_t py, int32_t pz);
    void tickWaterBridge(int32_t px, int32_t py, int32_t pz);
    void tickTNTClear(int32_t px, int32_t py, int32_t pz);
    void tickAutoSpawn(int32_t px, int32_t py, int32_t pz);
    void tickAutoRepair(int32_t px, int32_t py, int32_t pz);
    void tickAutoFeed();
    void tickAutoPickup(int32_t px, int32_t py, int32_t pz);
    void tickSafetyDome(int32_t px, int32_t py, int32_t pz);
    void tickCoordHUD(int32_t px, int32_t py, int32_t pz);
    void tickTrail(int32_t px, int32_t py, int32_t pz);

    bool isAirBlock(int32_t x, int32_t y, int32_t z);

    mutable std::mutex mutex_;
    FeatureConfig configs_[kFeatureCount];
    PlayerInfo player_info_;
    int64_t tick_counter_ = 0;

    std::deque<std::array<float, 3>> trail_points_;

    int32_t home_x_ = 0, home_y_ = 0, home_z_ = 0;
    bool home_set_ = false;

    int32_t safe_min_x_ = 0, safe_min_y_ = 0, safe_min_z_ = 0;
    int32_t safe_max_x_ = 0, safe_max_y_ = 0, safe_max_z_ = 0;
    bool safe_set_ = false;

    struct RepairBlock {
        int32_t x, y, z;
        std::string name;
        int32_t aux;
    };
    std::vector<RepairBlock> repair_snapshot_;
    bool repair_initialized_ = false;
};

}  // namespace shortcuts

#endif  // INFINITE_SHORTCUTS_RUNTIME_H
