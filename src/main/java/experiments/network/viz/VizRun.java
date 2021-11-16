package experiments.network.viz;

import com.powsybl.iidm.xml.NetworkXml;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VizRun {
    private VizRun() {
    }

    public static void main(String[] args) throws IOException, FontFormatException {
        Path networkFile = Paths.get("/Users/zamarrenolm/work/temp/viz/cgmes3RealGrid.xiidm");
        Path outputFolder = Paths.get("/Users/zamarrenolm/work/temp/viz/gephi-java-toolkit");
        new Viz(NetworkXml.read(networkFile)).createDiagrams(outputFolder);
    }
}
