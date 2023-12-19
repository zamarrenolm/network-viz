package experiments.network.viz;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.nad.build.iidm.VoltageLevelFilter;
import experiments.network.viz.gephi.Gephi;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.function.Predicate;

public final class VizRun {
    private VizRun() {
    }

    private static final Path OUTPUT_FOLDER = Paths.get("/Users/zamarrenolm/work/temp/viz/gephi-java-toolkit");
    private static final Path INPUT_FOLDER_CGMES3 = Paths.get("/Users/zamarrenolm/work/RTE/doc/CGMES/ENTSO-E_Test_Configurations/ENTSO-E_Test_Configurations_v3.0.2/v3.0");
    private static final Path INPUT_FOLDER_RTE = Paths.get("/Users/zamarrenolm/work/RTE/data/RTE_France/");

    public static void main(String[] args) throws IOException, FontFormatException {
        createDiagrams(1, 4);
    }

    private static void createDiagrams(int startAlternative, int endAlternative) throws IOException, FontFormatException {
        for (int alternative = startAlternative; alternative <= endAlternative; alternative++) {
            createDiagrams(createInputsForAlternative(alternative));
        }
    }

    private static DiagramInputs createInputsForAlternative(int alternative) {
        DiagramInputs inputs = new DiagramInputs();
        switch (alternative) {
            case 1:
                inputs.networkFile = INPUT_FOLDER_CGMES3.resolve("MicroGrid/MicroGid-BaseCase/MicroGrid-BaseCase-Merged/kk.zip");
                inputs.name = "MicroGrid";
                break;
            case 2:
                inputs.networkFile = INPUT_FOLDER_CGMES3.resolve("Svedala/Svedala-Merged/kk.zip");
                inputs.voltageLevelFilterFactory = network -> VoltageLevelFilter.createVoltageLevelDepthFilter(network, "6168cd36-d13e-477f-9f8f-8f2e5ee09d5d", 3);
                inputs.name = "Svedala";
                break;
            case 3:
                inputs.networkFile = INPUT_FOLDER_CGMES3.resolve("RealGrid/RealGrid-Merged/kk.zip");
                inputs.voltageLevelFilterFactory = network -> VoltageLevelFilter.createVoltageLevelDepthFilter(network, "f50658c1-485d-4dd5-b146-039e444ed167", 3);
                inputs.name = "RealGrid";
                break;
            case 4:
                inputs.networkFile = INPUT_FOLDER_RTE.resolve("recollement_20210422_0930.xiidm");
                inputs.voltageLevelFilterFactory = network -> VoltageLevelFilter.createVoltageLevelDepthFilter(network, "VLEJUP6", 2);
                inputs.name = "rte-VLEJUP6";
                break;
            case 5:
                inputs.networkFile = INPUT_FOLDER_RTE.resolve("recollement_20210422_0930.xiidm");
                inputs.name = "rte-all";
                break;
            default:
                throw new RuntimeException("no alternative " + alternative);
        }
        return inputs;
    }

    private static final class DiagramInputs {
        Path outputFolder = OUTPUT_FOLDER;
        Path networkFile;
        String name;
        Function<Network, Predicate<VoltageLevel>> voltageLevelFilterFactory = network -> vl -> true;

        Network network;
        boolean useWeights;
    }

    private static void createDiagrams(DiagramInputs inputs) throws IOException, FontFormatException {
        inputs.network = Network.read(inputs.networkFile);
        for (boolean b : new boolean[]{true, false}) {
            inputs.useWeights = b;
            createDiagram(inputs);
        }
    }

    private static void createDiagram(DiagramInputs inputs) throws IOException, FontFormatException {
        String name1 = inputs.name + "-use-weights-" + inputs.useWeights;

        // ATLAS2 and YIFANHU seem to consider weight as edge strength (admittance) instead of costs (impedances)
        new Viz(inputs.network,
                inputs.voltageLevelFilterFactory.apply(inputs.network),
                inputs.useWeights,
                Viz.WeightInterpretation.WEIGHT_IS_ADMITTANCE)
                .createDiagram(inputs.outputFolder, name1, Gephi.LayoutAlgorithm.ATLAS2);
        new Viz(inputs.network,
                inputs.voltageLevelFilterFactory.apply(inputs.network),
                inputs.useWeights,
                Viz.WeightInterpretation.WEIGHT_IS_ADMITTANCE)
                .createDiagram(inputs.outputFolder, name1, Gephi.LayoutAlgorithm.ATLAS2_TOQUETEAR);
        new Viz(inputs.network,
                inputs.voltageLevelFilterFactory.apply(inputs.network),
                inputs.useWeights,
                Viz.WeightInterpretation.WEIGHT_IS_ADMITTANCE)
                .createDiagram(inputs.outputFolder, name1, Gephi.LayoutAlgorithm.YIFANHU);
    }
}
