import subprocess

svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <!-- Red Linear Gradient with diagonal light -->
    <linearGradient id="redBase" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FF3B30" />
      <stop offset="35%" stop-color="#FF1A27" />
      <stop offset="70%" stop-color="#E50914" />
      <stop offset="100%" stop-color="#B80010" />
    </linearGradient>

    <!-- Subtle Red Fold Shadow -->
    <linearGradient id="redCrease" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#FF2A35" />
      <stop offset="50%" stop-color="#C00014" />
      <stop offset="100%" stop-color="#800008" />
    </linearGradient>

    <!-- White S Gradient with gentle depth -->
    <linearGradient id="whiteS" x1="20%" y1="0%" x2="80%" y2="100%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="75%" stop-color="#F4F5F8" />
      <stop offset="100%" stop-color="#E2E4EB" />
    </linearGradient>

    <!-- Drop shadow for the S over red play button -->
    <filter id="shadowS" x="-20%" y="-20%" width="150%" height="150%">
      <feDropShadow dx="-3" dy="6" stdDeviation="7" flood-color="#000000" flood-opacity="0.32" />
    </filter>
  </defs>

  <!-- Solid pitch black background -->
  <rect width="512" height="512" fill="#000000" />

  <!-- Centered Logo Group (Bounding box approx 300x300 inside 512x512) -->
  <g id="logo-emblem">
    <!-- RED PLAY TRIANGLE / BADGE with rounded corners -->
    <!-- Left edge from (138, 168) to (138, 350) with rounded corner, apex at (312, 276) -->
    <path d="M 138 184
             C 138 152, 158 140, 184 154
             L 300 220
             C 322 232, 322 254, 300 266
             L 184 332
             C 158 346, 138 334, 138 302
             Z"
          fill="url(#redBase)" />

    <!-- Red diagonal fold accent -->
    <path d="M 138 238
             L 282 246
             C 314 252, 314 262, 298 270
             L 184 332
             C 158 346, 138 334, 138 302
             Z"
          fill="url(#redCrease)"
          opacity="0.5" />

    <!-- THE WHITE 'S' CURVING BEAUTIFULLY OVER THE RED PLAY BUTTON -->
    <!-- Top terminal: horizontal rounded pill facing right at (385, 115) -->
    <!-- Curves around to the left (206, 115), down the spine to (268, 230) -->
    <!-- Bottom curve expands around to (380, 310) then wraps under to (170, 368) -->
    <!-- All matching the exact proportions of the uploaded emblem -->
    <path d="M 385 116
             C 385 100, 370 88, 352 88
             L 254 88
             C 188 88, 142 134, 142 198
             C 142 242, 172 270, 218 290
             L 272 314
             C 308 330, 326 348, 326 376
             C 326 410, 296 432, 252 432
             C 214 432, 178 414, 152 388
             C 142 378, 126 380, 116 392
             C 106 404, 108 420, 120 432
             C 154 466, 202 486, 256 486
             C 328 486, 384 440, 384 374
             C 384 322, 348 290, 300 268
             L 246 244
             C 214 230, 198 214, 198 188
             C 198 158, 222 142, 256 142
             L 352 142
             C 370 142, 385 130, 385 116
             Z"
          fill="url(#whiteS)"
          filter="url(#shadowS)"
          style="display:none;" />
  </g>
</svg>
"""

with open("stavio_logo.svg", "w") as f:
    f.write(svg)

print("Saved stavio_logo.svg")
