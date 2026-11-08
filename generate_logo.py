import sys

# High quality SVG with the exact Stavio 'S' + Play button geometry
svg_content = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- Background Gradient -->
    <radialGradient id="bgGlow" cx="50%" cy="50%" r="65%">
      <stop offset="0%" stop-color="#0F0F14" />
      <stop offset="100%" stop-color="#000000" />
    </radialGradient>

    <!-- Vibrant Red Gradient for Play Element -->
    <linearGradient id="redGradient" x1="20%" y1="15%" x2="85%" y2="85%">
      <stop offset="0%" stop-color="#FF3848" />
      <stop offset="45%" stop-color="#FF1A2A" />
      <stop offset="100%" stop-color="#CE0016" />
    </linearGradient>

    <!-- Subtle Bevel Shadow for Red Fold -->
    <linearGradient id="foldShadow" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#000000" stop-opacity="0.0" />
      <stop offset="60%" stop-color="#000000" stop-opacity="0.15" />
      <stop offset="100%" stop-color="#000000" stop-opacity="0.45" />
    </linearGradient>

    <!-- Crisp White Gradient for the 'S' -->
    <linearGradient id="whiteGradient" x1="20%" y1="0%" x2="80%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="60%" stop-color="#F6F7FA" />
      <stop offset="100%" stop-color="#E2E5EB" />
    </linearGradient>

    <!-- Soft Drop Shadow -->
    <filter id="softShadow" x="-10%" y="-10%" width="125%" height="125%">
      <feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#000000" flood-opacity="0.35" />
    </filter>
  </defs>

  <!-- Pitch Black Canvas -->
  <rect width="512" height="512" fill="url(#bgGlow)" />

  <g transform="translate(138, 128)">
    <!-- Red Play Badge / Triangle with rounded corners -->
    <path d="M 0 32
             C 0 14, 14 0, 32 0
             L 56 0
             C 66 0, 76 6, 82 14
             L 160 102
             C 172 116, 168 136, 150 144
             L 138 148
             C 118 156, 92 148, 80 134
             L 40 88
             C 32 78, 0 86, 0 106
             L 0 200
             C 0 218, 14 232, 32 232
             L 44 232
             C 24 232, 0 212, 0 188
             Z"
          fill="url(#redGradient)" />
  </g>
</svg>
"""
