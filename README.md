# network-viz

Experiments with power systems visualizations.

Based on [PowSyBl](https://www.powsybl.org), an open source framework written in Java, that makes it easy to write complex software for power systems’ simulations and analysis.

Uses [Gephi toolkit](https://gephi.org/toolkit/), a standard Java library to work with Graphs and Graph Layouts.

## Next tasks

Yifan Hu is able to improve layout even if algo says it has converged. Iterate until no relevant changes are made.

If Network has one connected component and the backbone has more than one connected component, try to expand it including additional lines.

Consider _superconducting_ lines (very low impedance, X < 0.0125 ?):
 * Merge connected substations to a single node.
 * If they are in subnetworks, some nodes of the backbone may be _closer than they appear_.
