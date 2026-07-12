import java.io.*;
import java.util.*;

public class TestProcess {
    public static void main(String[] args) throws Exception {
        String[] cmd = {
            "ffmpeg", "-y", "-i", "test_in.mp4", "-loop", "1", "-i", "test_overlay.png",
            "-filter_complex", "[0:v][1:v]overlay=enable='between(t,1,3)':shortest=1[out]",
            "-map", "[out]", "-map", "0:a?", "-c:a", "copy", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "veryfast", "test_out2.mp4"
        };
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        p.waitFor();
    }
}
