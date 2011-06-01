package org.openstreetmap.josm.plugins.curves;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class CurvesPlugin extends Plugin {
    
    private CurveAction action;
    
	public CurvesPlugin(PluginInformation info) {
		super(info);
	}
	
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null) {
            action = new CurveAction();
            MainMenu.add(Main.main.menu.toolsMenu, action);
        }
    }
	
	@Override
    public void preReloadCleanup() {
		
	}

}
