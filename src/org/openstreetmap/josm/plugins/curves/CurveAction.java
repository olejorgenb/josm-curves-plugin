package org.openstreetmap.josm.plugins.curves;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

// TODO: investigate splines
// TODO: support only two nodes, inferring the orientation from the parent way


public class CurveAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    public CurveAction() {
        super(tr("Curve")+CurveAction.class.hashCode(), null, tr("Create a curve"),
                Shortcut.registerShortcut("tools:createcurve", tr("Tool: {0}", tr("Create a curve")), KeyEvent.VK_C, Shortcut.GROUP_EDIT, Shortcut.SHIFT_DEFAULT), true);
        putValue("help", ht("/Action/CreateCurve"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;
        int numberOfNodesInCircle = 50;
        List<Node> nodes = new ArrayList<Node>(
                getCurrentDataSet().getSelectedNodes());
        Collection<Command> cmds = new LinkedList<Command>();
        
        if(nodes.size() != 3)
            return;
        Way newWay = new Way();
        EastNorth p1 = nodes.get(0).getEastNorth();
        EastNorth p2 = nodes.get(1).getEastNorth();
        EastNorth p3 = nodes.get(2).getEastNorth();
        // Check the points are distinct
        List<EastNorth> points = circleSeqmentPoints(p1, p2, p3, true, 50);
        
        // @formatter:off
        for(EastNorth p : points) {
            if(p == p1 || p == p2 || p == p3) {
                if(p == p1)
                    newWay.addNode(nodes.get(0));
                else if(p == p2) 
                    newWay.addNode(nodes.get(1));
                else if(p == p3) 
                    newWay.addNode(nodes.get(2));
            } else {
                Node n = new Node(p);
                newWay.addNode(n);
                cmds.add(new AddCommand(n));
            }
        }
        // @formatter:on
        cmds.add(new AddCommand(newWay));
        Main.main.undoRedo.add(new SequenceCommand("Create a curve", cmds));
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
    private static List<EastNorth> circleSeqmentPoints(EastNorth n1, EastNorth n2, EastNorth n3,
            boolean includeAnchors, int resolution) {
        int numberOfNodesInCircle = resolution;
        // triangle: three single nodes needed or a way with three nodes

        List<EastNorth> points = new ArrayList<EastNorth>(numberOfNodesInCircle + 3);

        // let's get some shorter names
        double x1 = n1.east();
        double y1 = n1.north();
        double x2 = n2.east();
        double y2 = n2.north();
        double x3 = n3.east();
        double y3 = n3.north();

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
        double a1 = calcang(xc, yc, x1, y1);
        double a2 = calcang(xc, yc, x2, y2);
        double a3 = calcang(xc, yc, x3, y3);
        System.out.println(a2-a1);
        System.out.println(a3-a2);
        
        double radialLength = a3-a1;
        
        double startAngle = a1;
        int direction = a2-a1 >= 0 ? 1 : -1; // built-in sign function only for floating points?
        a2 += startAngle;
        a3 += startAngle;

        // Calculate the circle points in order
        // TODO: use anchorpoint if sufficient close
        for (int i = 0; i < numberOfNodesInCircle; i++) {
            double a = startAngle + direction*(radialLength * i / numberOfNodesInCircle);
            double x = xc + r * Math.cos(a);
            double y = yc + r * Math.sin(a);
            
            // insert existing nodes if they fit before this new node (999 means "already added this node")
            if (includeAnchors) {
                if (a1 <= a) {
                    points.add(n1);
                    a1 = 999;
                } else if (a2 <= a) {
                    points.add(n2);
                    a2 = 999;
                } else if (a3 <= a) {
                    points.add(n3);
                    a3 = 999;
                }
            }
            points.add(new EastNorth(x, y));
        }
        return points;
    }
    
    /**
     * Normalizes {@code a} so it is between 0 and 2 PI
     */
    private static double normalizeAngle(double angle) {
        double PI2 = Math.PI*2;
        if (angle < 0) {
            angle = angle + (-angle/PI2 + 1)*PI2;
        } else if (angle >= PI2) {
            angle = angle - (angle/PI2)*PI2;
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
