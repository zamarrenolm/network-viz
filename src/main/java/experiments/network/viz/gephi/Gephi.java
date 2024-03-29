package experiments.network.viz.gephi;

import com.itextpdf.text.PageSize;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PDFExporter;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.layout.plugin.AbstractLayout;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.random.RandomLayout;
import org.gephi.layout.plugin.scale.ExpandLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.types.DependantOriginalColor;
import org.gephi.preview.types.EdgeColor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

public class Gephi {
    private static final int LAYOUT_MAX_ITERATIONS = 1000;
    private static final float YIFANHU_OPTIMAL_DISTANCE = 40f;

    private static final boolean DEBUG = false;
    private static final float DEFAULT_NODE_SIZE = 1.0f;
    public static final float MIN_NODE_SIZE = 1.0f;
    public static final float MAX_NODE_SIZE = 4.0f;
    private static final float DEFAULT_EDGE_THICKNESS = 0.5f;
    private static final float DEFAULT_NODE_BORDER_WIDTH = 0.2f;
    private static final int FONT_SIZE = 8;

    public AppearanceController appearanceController;
    public AppearanceModel appearanceModel;
    public GraphModel model;
    public UndirectedGraph graph;
    private ProjectController projectController;
    private Workspace workspace;
    private boolean useWeights = false;

    public Gephi() {
        init();
    }

    private static Font getNodeLabelFont() throws IOException, FontFormatException {
        //return Font.createFont(Font.TRUETYPE_FONT, new File("/System/Library/Fonts/Helvetica.ttc")).deriveFont(FONT_SIZE);
        return new Font("Lucida Sans", Font.PLAIN, FONT_SIZE);
    }

    private static boolean hasPosition(Node node) {
        return node.x() != 0 || node.y() != 0;
    }

    private static void moveCloseTo(Node node, Node ref) {
        float maxDistance = 100;
        float dx = (float) ((0.01 + Math.random()) * 2 * maxDistance) - maxDistance;
        float dy = (float) ((0.01 + Math.random()) * 2 * maxDistance) - maxDistance;
        node.setX(ref.x() + dx);
        node.setY(ref.y() + dy);
    }

    private void init() {
        projectController = Lookup.getDefault().lookup(ProjectController.class);
        projectController.newProject();
        workspace = projectController.getCurrentWorkspace();

        appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
        appearanceModel = appearanceController.getModel();

        model = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        graph = model.getUndirectedGraph();
    }

    public void setUseWeights(boolean useWeights) {
        this.useWeights = useWeights;
    }

    public Node addNode(String id, String label) {
        Node node = graph.getNode(id);
        if (node != null) {
            if (node.getLabel().equals(label)) {
                return node;
            } else {
                throw new RuntimeException("Noded " + id + " added twice with different labels, " + node.getLabel() + " <> " + label);
            }
        }
        node = model.factory().newNode(id);
        node.setLabel(label);
        node.setSize(DEFAULT_NODE_SIZE);
        graph.addNode(node);

        return node;
    }

    public Edge addEdge(String label, Node n0, Node n1) {
        return addEdge(label, n0, n1, Double.NaN);
    }

    public Edge addEdge(String label, Node n0, Node n1, double weight) {
        boolean directed = false;
        Edge edge = model.factory().newEdge(n0, n1, directed);
        edge.setLabel(label);
        if (Double.isFinite(weight) && useWeights) {
            edge.setWeight(weight);
        }
        graph.addEdge(edge);
        return edge;
    }

    public void layout(LayoutAlgorithm algorithm, boolean reset) {
        if (reset) {
            // This step is absolutely required for YifanHu first run
            AbstractLayout.ensureSafeLayoutNodePositions(model);
        }

        Layout layout = createLayout(algorithm);
        long t0 = System.currentTimeMillis();
        layout.initAlgo();
        int iteration;
        for (iteration = 0; iteration < LAYOUT_MAX_ITERATIONS && layout.canAlgo(); iteration++) {
            layout.goAlgo();
        }
        layout.endAlgo();
        long t1 = System.currentTimeMillis();
        info("Layout " + algorithm + " took " + (t1 - t0) + " ms, " + iteration + " iterations");
    }

    public void keepPositions() {
        for (Node node : graph.getNodes()) {
            node.setFixed(true);
        }
    }

    public void moveNewNodesCloseToLaidOutNeighbor() {
        boolean changed;
        int iteration = 0;
        int withoutPosition = 0;
        do {
            changed = false;
            withoutPosition = 0;
            for (Node node : graph.getNodes()) {
                if (node.isFixed()) {
                    continue;
                }
                if (hasPosition(node)) {
                    continue;
                }
                debug("no position for " + node.getLabel());
                withoutPosition++;
                for (Node neighbor : graph.getNeighbors(node)) {
                    debug("  neighbor " + neighbor.getLabel());
                    if (hasPosition(neighbor)) {
                        debug("  neighbor with position " + neighbor.getLabel());
                        moveCloseTo(node, neighbor);
                        changed = true;
                        break;
                    }
                }
            }
            iteration++;
            debug("iteration " + iteration + ", withoutPosition : " + withoutPosition);
        } while (changed);
    }

    public Layout createLayout(LayoutAlgorithm algorithm) {
        Layout layout = null;
        switch (algorithm) {
            case YIFANHU:
                YifanHuLayout yh = new YifanHuLayout(null, new StepDisplacement(10f));
                yh.resetPropertiesValues();
                yh.setOptimalDistance(YIFANHU_OPTIMAL_DISTANCE);
                yh.setConvergenceThreshold(1e-6f);
                yh.setRelativeStrength(0.6f);
                layout = yh;
                break;
            case ATLAS2:
                ForceAtlas2 fa2 = new ForceAtlas2(null);
                fa2.resetPropertiesValues();
                fa2.setBarnesHutOptimize(true);
                fa2.setAdjustSizes(false);
                fa2.setNormalizeEdgeWeights(true);
                layout = fa2;
                break;
            case ATLAS2_TOQUETEAR:
                ForceAtlas2Toquetear fa2i = new ForceAtlas2Toquetear(null);
                fa2i.resetPropertiesValues();
                fa2i.setBarnesHutOptimize(true);
                fa2i.setAdjustSizes(false);
                fa2i.setNormalizeEdgeWeights(true);
                layout = fa2i;
                break;
            case ATLAS2_NO_WEIGHT:
                ForceAtlas2 fa2now = new ForceAtlas2(null);
                fa2now.resetPropertiesValues();
                fa2now.setBarnesHutOptimize(true);
                fa2now.setEdgeWeightInfluence(0.0);
                layout = fa2now;
                break;
            case EXPANSION:
                // We could calculate the expansion factor based on the amount of nodes added in subNetworks
                // relative to the nodes in the backbone
                ExpandLayout ex = new ExpandLayout(null, 3);
                layout = ex;
                break;
            case RANDOM:
                layout = new RandomLayout(null, 200);
                break;
            default:
                throw new RuntimeException("missing layout algorithm " + algorithm);
        }
        if (layout != null) {
            layout.setGraphModel(model);
        }
        return layout;
    }

    public void export(Path path) throws IOException {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf");
        ec.exportFile(path.toFile(), exporter);
    }

    public void print(Path path) throws IOException, FontFormatException {
        PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
        PreviewProperties prop = model.getProperties();
        prop.putValue(PreviewProperty.SHOW_NODE_LABELS, true);
        prop.putValue(PreviewProperty.NODE_LABEL_COLOR, new DependantOriginalColor(Color.LIGHT_GRAY));
        prop.putValue(PreviewProperty.NODE_LABEL_FONT, getNodeLabelFont());
        prop.putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, true);
        prop.putValue(PreviewProperty.NODE_BORDER_WIDTH, DEFAULT_NODE_BORDER_WIDTH);
        prop.putValue(PreviewProperty.EDGE_COLOR, new EdgeColor(EdgeColor.Mode.ORIGINAL));
        prop.putValue(PreviewProperty.EDGE_THICKNESS, DEFAULT_EDGE_THICKNESS);
        prop.putValue(PreviewProperty.EDGE_CURVED, false);

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        PDFExporter pdfExporter = (PDFExporter) ec.getExporter("pdf");
        pdfExporter.setPageSize(PageSize.A4);
        pdfExporter.setLandscape(true);
        ec.exportFile(path.toFile(), pdfExporter);
    }

    private void debug(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }

    private void info(String message) {
        System.out.println(message);
    }

    public enum LayoutAlgorithm {
        YIFANHU, ATLAS2, ATLAS2_TOQUETEAR, ATLAS2_NO_WEIGHT, EXPANSION, RANDOM;
    }
}
