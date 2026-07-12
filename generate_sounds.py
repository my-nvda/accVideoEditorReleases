import wave
import struct
import math
import os

def generate_tone(filename, freq_start, freq_end, duration_ms, wave_type='sine'):
    sample_rate = 44100
    num_samples = int(sample_rate * (duration_ms / 1000.0))
    
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        
        for i in range(num_samples):
            t = float(i) / sample_rate
            # Linear frequency sweep
            freq = freq_start + (freq_end - freq_start) * (t / (duration_ms / 1000.0))
            
            if wave_type == 'sine':
                value = math.sin(2.0 * math.pi * freq * t)
            elif wave_type == 'square':
                value = 1.0 if math.sin(2.0 * math.pi * freq * t) > 0 else -1.0
            
            # Apply envelope (fade in/out) to prevent clicks
            envelope = 1.0
            if i < sample_rate * 0.05:
                envelope = i / (sample_rate * 0.05)
            elif i > num_samples - sample_rate * 0.05:
                envelope = (num_samples - i) / (sample_rate * 0.05)
                
            packed_value = struct.pack('h', int(value * envelope * 32767.0 * 0.5))
            wav_file.writeframes(packed_value)

# Generate UI Sounds
# 1. Startup: Ascending pleasant chime
generate_tone('app/src/main/res/raw/sound_startup.wav', 440.0, 880.0, 400, 'sine')

# 2. Processing start: Short subtle click/beep
generate_tone('app/src/main/res/raw/sound_processing.wav', 600.0, 600.0, 150, 'sine')

# 3. Success: High pitched quick ascending
generate_tone('app/src/main/res/raw/sound_success.wav', 523.25, 1046.50, 300, 'sine')

# 4. Error: Low pitched descending buzz
generate_tone('app/src/main/res/raw/sound_error.wav', 300.0, 150.0, 400, 'square')

print("Sounds generated successfully in app/src/main/res/raw/")
