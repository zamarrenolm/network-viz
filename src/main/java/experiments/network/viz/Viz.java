package experiments.network.viz;

import com.powsybl.iidm.network.*;
import experiments.network.viz.gephi.Gephi;
import org.apache.commons.io.FileUtils;
import org.gephi.appearance.api.*;
import org.gephi.appearance.plugin.PartitionElementColorTransformer;
import org.gephi.appearance.plugin.RankingElementColorTransformer;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Node;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Viz {
    // Colors from ENTSO-E map of Continental Europe
    private static final Color ENTSOE_BLUE = new Color(27, 78, 162);
    private static final Color ENTSOE_MAROON = new Color(169, 41, 71);
    private static final Color ENTSOE_RED = new Color(235, 51, 35);
    private static final Color ENTSOE_ORANGE = new Color(243, 169, 60);
    private static final Color ENTSOE_GREEN = new Color(90, 165, 48);
    private static final Color ENTSOE_BLACK = new Color(0, 0, 0);
    private static final Color ENTSOE_PINK = new Color(223, 137, 232);
    private static final double[] acNominalVoltages = {
            0, 110, 220, 300, 380, 500, 750
    };
    private static final Color[] AC_NOMINAL_VOLTAGE_COLORS = {
            Color.LIGHT_GRAY, ENTSOE_BLACK, ENTSOE_GREEN, ENTSOE_ORANGE, ENTSOE_RED, ENTSOE_MAROON, ENTSOE_BLUE
    };
    private static final Color DC_COLOR = ENTSOE_PINK;
    private static final Color NETP_GENERATION = new Color(255, 145, 61);
    private static final Color NETP_ZERO = new Color(255, 255, 255);
    private static final Color NETP_CONSUMPTION = new Color(188, 238, 98);

    private final Network network;
    private final Grid grid;
    private final Gephi gephi;
    private final Column nominalVoltageColumn;

    public Viz(Network network) {
        this.network = network;
        grid = new Grid(network);
        gephi = new Gephi();
        addNetworkColumns();
        nominalVoltageColumn = gephi.model.getEdgeTable().getColumn("nominalVoltage");
    }

    private static void prepareOutputFolder(Path outputFolder) throws IOException {
        Files.createDirectories(outputFolder);
        FileUtils.cleanDirectory(outputFolder.toFile());
    }

    public void createDiagrams(Path outputFolder) throws IOException, FontFormatException {
        prepareOutputFolder(outputFolder);

        addLines(grid.backbone().lines);
        addDcLines(grid.backbone().hvdcLines);

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
        addLines(grid.subNetworks());
        colorizeNodes();
        sizeNodes();
        colorizeEdges();
        gephi.moveNewNodesCloseToLaidOutNeighbor();

        gephi.export(outputFolder.resolve("all-closer-to-backbone-refs.gexf"));
        gephi.print(outputFolder.resolve("all-closer-to-backbone-refs.pdf"));

        layoutAndExport(Gephi.LayoutAlgorithm.EXPANSION, outputFolder, "all", false);
        layoutAndExport(Gephi.LayoutAlgorithm.ATLAS2, outputFolder, "all", false);
    }

    private void layoutAndExport(Gephi.LayoutAlgorithm algorithm, Path outputFolder, String part) throws IOException, FontFormatException {
        layoutAndExport(algorithm, outputFolder, part, true);
    }

    private void layoutAndExport(Gephi.LayoutAlgorithm algorithm, Path outputFolder, String part, boolean reset) throws IOException, FontFormatException {
        gephi.layout(algorithm, reset);
        gephi.export(outputFolder.resolve(part + "-" + algorithm + ".gexf"));
        gephi.print(outputFolder.resolve(part + "-" + algorithm + ".pdf"));
    }

    private void colorizeEdges() {
        Column column = gephi.model.getEdgeTable().getColumn("nominalVoltage");
        Function function = gephi.appearanceModel.getEdgeFunction(gephi.graph, column, PartitionElementColorTransformer.class);
        Partition partition = ((PartitionFunction) function).getPartition();
        System.out.println("Colorize edges:");
        System.out.println("   Partition by nominal voltage : " + partition.size() + " " + partition.getValues());

        for (Object v : partition.getValues()) {
            double nominalVoltage = (Double)v;
            int kc = 0;
            for (kc = 0; kc < acNominalVoltages.length && nominalVoltage >= acNominalVoltages[kc]; kc++);
            partition.setColor(nominalVoltage, AC_NOMINAL_VOLTAGE_COLORS[kc - 1]);
        }
        gephi.appearanceController.transform(function);
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (Edge e : gephi.graph.getEdges()) {
            boolean isDc = (Boolean)e.getAttribute(isDcColumn);
            if (isDc) {
                e.setColor(DC_COLOR);
            }
        }
    }

    public void colorizeNodes() {
        Column column = gephi.model.getNodeTable().getColumn("netActivePower");
        Function function = gephi.appearanceModel.getNodeFunction(gephi.graph, column, RankingElementColorTransformer.class);
        Ranking ranking = ((RankingFunction) function).getRanking();
        System.out.println("Colorize nodes:");
        System.out.println("   Ranking by net active power : min = " + ranking.getMinValue() + ", max = " + ranking.getMaxValue());

        RankingElementColorTransformer transformer = (RankingElementColorTransformer) function.getTransformer();
        transformer.setColors(new Color[] {NETP_GENERATION, NETP_ZERO, NETP_CONSUMPTION});
        transformer.setColorPositions(new float[] {0f, 0.5f, 1f});
        gephi.appearanceController.transform(function);
    }

    public void sizeNodes() {
        Column column = gephi.model.getNodeTable().getColumn("absActivePower");
        Function function = gephi.appearanceModel.getNodeFunction(gephi.graph, column, RankingNodeSizeTransformer.class);
        Ranking ranking = ((RankingFunction) function).getRanking();
        System.out.println("Size nodes:");
        System.out.println("   Ranking by abs active power : min = " + ranking.getMinValue() + ", max = " + ranking.getMaxValue());

        RankingNodeSizeTransformer transformer = (RankingNodeSizeTransformer) function.getTransformer();
        transformer.setMinSize(10);
        transformer.setMaxSize(200);
        gephi.appearanceController.transform(function);
    }

    private void addNetworkColumns() {
        gephi.model.getNodeTable().addColumn("netActivePower", Double.class);
        gephi.model.getNodeTable().addColumn("absActivePower", Double.class);
        gephi.model.getNodeTable().addColumn("maxNominalVoltage", Double.class);
        gephi.model.getEdgeTable().addColumn("nominalVoltage", Double.class);
        gephi.model.getEdgeTable().addColumn("isDC", Boolean.class);
    }

    private void addLines(List<Line> lines) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (Line line : lines) {
            Terminal t1 = line.getTerminal1();
            Terminal t2 = line.getTerminal2();
            double weight = 1 / line.getX();
            String label = line.getNameOrId();
            double nominalVoltage = t1.getVoltageLevel().getNominalV();
            Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
            e.setAttribute(isDcColumn, false);
        }
    }

    private void addDcLines(List<HvdcLine> dcLines) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (HvdcLine dcLine : dcLines) {
            Terminal t1 = dcLine.getConverterStation1().getTerminal();
            Terminal t2 = dcLine.getConverterStation2().getTerminal();
            double weight = 1 / dcLine.getR();
            String label = dcLine.getNameOrId();
            double nominalVoltage = dcLine.getNominalV();
            Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
            e.setAttribute(isDcColumn, true);
        }
    }

    private Edge addEdge(String label, Terminal t1, Terminal t2, double weight, double nominalVoltage) {
        Node node1 = gephi.addNode(Grid.substationId(t1), Grid.substationName(t1), this::calculateAttributes);
        Node node2 = gephi.addNode(Grid.substationId(t2), Grid.substationName(t2), this::calculateAttributes);
        Edge edge = gephi.addEdge(label, node1, node2, weight);
        edge.setAttribute(nominalVoltageColumn, nominalVoltage);
        return edge;
    }

    private void calculateAttributes(Node n) {
        Substation substation = network.getSubstation((String)n.getId());
        if (substation == null) {
            throw new RuntimeException("Missing substation " + n.getId());
        }
        double loadsP = substation.getVoltageLevelStream().flatMap(vl -> vl.getLoadStream()).mapToDouble(Load::getP0).sum();
        double gensP = substation.getVoltageLevelStream().flatMap(vl -> vl.getGeneratorStream()).mapToDouble(Generator::getTargetP).sum();
        n.setAttribute("netActivePower", loadsP - gensP);
        n.setAttribute("absActivePower", Math.abs(loadsP - gensP));
    }
}
