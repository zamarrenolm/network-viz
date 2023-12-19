// This is copyrighted Atlas2, only to initialize with fixed poisitions

package experiments.network.viz.gephi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.gephi.graph.api.*;
import org.gephi.layout.plugin.forceAtlas2.*;
import org.gephi.layout.plugin.forceAtlas2.ForceFactory.AttractionForce;
import org.gephi.layout.plugin.forceAtlas2.ForceFactory.RepulsionForce;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

public class ForceAtlas2Toquetear implements Layout {

    private final ForceAtlas2Builder layoutBuilder;
    double outboundAttCompensation = 1;
    private GraphModel graphModel;
    private Graph graph;
    private double edgeWeightInfluence;
    private double jitterTolerance;
    private double scalingRatio;
    private double gravity;
    private double speed;
    private double speedEfficiency;
    private boolean outboundAttractionDistribution;
    private boolean adjustSizes;
    private boolean barnesHutOptimize;
    private double barnesHutTheta;
    private boolean linLogMode;
    private boolean normalizeEdgeWeights;
    private boolean strongGravityMode;
    private boolean invertedEdgeWeightsMode;
    private int threadCount;
    private int currentThreadCount;
    private Region rootRegion;
    private ExecutorService pool;

    public ForceAtlas2Toquetear(ForceAtlas2Builder layoutBuilder) {
        this.layoutBuilder = layoutBuilder;
        this.threadCount = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    public static void ensureSafeLayoutNodePositions(GraphModel graphModel) {
        Graph graph = graphModel.getGraph();
        NodeIterable nodesIterable = graph.getNodes();
        for (Node node : nodesIterable) {
            if (node.x() != 0 || node.y() != 0) {
                nodesIterable.doBreak();
                return;
            }
        }

        //All at 0.0, init some random positions
        nodesIterable = graph.getNodes();
        for (Node node : nodesIterable) {
            node.setX((float) ((0.01 + Math.random()) * 1000) - 500);
            node.setY((float) ((0.01 + Math.random()) * 1000) - 500);
        }
    }

    @Override
    public void initAlgo() {
        ensureSafeLayoutNodePositions(graphModel);

        speed = 1.;
        speedEfficiency = 1.;

        graph = graphModel.getGraphVisible();

        graph.readLock();
        try {
            Node[] nodes = graph.getNodes().toArray();

            // Initialise layout data
            for (Node n : nodes) {
                if (n.getLayoutData() == null || !(n.getLayoutData() instanceof ForceAtlas2LayoutData)) {
                    ForceAtlas2LayoutData nLayout = new ForceAtlas2LayoutData();
                    n.setLayoutData(nLayout);
                }
                ForceAtlas2LayoutData nLayout = n.getLayoutData();
                nLayout.mass = 1 + graph.getDegree(n);
                nLayout.old_dx = 0;
                nLayout.old_dy = 0;
                nLayout.dx = 0;
                nLayout.dy = 0;
            }

            pool = Executors.newFixedThreadPool(threadCount);
            currentThreadCount = threadCount;
        } finally {
            graph.readUnlockAll();
        }
    }

    private double getEdgeWeight(Edge edge, boolean isDynamicWeight, Interval interval) {
        double w = edge.getWeight();
        if (isDynamicWeight) {
            w = edge.getWeight(interval);
        }
        if (isInvertedEdgeWeightsMode()) {
            return w == 0 ? 0 : 1 / w;
        }
        return w;
    }

    @Override
    public void goAlgo() {
        // Initialize graph data
        if (graphModel == null) {
            return;
        }
        graph = graphModel.getGraphVisible();
        graph.readLock();
        boolean isDynamicWeight = graphModel.getEdgeTable().getColumn("weight").isDynamic();
        Interval interval = graph.getView().getTimeInterval();

        try {
            Node[] nodes = graph.getNodes().toArray();
            Edge[] edges = graph.getEdges().toArray();

            // Initialise layout data
            for (Node n : nodes) {
                if (n.getLayoutData() == null || !(n.getLayoutData() instanceof ForceAtlas2LayoutData)) {
                    ForceAtlas2LayoutData nLayout = new ForceAtlas2LayoutData();
                    n.setLayoutData(nLayout);
                }
                ForceAtlas2LayoutData nLayout = n.getLayoutData();
                nLayout.mass = 1 + graph.getDegree(n);
                nLayout.old_dx = nLayout.dx;
                nLayout.old_dy = nLayout.dy;
                nLayout.dx = 0;
                nLayout.dy = 0;
            }

            // If Barnes Hut active, initialize root region
            if (isBarnesHutOptimize()) {
                rootRegion = new Region(nodes);
                rootRegion.buildSubRegions();
            }

            // If outboundAttractionDistribution active, compensate.
            if (isOutboundAttractionDistribution()) {
                outboundAttCompensation = 0;
                for (Node n : nodes) {
                    ForceAtlas2LayoutData nLayout = n.getLayoutData();
                    outboundAttCompensation += nLayout.mass;
                }
                outboundAttCompensation /= nodes.length;
            }

            // Repulsion (and gravity)
            // NB: Muti-threaded
            RepulsionForce repulsion = ForceFactory.builder.buildRepulsion(isAdjustSizes(), getScalingRatio());

            int taskCount = 8 *
                    currentThreadCount;  // The threadPool Executor Service will manage the fetching of tasks and threads.
            // We make more tasks than threads because some tasks may need more time to compute.
            ArrayList<Future> threads = new ArrayList<>();
            for (int t = taskCount; t > 0; t--) {
                int from = (int) Math.floor(nodes.length * (t - 1) / taskCount);
                int to = (int) Math.floor(nodes.length * t / taskCount);
                Future future = pool.submit(
                        new NodesThread(nodes, from, to, isBarnesHutOptimize(), getBarnesHutTheta(), getGravity(),
                                isStrongGravityMode() ? (ForceFactory.builder.getStrongGravity(getScalingRatio())) :
                                        repulsion, getScalingRatio(), rootRegion, repulsion));
                threads.add(future);
            }
            for (Future future : threads) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException("Unable to layout " + this.getClass().getSimpleName() + ".", e);
                }
            }

            // Attraction
            AttractionForce attraction = ForceFactory.builder
                    .buildAttraction(isLinLogMode(), isOutboundAttractionDistribution(), isAdjustSizes(),
                            1 * (isOutboundAttractionDistribution() ? outboundAttCompensation : 1));
            if (getEdgeWeightInfluence() == 0) {
                for (Edge e : edges) {
                    attraction.apply(e.getSource(), e.getTarget(), 1);
                }
            } else if (getEdgeWeightInfluence() == 1) {
                if (isNormalizeEdgeWeights()) {
                    Double w;
                    Double edgeWeightMin = Double.MAX_VALUE;
                    Double edgeWeightMax = Double.MIN_VALUE;
                    for (Edge e : edges) {
                        w = getEdgeWeight(e, isDynamicWeight, interval);
                        edgeWeightMin = Math.min(w, edgeWeightMin);
                        edgeWeightMax = Math.max(w, edgeWeightMax);
                    }
                    if (edgeWeightMin < edgeWeightMax) {
                        for (Edge e : edges) {
                            w = (getEdgeWeight(e, isDynamicWeight, interval) - edgeWeightMin) / (edgeWeightMax - edgeWeightMin);
                            attraction.apply(e.getSource(), e.getTarget(), w);
                        }
                    } else {
                        for (Edge e : edges) {
                            attraction.apply(e.getSource(), e.getTarget(), 1.);
                        }
                    }
                } else {
                    for (Edge e : edges) {
                        attraction.apply(e.getSource(), e.getTarget(), getEdgeWeight(e, isDynamicWeight, interval));
                    }
                }
            } else {
                if (isNormalizeEdgeWeights()) {
                    Double w;
                    Double edgeWeightMin = Double.MAX_VALUE;
                    Double edgeWeightMax = Double.MIN_VALUE;
                    for (Edge e : edges) {
                        w = getEdgeWeight(e, isDynamicWeight, interval);
                        edgeWeightMin = Math.min(w, edgeWeightMin);
                        edgeWeightMax = Math.max(w, edgeWeightMax);
                    }
                    if (edgeWeightMin < edgeWeightMax) {
                        for (Edge e : edges) {
                            w = (getEdgeWeight(e, isDynamicWeight, interval) - edgeWeightMin) / (edgeWeightMax - edgeWeightMin);
                            attraction.apply(e.getSource(), e.getTarget(),
                                    Math.pow(w, getEdgeWeightInfluence()));
                        }
                    } else {
                        for (Edge e : edges) {
                            attraction.apply(e.getSource(), e.getTarget(), 1.);
                        }
                    }
                } else {
                    for (Edge e : edges) {
                        attraction.apply(e.getSource(), e.getTarget(),
                                Math.pow(getEdgeWeight(e, isDynamicWeight, interval), getEdgeWeightInfluence()));
                    }
                }
            }

            // Auto adjust speed
            double totalSwinging = 0d;  // How much irregular movement
            double totalEffectiveTraction = 0d;  // Hom much useful movement
            for (Node n : nodes) {
                ForceAtlas2LayoutData nLayout = n.getLayoutData();
                if (!n.isFixed()) {
                    double swinging =
                            Math.sqrt(Math.pow(nLayout.old_dx - nLayout.dx, 2) + Math.pow(nLayout.old_dy - nLayout.dy, 2));
                    totalSwinging += nLayout.mass *
                            swinging;   // If the node has a burst change of direction, then it's not converging.
                    totalEffectiveTraction += nLayout.mass * 0.5 *
                            Math.sqrt(Math.pow(nLayout.old_dx + nLayout.dx, 2) + Math.pow(nLayout.old_dy + nLayout.dy, 2));
                }
            }
            // We want that swingingMovement < tolerance * convergenceMovement

            // Optimize jitter tolerance
            // The 'right' jitter tolerance for this network. Bigger networks need more tolerance. Denser networks need less tolerance. Totally empiric.
            double estimatedOptimalJitterTolerance = 0.05 * Math.sqrt(nodes.length);
            double minJT = Math.sqrt(estimatedOptimalJitterTolerance);
            double maxJT = 10;
            double jt = jitterTolerance * Math.max(minJT,
                    Math.min(maxJT, estimatedOptimalJitterTolerance * totalEffectiveTraction / Math.pow(nodes.length, 2)));

            double minSpeedEfficiency = 0.05;

            // Protection against erratic behavior
            if (totalSwinging / totalEffectiveTraction > 2.0) {
                if (speedEfficiency > minSpeedEfficiency) {
                    speedEfficiency *= 0.5;
                }
                jt = Math.max(jt, jitterTolerance);
            }

            double targetSpeed = jt * speedEfficiency * totalEffectiveTraction / totalSwinging;

            // Speed efficiency is how the speed really corresponds to the swinging vs. convergence tradeoff
            // We adjust it slowly and carefully
            if (totalSwinging > jt * totalEffectiveTraction) {
                if (speedEfficiency > minSpeedEfficiency) {
                    speedEfficiency *= 0.7;
                }
            } else if (speed < 1000) {
                speedEfficiency *= 1.3;
            }

            // But the speed shoudn't rise too much too quickly, since it would make the convergence drop dramatically.
            double maxRise = 0.5;   // Max rise: 50%
            speed = speed + Math.min(targetSpeed - speed, maxRise * speed);

            // Apply forces
            if (isAdjustSizes()) {
                // If nodes overlap prevention is active, it's not possible to trust the swinging mesure.
                for (Node n : nodes) {
                    ForceAtlas2LayoutData nLayout = n.getLayoutData();
                    if (!n.isFixed()) {

                        // Adaptive auto-speed: the speed of each node is lowered
                        // when the node swings.
                        double swinging = nLayout.mass * Math.sqrt(
                                (nLayout.old_dx - nLayout.dx) * (nLayout.old_dx - nLayout.dx) +
                                        (nLayout.old_dy - nLayout.dy) * (nLayout.old_dy - nLayout.dy));
                        double factor = 0.1 * speed / (1f + Math.sqrt(speed * swinging));

                        double df = Math.sqrt(Math.pow(nLayout.dx, 2) + Math.pow(nLayout.dy, 2));
                        factor = Math.min(factor * df, 10.) / df;

                        double x = n.x() + nLayout.dx * factor;
                        double y = n.y() + nLayout.dy * factor;

                        n.setX((float) x);
                        n.setY((float) y);
                    }
                }
            } else {
                for (Node n : nodes) {
                    ForceAtlas2LayoutData nLayout = n.getLayoutData();
                    if (!n.isFixed()) {

                        // Adaptive auto-speed: the speed of each node is lowered
                        // when the node swings.
                        double swinging = nLayout.mass * Math.sqrt(
                                (nLayout.old_dx - nLayout.dx) * (nLayout.old_dx - nLayout.dx) +
                                        (nLayout.old_dy - nLayout.dy) * (nLayout.old_dy - nLayout.dy));
                        //double factor = speed / (1f + Math.sqrt(speed * swinging));
                        double factor = speed / (1f + Math.sqrt(speed * swinging));

                        double x = n.x() + nLayout.dx * factor;
                        double y = n.y() + nLayout.dy * factor;

                        n.setX((float) x);
                        n.setY((float) y);
                    }
                }
            }
        } finally {
            graph.readUnlockAll();
        }
    }

    @Override
    public boolean canAlgo() {
        return graphModel != null;
    }

    @Override
    public void endAlgo() {
        graph.readLock();
        try {
            for (Node n : graph.getNodes()) {
                n.setLayoutData(null);
            }
            pool.shutdown();
        } finally {
            graph.readUnlockAll();
        }
    }

    @Override
    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<>();
        final String forceAtlas2Tuning = NbBundle.getMessage(getClass(), "ForceAtlas2.tuning");
        final String forceAtlas2Behavior = NbBundle.getMessage(getClass(), "ForceAtlas2.behavior");
        final String forceAtlas2Performance = NbBundle.getMessage(getClass(), "ForceAtlas2.performance");
        final String forceAtlas2Threads = NbBundle.getMessage(getClass(), "ForceAtlas2.threads");

        try {
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.scalingRatio.name"),
                    forceAtlas2Tuning,
                    "ForceAtlas2.scalingRatio.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.scalingRatio.desc"),
                    "getScalingRatio", "setScalingRatio"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.strongGravityMode.name"),
                    forceAtlas2Tuning,
                    "ForceAtlas2.strongGravityMode.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.strongGravityMode.desc"),
                    "isStrongGravityMode", "setStrongGravityMode"));

            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.gravity.name"),
                    forceAtlas2Tuning,
                    "ForceAtlas2.gravity.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.gravity.desc"),
                    "getGravity", "setGravity"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.distributedAttraction.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.distributedAttraction.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.distributedAttraction.desc"),
                    "isOutboundAttractionDistribution", "setOutboundAttractionDistribution"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.linLogMode.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.linLogMode.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.linLogMode.desc"),
                    "isLinLogMode", "setLinLogMode"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.adjustSizes.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.adjustSizes.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.adjustSizes.desc"),
                    "isAdjustSizes", "setAdjustSizes"));

            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.edgeWeightInfluence.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.edgeWeightInfluence.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.edgeWeightInfluence.desc"),
                    "getEdgeWeightInfluence", "setEdgeWeightInfluence"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.normalizeEdgeWeights.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.normalizeEdgeWeights.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.normalizeEdgeWeights.desc"),
                    "isNormalizeEdgeWeights", "setNormalizeEdgeWeights"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.invertedEdgeWeightsMode.name"),
                    forceAtlas2Behavior,
                    "ForceAtlas2.invertedEdgeWeightsMode.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.invertedEdgeWeightsMode.desc"),
                    "isInvertedEdgeWeightsMode", "setInvertedEdgeWeightsMode"));

            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.jitterTolerance.name"),
                    forceAtlas2Performance,
                    "ForceAtlas2.jitterTolerance.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.jitterTolerance.desc"),
                    "getJitterTolerance", "setJitterTolerance"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.barnesHutOptimization.name"),
                    forceAtlas2Performance,
                    "ForceAtlas2.barnesHutOptimization.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.barnesHutOptimization.desc"),
                    "isBarnesHutOptimize", "setBarnesHutOptimize"));

            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.barnesHutTheta.name"),
                    forceAtlas2Performance,
                    "ForceAtlas2.barnesHutTheta.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.barnesHutTheta.desc"),
                    "getBarnesHutTheta", "setBarnesHutTheta"));

            properties.add(LayoutProperty.createProperty(
                    this, Integer.class,
                    NbBundle.getMessage(getClass(), "ForceAtlas2.threads.name"),
                    forceAtlas2Threads,
                    "ForceAtlas2.threads.name",
                    NbBundle.getMessage(getClass(), "ForceAtlas2.threads.desc"),
                    "getThreadsCount", "setThreadsCount"));

        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }

        return properties.toArray(new LayoutProperty[0]);
    }

    @Override
    public void resetPropertiesValues() {
        int nodesCount = 0;

        if (graphModel != null) {
            nodesCount = graphModel.getGraphVisible().getNodeCount();
        }

        // Tuning
        if (nodesCount >= 100) {
            setScalingRatio(2.0);
        } else {
            setScalingRatio(10.0);
        }
        setStrongGravityMode(false);
        setInvertedEdgeWeightsMode(false);
        setGravity(1.);

        // Behavior
        setOutboundAttractionDistribution(false);
        setLinLogMode(false);
        setAdjustSizes(false);
        setEdgeWeightInfluence(1.);
        setNormalizeEdgeWeights(false);

        // Performance
        setJitterTolerance(1d);
        setBarnesHutOptimize(nodesCount >= 1000);
        setBarnesHutTheta(1.2);
        setThreadsCount(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    @Override
    public LayoutBuilder getBuilder() {
        return layoutBuilder;
    }

    @Override
    public void setGraphModel(GraphModel graphModel) {
        this.graphModel = graphModel;
        // Trick: reset here to take the profile of the graph in account for default values
        resetPropertiesValues();
    }

    public Double getBarnesHutTheta() {
        return barnesHutTheta;
    }

    public void setBarnesHutTheta(Double barnesHutTheta) {
        this.barnesHutTheta = barnesHutTheta;
    }

    public Double getEdgeWeightInfluence() {
        return edgeWeightInfluence;
    }

    public void setEdgeWeightInfluence(Double edgeWeightInfluence) {
        this.edgeWeightInfluence = edgeWeightInfluence;
    }

    public Double getJitterTolerance() {
        return jitterTolerance;
    }

    public void setJitterTolerance(Double jitterTolerance) {
        this.jitterTolerance = jitterTolerance;
    }

    public Boolean isLinLogMode() {
        return linLogMode;
    }

    public void setLinLogMode(Boolean linLogMode) {
        this.linLogMode = linLogMode;
    }

    public Boolean isNormalizeEdgeWeights() {
        return normalizeEdgeWeights;
    }

    public void setNormalizeEdgeWeights(Boolean normalizeEdgeWeights) {
        this.normalizeEdgeWeights = normalizeEdgeWeights;
    }

    public Double getScalingRatio() {
        return scalingRatio;
    }

    public void setScalingRatio(Double scalingRatio) {
        this.scalingRatio = scalingRatio;
    }

    public Boolean isStrongGravityMode() {
        return strongGravityMode;
    }

    public void setStrongGravityMode(Boolean strongGravityMode) {
        this.strongGravityMode = strongGravityMode;
    }

    public Boolean isInvertedEdgeWeightsMode() {
        return invertedEdgeWeightsMode;
    }

    public void setInvertedEdgeWeightsMode(Boolean invertedEdgeWeightsMode) {
        this.invertedEdgeWeightsMode = invertedEdgeWeightsMode;
    }

    public Double getGravity() {
        return gravity;
    }

    public void setGravity(Double gravity) {
        this.gravity = gravity;
    }

    public Integer getThreadsCount() {
        return threadCount;
    }

    public void setThreadsCount(Integer threadCount) {
        this.threadCount = Math.max(1, threadCount);
    }

    public Boolean isOutboundAttractionDistribution() {
        return outboundAttractionDistribution;
    }

    public void setOutboundAttractionDistribution(Boolean outboundAttractionDistribution) {
        this.outboundAttractionDistribution = outboundAttractionDistribution;
    }

    public Boolean isAdjustSizes() {
        return adjustSizes;
    }

    public void setAdjustSizes(Boolean adjustSizes) {
        this.adjustSizes = adjustSizes;
    }

    public Boolean isBarnesHutOptimize() {
        return barnesHutOptimize;
    }

    public void setBarnesHutOptimize(Boolean barnesHutOptimize) {
        this.barnesHutOptimize = barnesHutOptimize;
    }
}
