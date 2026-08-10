"""Maps a patient's rhythm (acrophase + strength = CWT CRI) to a 24 h schedule of light intensity and colour temperature."""
import math

CCT_DAY_STRONG, CCT_DAY_WEAK = 5000.0, 6500.0      # weaker rhythm -> more blue-enriched day
CCT_NIGHT_STRONG, CCT_NIGHT_WEAK = 2700.0, 2300.0  # weaker rhythm -> warmer night
NIGHT_FLOOR_STRONG, NIGHT_FLOOR_WEAK = 0.25, 0.05  # weaker rhythm -> darker night

def peak_hour(acrophase_rad):
    h = -acrophase_rad * 24.0 / (2.0 * math.pi)
    return ((h % 24.0) + 24.0) % 24.0

def _lerp(weak, strong, s):
    return weak + (strong - weak) * s

def kelvin_to_rgb(kelvin):
    """Tanner-Helland approximation -> (r, g, b) in 0-255."""
    t = kelvin / 100.0
    r = 255.0 if t <= 66 else 329.698727446 * ((t - 60.0) ** -0.1332047592)
    g = (99.4708025861 * math.log(t) - 161.1195681661) if t <= 66 \
        else 288.1221695283 * ((t - 60.0) ** -0.0755148492)
    if t >= 66:
        b = 255.0
    elif t <= 19:
        b = 0.0
    else:
        b = 138.5177312231 * math.log(t - 10.0) - 305.0447927307
    clamp = lambda v: int(max(0.0, min(255.0, v)))
    return clamp(r), clamp(g), clamp(b)

def schedule(acrophase_rad, cri, steps=48):
    s = max(0.0, min(1.0, cri if cri is not None else 0.0))
    pk = peak_hour(acrophase_rad)
    night_floor = _lerp(NIGHT_FLOOR_WEAK, NIGHT_FLOOR_STRONG, s)
    cct_day = _lerp(CCT_DAY_WEAK, CCT_DAY_STRONG, s)
    cct_night = _lerp(CCT_NIGHT_WEAK, CCT_NIGHT_STRONG, s)

    out = []
    for i in range(steps):
        hour = i * 24.0 / steps
        day_shape = 0.5 * (1.0 + math.cos(2.0 * math.pi * (hour - pk) / 24.0))  # 1 at peak, 0 twelve h away
        intensity = night_floor + (1.0 - night_floor) * day_shape
        cct = cct_night + (cct_day - cct_night) * day_shape
        r, g, b = kelvin_to_rgb(cct)
        out.append({"hour": round(hour, 3), "intensity": round(intensity, 4),
                    "cct": round(cct), "rgb": f"rgb({r},{g},{b})"})
    return {"peak_hour": round(pk, 3), "cri": round(s, 4), "steps": out}