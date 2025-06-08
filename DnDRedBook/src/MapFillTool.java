import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;

import javax.swing.JLabel;

public class MapFillTool extends WorldMapTool
{
	private JLabel info;
	public MapFillTool(WorldMap myMap, JLabel i, ArrayList<SamplePoint> activeForElevation) {
		super(true, true, myMap);
		info = i;
		info.setText("Click empty space to generate");
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		RegionalMap.Coordinate found = map.GetRegionalMapAt(e.getX(), e.getY(), true);
		if(found.GetRegionalMap() == null)
			return;
		RegionalMap newMap = found.GetRegionalMap();
		newMap.EnableRendering();
	}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mouseDragged(MouseEvent e) {}

	@Override
	public void mouseMoved(MouseEvent e) {}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {}

	@Override
	public void Activate() {}

	@Override
	public void Deactivate() {}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}
	
}