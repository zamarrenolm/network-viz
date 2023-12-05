package experiments.network.viz;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import experiments.network.viz.GridHierarchy.Backbone;
import experiments.network.viz.gephi.Gephi;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

public class VizBackbone extends Viz {

    public VizBackbone(Network network, Predicate<VoltageLevel> voltageLevelFilter, boolean useWeights, WeightInterpretation weightInterpretation) {
        super(network, voltageLevelFilter, useWeights, weightInterpretation);
    }

    public void createBackboneDiagrams(Path outputFolder) throws IOException, FontFormatException {
        prepareOutputFolder(outputFolder);

        GridHierarchy gridHierarchy = new GridHierarchy(network);
        Backbone backbone = gridHierarchy.backbone();

        addLines(backbone.lines);
        addDcLines(backbone.hvdcLines);

        colorizeNodes();
        sizeNodes();
        colorizeEdges();

        layoutAndExport(Gephi.LayoutAlgorithm.ATLAS2, outputFolder, "backbone");
        layoutAndExport(Gephi.LayoutAlgorithm.ATLAS2_NO_WEIGHT, outputFolder, "backbone");
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-0");
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-1", false);
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-2", false);
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-3", false);
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-4", false);
        layoutAndExport(Gephi.LayoutAlgorithm.YIFANHU, outputFolder, "backbone-5", false);

        gephi.keepPositions();
        addLines(gridHierarchy.belowBackbone());
        colorizeNodes();
        sizeNodes();
        colorizeEdges();
        gephi.moveNewNodesCloseToLaidOutNeighbor();

        if (EXPORT_GEXF) {
            gephi.export(outputFolder.resolve("all-closer-to-backbone-refs.gexf"));
        }
        gephi.print(outputFolder.resolve("all-closer-to-backbone-refs.pdf"));

        layoutAndExport(Gephi.LayoutAlgorithm.EXPANSION, outputFolder, "all", false);
        layoutAndExport(Gephi.LayoutAlgorithm.ATLAS2, outputFolder, "all", false);
    }

}
