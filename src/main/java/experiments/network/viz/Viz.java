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
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

public class Viz {
    // Colors from ENTSO-E map of Continental Europe
    private static final Color ENTSOE_BLUE = new Color(27, 78, 162);
    private static final Color ENTSOE_MAROON = new Color(169, 41, 71);
    private static final Color ENTSOE_RED = new Color(235, 51, 35);
    private static final Color ENTSOE_ORANGE = new Color(243, 169, 60);
    private static final Color ENTSOE_GREEN = new Color(90, 165, 48);
    private static final Color ENTSOE_BLACK = new Color(0, 0, 0);
    private static final Color ENTSOE_PINK = new Color(223, 137, 232);
    private static final double[] AC_NOMINAL_VOLTAGES = {0, 110, 220, 300, 380, 500, 750};
    private static final Color[] AC_NOMINAL_VOLTAGE_COLORS = {Color.LIGHT_GRAY, ENTSOE_BLACK, ENTSOE_GREEN, ENTSOE_ORANGE, ENTSOE_RED, ENTSOE_MAROON, ENTSOE_BLUE};
    private static final Color DC_COLOR = ENTSOE_PINK;
    private static final Color NETP_GENERATION = new Color(255, 145, 61);
    private static final Color NETP_ZERO = new Color(255, 255, 255);
    private static final Color NETP_CONSUMPTION = new Color(188, 238, 98);

    protected static final boolean EXPORT_GEXF = false;
    private static final boolean PERFORM_ADDITIONAL_EXPANSION_AFTER_LAYOUT = false;

    protected final Network network;
    private final Predicate<VoltageLevel> voltageLevelFilter;
    private final Viz.WeightInterpretation weightInterpretation;
    protected final Gephi gephi;
    private final Column nominalVoltageColumn;

    public Viz(Network network, Predicate<VoltageLevel> voltageLevelFilter, boolean useWeights, Viz.WeightInterpretation weightInterpretation) {
        this.network = network;
        this.voltageLevelFilter = voltageLevelFilter;
        this.weightInterpretation = weightInterpretation;
        gephi = new Gephi();
        gephi.setUseWeights(useWeights);
        addNetworkColumns();
        nominalVoltageColumn = gephi.model.getEdgeTable().getColumn("nominalVoltage");
    }

    protected static void prepareOutputFolder(Path outputFolder) throws IOException {
        Files.createDirectories(outputFolder);
        FileUtils.cleanDirectory(outputFolder.toFile());
    }

    public void createDiagram(Path outputFolder, String name, Gephi.LayoutAlgorithm layoutAlgorithm) throws IOException, FontFormatException {
        addLines(network.getLineStream().toList());
        addTieLines(network.getTieLineStream().toList());
        add2wTransformers(network.getTwoWindingsTransformerStream().toList());
        add3wTransformers(network.getThreeWindingsTransformerStream().toList());
        addDcLines(network.getHvdcLineStream().toList());

        colorizeNodes();
        sizeNodes();
        colorizeEdges();

        layoutAndExport(layoutAlgorithm, outputFolder, name);
        if (PERFORM_ADDITIONAL_EXPANSION_AFTER_LAYOUT) {
            // In case we want to do an additional expansion
            String name1 = name + "-" + layoutAlgorithm.name();
            layoutAndExport(Gephi.LayoutAlgorithm.EXPANSION, outputFolder, name1, false);
        }
    }

    protected void layoutAndExport(Gephi.LayoutAlgorithm algorithm, Path outputFolder, String part) throws IOException, FontFormatException {
        layoutAndExport(algorithm, outputFolder, part, true);
    }

    protected void layoutAndExport(Gephi.LayoutAlgorithm algorithm, Path outputFolder, String part, boolean reset) throws IOException, FontFormatException {
        gephi.layout(algorithm, reset);
        if (EXPORT_GEXF) {
            gephi.export(outputFolder.resolve(part + "-" + algorithm + ".gexf"));
        }
        gephi.print(outputFolder.resolve(part + "-" + algorithm + ".pdf"));
    }

    public void colorizeNodes() {
        System.out.println("Colorize nodes:");
        Function colorTransformer;
        String colorizeNodes = "byVoltageLevel";
        switch (colorizeNodes) {
            case "byNetActivePower":
                colorTransformer = byNetActivePower();
                break;
            case "byVoltageLevel":
                colorTransformer = byNodeVoltageLevel();
                break;
            default:
                return;
        }
        if (colorTransformer != null) {
            gephi.appearanceController.transform(colorTransformer);
        }
    }

    Function byNodeVoltageLevel() {
        Column column = gephi.model.getNodeTable().getColumn("nominalVoltage");

        // Gephi Toolkit 0.9.2
        // Function function = gephi.appearanceModel.getNodeFunction(gephi.graph, column, PartitionElementColorTransformer.class);
        // Gephi Toolkit 0.9.3
        Function function = gephi.appearanceModel.getNodeFunction(column, PartitionElementColorTransformer.class);
        assignVoltageLevelColors(function);
        return function;
    }

    Function byNetActivePower() {
        Column column = gephi.model.getNodeTable().getColumn("netActivePower");

        // Gephi Toolkit 0.9.2
        // Function function = gephi.appearanceModel.getNodeFunction(gephi.graph, column, RankingElementColorTransformer.class);
        // Gephi Toolkit 0.9.3
        Function function = gephi.appearanceModel.getNodeFunction(column, RankingElementColorTransformer.class);

        Ranking ranking = ((RankingFunction) function).getRanking();

        // Gephi Toolkit 0.9.2
        //System.out.println("   Ranking by net active power : min = " + ranking.getMinValue() + ", max = " + ranking.getMaxValue());
        // Gephi Toolkit 0.9.3
        System.out.println("   Ranking by net active power : min = " + ranking.getMinValue(gephi.graph) + ", max = " + ranking.getMaxValue(gephi.graph));

        RankingElementColorTransformer transformer = (RankingElementColorTransformer) function.getTransformer();
        transformer.setColors(new Color[]{NETP_GENERATION, NETP_ZERO, NETP_CONSUMPTION});
        transformer.setColorPositions(new float[]{0f, 0.5f, 1f});
        return function;
    }

    public void colorizeEdges() {
        System.out.println("Colorize edges:");
        Column column = gephi.model.getEdgeTable().getColumn("nominalVoltage");

        // Gephi Toolkit 0.9.2
        // Function function = gephi.appearanceModel.getEdgeFunction(gephi.graph, column, PartitionElementColorTransformer.class);
        // Gephi Toolkit 0.9.3
        Function function = gephi.appearanceModel.getEdgeFunction(column, PartitionElementColorTransformer.class);
        if (function == null) {
            return;
        }

        assignVoltageLevelColors(function);
        gephi.appearanceController.transform(function);
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (Edge e : gephi.graph.getEdges()) {
            boolean isDc = (Boolean) e.getAttribute(isDcColumn);
            if (isDc) {
                e.setColor(DC_COLOR);
            }
        }
    }

    private void assignVoltageLevelColors(Function function) {
        if (function == null) {
            return;
        }
        Partition partition = ((PartitionFunction) function).getPartition();

        // Gephi Toolkit 0.9.2
        // System.out.println("   Partition by nominal voltage : " + partition.size() + " " + partition.getValues());
        // Gephi Toolkit 0.9.3
        System.out.println("   Partition by nominal voltage : " + partition.size(gephi.graph) + " " + partition.getValues(gephi.graph));

        // Gephi Toolkit 0.9.2
        // for (Object v : partition.getValues()) {
        // Gephi Toolkit 0.9.3
        for (Object v : partition.getValues(gephi.graph)) {
            double nominalVoltage = (Double) v;
            int kc = 0;
            for (kc = 0; kc < AC_NOMINAL_VOLTAGES.length && nominalVoltage >= AC_NOMINAL_VOLTAGES[kc]; kc++) {
                partition.setColor(nominalVoltage, AC_NOMINAL_VOLTAGE_COLORS[kc]);
            }
        }
    }

    public void sizeNodes() {
        Column column = gephi.model.getNodeTable().getColumn("absActivePower");

        // Gephi Toolkit 0.9.2
        // Function function = gephi.appearanceModel.getNodeFunction(gephi.graph, column, RankingNodeSizeTransformer.class);
        // Gephi Toolkit 0.9.3
        Function function = gephi.appearanceModel.getNodeFunction(column, RankingNodeSizeTransformer.class);

        Ranking ranking = ((RankingFunction) function).getRanking();
        System.out.println("Size nodes:");
        // Gephi Toolkit 0.9.2
        // System.out.println("   Ranking by abs active power : min = " + ranking.getMinValue() + ", max = " + ranking.getMaxValue());
        // Gephi Toolkit 0.9.3
        System.out.println("   Ranking by abs active power : min = " + ranking.getMinValue(gephi.graph) + ", max = " + ranking.getMaxValue(gephi.graph));

        RankingNodeSizeTransformer transformer = function.getTransformer();
        transformer.setMinSize(Gephi.MIN_NODE_SIZE);
        transformer.setMaxSize(Gephi.MAX_NODE_SIZE);
        gephi.appearanceController.transform(function);
    }

    private void addNetworkColumns() {
        gephi.model.getNodeTable().addColumn("netActivePower", Double.class);
        gephi.model.getNodeTable().addColumn("absActivePower", Double.class);
        gephi.model.getNodeTable().addColumn("nominalVoltage", Double.class);
        gephi.model.getEdgeTable().addColumn("nominalVoltage", Double.class);
        gephi.model.getEdgeTable().addColumn("isDC", Boolean.class);
    }

    protected void addLines(List<Line> lines) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (Line line : lines) {
            Terminal t1 = line.getTerminal1();
            Terminal t2 = line.getTerminal2();
            if (accepted(t1, t2)) {
                double weight = weight(line.getX());
                String label = line.getNameOrId();
                double nominalVoltage = t1.getVoltageLevel().getNominalV();
                Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
                e.setAttribute(isDcColumn, false);
            }
        }
    }

    protected void addTieLines(List<TieLine> tieLines) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (TieLine line : tieLines) {
            Terminal t1 = line.getTerminal1();
            Terminal t2 = line.getTerminal2();
            if (accepted(t1, t2)) {
                double weight = weight(line.getX());
                String label = line.getNameOrId();
                double nominalVoltage = t1.getVoltageLevel().getNominalV();
                Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
                e.setAttribute(isDcColumn, false);
            }
        }
    }

    private void add2wTransformers(List<TwoWindingsTransformer> transformers) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (TwoWindingsTransformer transformer : transformers) {
            Terminal t1 = transformer.getTerminal1();
            Terminal t2 = transformer.getTerminal2();
            if (accepted(t1, t2)) {
                double weight = weight(transformer.getX());
                String label = transformer.getNameOrId();
                double nominalVoltage = t1.getVoltageLevel().getNominalV();
                Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
                e.setAttribute(isDcColumn, false);
            }
        }
    }

    private void add3wTransformers(List<ThreeWindingsTransformer> transformers) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (ThreeWindingsTransformer transformer : transformers) {
            Terminal t1 = transformer.getLeg1().getTerminal();
            Terminal t2 = transformer.getLeg2().getTerminal();
            Terminal t3 = transformer.getLeg3().getTerminal();
            if (accepted(t1, t2, t3)) {
                Node node0 = new NodeAdder3wInnerNode().addNode(transformer);
                String label = transformer.getNameOrId();

                double weight1 = weight(transformer.getLeg1().getX());
                addEdge(label, t1, node0, weight1, t1.getVoltageLevel().getNominalV()).setAttribute(isDcColumn, false);

                double weight2 = weight(transformer.getLeg2().getX());
                addEdge(label, t2, node0, weight2, t2.getVoltageLevel().getNominalV()).setAttribute(isDcColumn, false);

                double weight3 = weight(transformer.getLeg3().getX());
                addEdge(label, t3, node0, weight3, t3.getVoltageLevel().getNominalV()).setAttribute(isDcColumn, false);
            }
        }
    }

    protected void addDcLines(List<HvdcLine> dcLines) {
        Column isDcColumn = gephi.model.getEdgeTable().getColumn("isDC");
        for (HvdcLine dcLine : dcLines) {
            Terminal t1 = dcLine.getConverterStation1().getTerminal();
            Terminal t2 = dcLine.getConverterStation2().getTerminal();
            if (accepted(t1, t2)) {
                double weight = weight(dcLine.getR());
                String label = dcLine.getNameOrId();
                double nominalVoltage = dcLine.getNominalV();
                Edge e = addEdge(label, t1, t2, weight, nominalVoltage);
                e.setAttribute(isDcColumn, true);
            }
        }
    }

    private double weight(double impedance) {
        return switch (weightInterpretation) {
            case WEIGHT_IS_IMPEDANCE -> Math.max(0.01, impedance);
            case WEIGHT_IS_ADMITTANCE -> Math.max(0.01, 1.0 / impedance);
        };
    }

    private boolean accepted(Terminal... ts) {
        for (Terminal t : ts) {
            if (voltageLevelFilter.test(t.getVoltageLevel())) {
                return true;
            }
        }
        return false;
    }

    public enum WeightInterpretation {
        WEIGHT_IS_IMPEDANCE, WEIGHT_IS_ADMITTANCE

    }

    interface NodeAdder {
        default Node addNode() {
            throw new RuntimeException("not implemented");
        }

        default Node addNode(Terminal t) {
            throw new RuntimeException("not implemented");
        }
    }

    class NodeAdderSubstation implements NodeAdder {
        public Node addNode(Terminal t) {
            return calcAttributes(gephi.addNode(GridHierarchy.substationId(t), GridHierarchy.substationName(t)));
        }

        private Node calcAttributes(Node n) {
            Substation substation = network.getSubstation((String) n.getId());
            if (substation == null) {
                throw new RuntimeException("Missing substation " + n.getId());
            }
            double loadsP = substation.getVoltageLevelStream().flatMap(VoltageLevel::getLoadStream).mapToDouble(Load::getP0).sum();
            double gensP = substation.getVoltageLevelStream().flatMap(VoltageLevel::getGeneratorStream).mapToDouble(Generator::getTargetP).sum();
            double maxNominalVoltage = substation.getVoltageLevelStream()
                    .map(VoltageLevel::getNominalV)
                    .max(Double::compare)
                    .orElse(0.0);
            n.setAttribute("netActivePower", loadsP - gensP);
            n.setAttribute("absActivePower", Math.abs(loadsP - gensP));
            n.setAttribute("nominalVoltage", maxNominalVoltage);
            return n;
        }
    }

    class NodeAdderVoltageLevel implements NodeAdder {
        public Node addNode(Terminal t) {
            String label = GridHierarchy.substationName(t) + " " + t.getVoltageLevel().getNameOrId();
            return calcAttributes(gephi.addNode(t.getVoltageLevel().getId(), label));
        }

        private Node calcAttributes(Node n) {
            VoltageLevel vl = network.getVoltageLevel((String) n.getId());
            double loadsP = vl.getLoadStream().mapToDouble(Load::getP0).sum();
            double gensP = vl.getGeneratorStream().mapToDouble(Generator::getTargetP).sum();
            n.setAttribute("netActivePower", loadsP - gensP);
            n.setAttribute("absActivePower", Math.abs(loadsP - gensP));
            n.setAttribute("nominalVoltage", vl.getNominalV());
            return n;
        }
    }

    class NodeAdder3wInnerNode implements NodeAdder {
        public Node addNode(ThreeWindingsTransformer t) {
            String label = "";
            Node n = gephi.addNode(t.getId(), label);
            n.setAttribute("netActivePower", 0.0);
            n.setAttribute("absActivePower", 0.0);
            n.setAttribute("nominalVoltage", 0.0);
            return n;
        }
    }

    private Edge addEdge(String label, Terminal t1, Terminal t2, double weight, double nominalVoltage) {
        // NodeAdder nodeAdder = new NodeAdderSubstation();
        NodeAdder nodeAdder = new NodeAdderVoltageLevel();
        Node node1 = nodeAdder.addNode(t1);
        Node node2 = nodeAdder.addNode(t2);
        Edge edge = gephi.addEdge(label, node1, node2, weight);
        edge.setAttribute(nominalVoltageColumn, nominalVoltage);
        return edge;
    }

    private Edge addEdge(String label, Terminal t1, Node node0, double weight, double nominalVoltage) {
        // NodeAdder nodeAdder = new NodeAdderSubstation();
        NodeAdder nodeAdder = new NodeAdderVoltageLevel();
        Node node1 = nodeAdder.addNode(t1);
        Edge edge = gephi.addEdge(label, node1, node0, weight);
        edge.setAttribute(nominalVoltageColumn, nominalVoltage);
        return edge;
    }
}
