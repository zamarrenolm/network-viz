package experiments.network.viz;

import com.powsybl.iidm.network.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Just to handle what is the backbone and what is "below" the backbone in a Network

public class GridHierarchy {

    private final Network network;
    private Backbone backbone;

    public static String substationId(Terminal t) {
        return t.getVoltageLevel().getSubstation().map(Substation::getId).orElse("NETWORK");
    }

    public static String substationName(Terminal t) {
        String name = t.getVoltageLevel().getSubstation().map(Substation::getNameOrId).orElse("Network");
        if (name.equals("undefined")) {
            name = substationId(t);
        }
        return name;
    }

    public static class Backbone {
        public Backbone(List<Line> lines, List<HvdcLine> hvdcLines) {
            this.lines = lines;
            this.hvdcLines = hvdcLines;
        }

        public final List<Line> lines;
        public final List<HvdcLine> hvdcLines;
    }

    public GridHierarchy(Network network) {
        this.network = network;
    }

    public Backbone backbone() {
        if (backbone == null) {
            backbone = new Backbone(computeBackboneLines(), computeBackboneHvdcLines());
            fixAddMissingLines();
        }
        return backbone;
    }

    private void fixAddMissingLines() {
        // Connect "PONTE" - "M.PON" adding the lines "PONTE" - "REALT"
        fixAddMissingLine(network.getLine("PONTEL61REALT"));
        fixAddMissingLine(network.getLine("PONTEL62REALT"));

        // Connect ".TDCT 6_site"
        fixAddMissingLine(network.getLine(".CTLA 6 Z.CTL 1"));
        fixAddMissingLine(network.getLine("VALLOL61Z.CTL"));
        fixAddMissingLine(network.getLine("PRESSL61VALLO"));
        fixAddMissingLine(network.getLine("CORNIL61PRESS"));
    }

    private void fixAddMissingLine(Line line) {
        if (line != null) {
            backbone.lines.add(line);
        }
    }

    private List<Line> computeBackboneLines() {
        System.out.println("Computing backbone");
        Set<Double> allNominalVoltages = allNominalVoltages(network);
        System.out.println("  All nominal voltages       = " + allNominalVoltages);

        Set<Double> backboneNominalVoltages = backboneNominalVoltages(allNominalVoltages);
        System.out.println("  Backbone nominal voltages  = " + backboneNominalVoltages);

        List<Line> lines = network.getLineStream()
                .filter(l -> backboneNominalVoltages.contains(lineNominalVoltage(l)))
                .collect(Collectors.toList());
        System.out.println("  Backbone lines size        = " + lines.size());
        return lines;
    }

    private List<HvdcLine> computeBackboneHvdcLines() {
        // All HVDC lines considered part of the backbone
        return network.getHvdcLineStream().collect(Collectors.toList());
    }

    public List<Line> belowBackbone() {
        // The backbone connects subnetworks, but the word subnetwork has a specific meaning in IIDM
        System.out.println("Computing the lines below the backbone (lines not included in the backbone)");
        Set<String> backboneLineIds = backbone().lines.stream().map(Line::getId).collect(Collectors.toUnmodifiableSet());
        List<Line> subNetworksLines = network.getLineStream()
                .filter(line -> !backboneLineIds.contains(line))
                .collect(Collectors.toList());
        System.out.println("  belowBackbone lines size     = " + subNetworksLines.size());
        return subNetworksLines;
    }

    private static Set<Double> allNominalVoltages(Network network) {
        return network.getVoltageLevelStream().map(VoltageLevel::getNominalV).collect(Collectors.toSet());
    }

    private static Set<Double> backboneNominalVoltages(Set<Double> allNominalVoltages) {
        return Set.of(Collections.max(allNominalVoltages));
    }

    public static double lineNominalVoltage(Line line) {
        return line.getTerminal1().getVoltageLevel().getNominalV();
    }
}
