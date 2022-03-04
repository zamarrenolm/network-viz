package experiments.network.viz;

import com.powsybl.iidm.xml.NetworkXml;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class VizRun {
    private VizRun() {
    }

    public static void main(String[] args) throws IOException, FontFormatException {
        int option = 2;
        switch (option) {
            case 1: {
                Path networkFile = Paths.get("/Users/zamarrenolm/work/temp/viz/cgmes3RealGrid.xiidm");
                Path outputFolder = Paths.get("/Users/zamarrenolm/work/temp/viz/gephi-java-toolkit");
                new Viz(NetworkXml.read(networkFile)).createDiagrams(outputFolder);
                break;
            }
            case 2: {
                Path networkFile = Paths.get("/Users/zamarrenolm/work/temp/viz/TRANSELECTRICA_20210310_1030_FO3_RO1.xiidm");
                Path outputFolder = Paths.get("/Users/zamarrenolm/work/temp/viz/transelectrica");
                new Viz(NetworkXml.read(networkFile)).createDiagrams(outputFolder);
                break;
            }
        }
    }
}
