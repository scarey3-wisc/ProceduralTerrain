

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;


public abstract class WorldMapTool implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener
{
	private boolean d, z;
	protected WorldMap map;
	public WorldMapTool(boolean dragOkay, boolean zoomOkay, WorldMap myMap)
	{
		d = dragOkay;
		z = zoomOkay;
		map = myMap;
	}
	public RegionalMap GetTouchedRegionalMap(int mX, int mY)
	{
		RegionalMap.Coordinate found = map.GetRegionalMapAt(mX, mY);
		if(found == null)
			return null;
		return found.GetRegionalMap();
	}
	public SamplePoint GetTouchedPoly(int mX, int mY)
	{
		RegionalMap.Coordinate region = map.GetRegionalMapAt(mX, mY);
		if(region == null)
			return null;
		double wX = region.x + region.GetRegionalMap().GetWorldX();
		double wY = region.y + region.GetRegionalMap().GetWorldY();
		SamplePoint vp = region.GetRegionalMap().GetNearest(wX, wY);
		return vp;
	}
	public abstract void Activate();
	public abstract void Deactivate();
	public boolean ZoomOkay() { return z; } 
	public boolean DragOkay() { return d; }
}