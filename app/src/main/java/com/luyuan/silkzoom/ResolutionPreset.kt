package com.luyuan.silkzoom

/** Legacy enum names preserve existing photo-quality preferences. */
enum class ResolutionPreset(val label: String, val width: Int, val height: Int) {
    PHOTO_12_MP("标准", 2560, 1920),
    PHOTO_MAX("高画质", 4000, 3000),
}
