package com.watchlauncher

enum class WatchFaceStyle(val displayName: String) {
    NOTHING_DOT("Nothing OS"),
    BOLD_DIGITAL("Bold Digital"),
    NEON_MINIMAL("Neon"),
    RETRO_ORANGE("Retro"),
    CLEAN_WHITE("Analog"),
    WAVE_SEIKO("Seiko Wave"),
    PIP_BOY("Pip-Boy 3000"),
    JAMES_BOND("007 Edition"),
    CASIO_RETRO("CASIO G-Shock"),
    MISS_MINUTES_FACE("Miss Minutes"),      // drawn TVA clock face
    MISS_MINUTES_GLOWING("Miss Mins: Glow"),// real image — mm_glowing.jpg
    MISS_MINUTES_GIF("Miss Mins: Animated"),// real gif  — mm_gif_frames/
    TVA_CRT_MONITOR("TVA Monitor"),         // real image — tva_monitor.jpg
    TVA_TIMEDOOR("TVA TimeDoor"),           // real image — tva_timedoor.jpg
    MISS_MINUTES_SCARED("Miss Mins: Alert") // real image — mm_scared.jpg
}
