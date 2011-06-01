package org.openstreetmap.josm.plugins.curves;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

// TODO: investigate splines

public class CurveAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    public CurveAction() {
        super(tr("Curve") + CurveAction.class.hashCode(), null, tr("Create a curve"),
                Shortcut.registerShortcut("tools:createcurve", tr("Tool: {0}", tr("Create a curve")), KeyEvent.VK_C,
                        Shortcut.GROUP_EDIT, Shortcut.SHIFT_DEFAULT), true);
        putValue("help", ht("/Action/CreateCurve"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;

        Collection<Node> nodes = getCurrentDataSet().getSelectedNodes();
        Collection<Way> ways = getCurrentDataSet().getSelectedWays();
        Collection<Command> cmds = new LinkedList<Command>();

        Node n1, n2, n3;

        if (nodes.size() == 3) {
            Iterator<Node> nodeIter = nodes.iterator();
            n1 = nodeIter.next();
            n2 = nodeIter.next();
            n3 = nodeIter.next();
        } else if (ways.size() == 1) {
            // TODO: use only two nodes inferring the orientation from the parent way.
            // Use the three last nodes in the way as anchors. This is intended to be used with the
            // built in draw mode
            Way w = ways.iterator().next();
            int nodeCount = w.getNodesCount();
            if (nodeCount < 3)
                return;
            n3 = w.getNode(nodeCount - 1);
            n2 = w.getNode(nodeCount - 2);
            n1 = w.getNode(nodeCount - 3);
        } else {
            return;
        }
        EastNorth p1 = n1.getEastNorth();
        EastNorth p2 = n2.getEastNorth();
        EastNorth p3 = n3.getEastNorth();
        // TODO: Check that the points are distinct
        List<EastNorth> points = circleSeqmentPoints(p1, p2, p3, true, 10);

        Way way;
        int nodeI = -1;
        boolean makeNewWay = false;
        Way originalWay = null;
        List<Way> refs = OsmPrimitive.getFilteredList(n2.getReferrers(), Way.class);
        if (refs.size() == 1) { // TODO: handle multiple ways like in draw mode
            originalWay = refs.get(0);
            way = new Way(originalWay);
            nodeI = way.getNodes().indexOf(n1) + 1;
        } else {
            way = new Way();
            way.addNode(n1);
            nodeI = 1;
            makeNewWay = true;
        }
        for (EastNorth p : slice(points, 1, -2)) {
            if (p == p2) {
                if (makeNewWay) {
                    way.addNode(nodeI, n2);
                }
            } else {
                Node n = new Node(p);
                way.addNode(nodeI, n);
                cmds.add(new AddCommand(n));
            }
            nodeI++;
        }
        if (makeNewWay) {
            way.addNode(n3);
            cmds.add(new AddCommand(way));
        } else {
            cmds.add(new ChangeCommand(originalWay, way));
        }
        Main.main.undoRedo.add(new SequenceCommand("Create a curve", cmds));
    }

    // gah... why can't java support "reverse indexes"?
    protected static <T> List<T> slice(List<T> list, int from, int to) {
        if (to < 0)
            to += list.size() + 1;
        return list.subList(from, to);
    }

    @Override
    protected void updateEnabledState() {
        if (getCurrentDataSet() == null) {
            setEnabled(false);
        } else {
            updateEnabledState(getCurrentDataSet().getSelected());
        }
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        setEnabled(selection != null && !selection.isEmpty());
    }

    // TODO: support only two nodes, inferring the orientation from the parent way
    /**
     * Return a list of coordinates lying an the circle segment determined by n1, n2 and n3.
     * The order of the list and which of the 3 possible segments are given by the order of n1, n2, n3
     *
     * @param includeAnchors include the anchorpoints in the list. The original objects will be used, not copies
     */
    private static List<EastNorth> circleSeqmentPoints(EastNorth p1, EastNorth p2, EastNorth p3,
            boolean includeAnchors, int resolution) {

        // triangle: three single nodes needed or a way with three nodes

        // let's get some shorter names
        double x1 = p1.east();
        double y1 = p1.north();
        double x2 = p2.east();
        double y2 = p2.north();
        double x3 = p3.east();
        double y3 = p3.north();

        // calculate the center (xc,yc)
        double s = 0.5 * ((x2 - x3) * (x1 - x3) - (y2 - y3) * (y3 - y1));
        double sUnder = (x1 - x2) * (y3 - y1) - (y2 - y1) * (x1 - x3);

        assert (sUnder == 0);

        s /= sUnder;

        double xc = 0.5 * (x1 + x2) + s * (y2 - y1);
        double yc = 0.5 * (y1 + y2) + s * (x1 - x2);

        // calculate the radius (r)
        double r = Math.sqrt(Math.pow(xc - x1, 2) + Math.pow(yc - y1, 2));

        // The angles of the anchor points relative to the center
        double realA1 = calcang(xc, yc, x1, y1);
        double realA2 = calcang(xc, yc, x2, y2);
        double realA3 = calcang(xc, yc, x3, y3);

        double startAngle = realA1;
        // Transform the angles to get a consistent starting point
        double a1 = 0;
        double a2 = normalizeAngle(realA2 - startAngle);
        double a3 = normalizeAngle(realA3 - startAngle);
        double direction = a3 > a2 ? 1 : -1;

        double radialLength = 0;
        if (direction == 1) { // counter clockwise
            radialLength = a3;
        } else { // clockwise
            radialLength = Math.PI * 2 - a3;
            // make the angles consistent with the direction.
            a2 = Math.PI * 2 - a2;
            a3 = Math.PI * 2 - a3;
        }
        int numberOfNodesInCircle = (int) Math.ceil((radialLength / Math.PI) * 180 / resolution);
        List<EastNorth> points = new ArrayList<EastNorth>(numberOfNodesInCircle);

        // Calculate the circle points in order
        double a = direction * (radialLength * 1 / numberOfNodesInCircle);
        points.add(p1);
        // i is ahead of the real index by one, since we need to be ahead in the angle calculation
        for (int i = 2; i < numberOfNodesInCircle + 1; i++) {
            double nextA = direction * (radialLength * i / numberOfNodesInCircle);
            double realAngle = a + startAngle;
            double x = xc + r * Math.cos(realAngle);
            double y = yc + r * Math.sin(realAngle);

            // Convoluted(?) way of ensuring that n2 is replacing the closest point
            if (includeAnchors && a2 != 999 &&
                    (fuzzyMatch(a2, a) || (a2 < direction * a && !fuzzyMatch(a2, nextA)))) {
                points.add(p2);
                a2 = 999;
            }
            points.add(new EastNorth(x, y));
            a = nextA;
        }
        points.add(p3);
        return points;
    }

    private static boolean fuzzyMatch(double a1, double a2) {
        // return a1 <= a2;
        return Math.abs(a1 - a2) < (Math.PI / 360); // 0.5 degrees
    }

    /**
     * Normalizes {@code a} so it is between 0 and 2 PI
     */
    private static double normalizeAngle(double angle) {
        double PI2 = Math.PI * 2;
        if (angle < 0) {
            angle = angle + (Math.floor(-angle / PI2) + 1) * PI2;
        } else if (angle >= PI2) {
            angle = angle - Math.floor(angle / PI2) * PI2;
        }
        return angle;
    }

    private static double calcang(double xc, double yc, double x, double y) {
        // calculate the angle from xc|yc to x|y
        if (xc == x && yc == y)
            return 0; // actually invalid, but we won't have this case in this context
        double yd = Math.abs(y - yc);
        if (yd == 0 && xc < x)
            return 0;
        if (yd == 0 && xc > x)
            return Math.PI;
        double xd = Math.abs(x - xc);
        double a = Math.atan2(xd, yd);
        if (y > yc) {
            a = Math.PI - a;
        }
        if (x < xc) {
            a = -a;
        }
        a = 1.5 * Math.PI + a;
        if (a < 0) {
            a += 2 * Math.PI;
        }
        if (a >= 2 * Math.PI) {
            a -= 2 * Math.PI;
        }
        return a;
    }

    public static void main(String[] args) {
        System.out.println(calcang(0, 0, -1, -1) / Math.PI);
    }

}
