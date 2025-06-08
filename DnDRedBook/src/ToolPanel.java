import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ToolPanel extends JPanel
{
	private JPanel activeTool;
	private JPanel toolSelector;
	private static final long serialVersionUID = 1L;
	private WorldMap map;
	public ToolPanel(WorldMap m)
	{
		super(new BorderLayout());
		map = m;
		
		activeTool = new JPanel();
		toolSelector = new JPanel(new GridLayout(0, 4));
		this.setBackground(Color.black);
		toolSelector.setBackground(Color.gray);
		activeTool.setBackground(Color.white);
		add(toolSelector, BorderLayout.NORTH);
		add(activeTool, BorderLayout.CENTER);
		CreateMapExpansionTools();
		
		//CreateOceanTool();
		SwapTool s = CreateHeightMeasureTool();
		CreateEmptyTool();
		s.ActivateTool();
	}
	private void CreateMapExpansionTools()
	{
		JButton expand = new JButton("Expand Map");
		toolSelector.add(expand);
		JPanel measureTool = new JPanel();
		measureTool.setBackground(Color.white);
		JLabel myHeight = new JLabel("?????");
		measureTool.add(myHeight);
		MapFillTool my = new MapFillTool(map, myHeight, null);
		SwapTool swa = new SwapTool(measureTool, my);
		expand.addActionListener(swa);
	}
	private SwapTool CreateEmptyTool()
	{
		JButton nothing = new JButton("Nothing");
		toolSelector.add(nothing);
		JPanel nothingTool = new JPanel();
		nothingTool.setBackground(Color.red);
		SwapTool swa = new SwapTool(nothingTool, null);
		nothing.addActionListener(swa);
		return swa;
	}
	private SwapTool CreateHeightMeasureTool()
	{
		JButton measure = new JButton("Measure");
		toolSelector.add(measure);
		JPanel measureTool = new JPanel();
		measureTool.setBackground(Color.white);
		MeasureTool my = new MeasureTool(map, measureTool);
		SwapTool swa = new SwapTool(measureTool, my);
		measure.addActionListener(swa);
		return swa;
	}
	private class ExpandWorldMap implements ActionListener
	{
		private boolean w, e, n, s;
		public ExpandWorldMap(String direction)
		{
			w = false;
			e = false;
			n = false;
			s = false;
			char first = direction.charAt(0);
			if(first == 'W' || first == 'w')
				w = true;
			if(first == 'E' || first == 'e')
				e = true;
			if(first == 'N' || first == 'n')
				n = true;
			if(first == 'S' || first == 's')
				s = true;
		}
		@Override
		public void actionPerformed(ActionEvent event) {
			ArrayList<RegionalMap> newMaps = new ArrayList<RegionalMap>();
			/*if(w)
			{
				ArrayList<RegionalMap> nM = map.ExpandWorld(0, 1, 0, 0);
				newMaps.addAll(nM);
			}
			if(e)
			{
				ArrayList<RegionalMap> nM = map.ExpandWorld(1, 0, 0, 0);
				newMaps.addAll(nM);
			}
			if(n)
			{
				ArrayList<RegionalMap> nM = map.ExpandWorld(0, 0, 0, 1);
				newMaps.addAll(nM);
			}
			if(s)
			{
				ArrayList<RegionalMap> nM = map.ExpandWorld(0, 0, 1, 0);
				newMaps.addAll(nM);
			}*/
			for(RegionalMap m : newMaps)
			{
				m.EnableRendering();
			}
		}
	}
	private class SwapTool implements ActionListener
	{
		private JPanel myToolPanel;
		private WorldMapTool theTool;
		public SwapTool(JPanel panel, WorldMapTool tool)
		{
			myToolPanel = panel;
			theTool = tool;
		}
		@Override
		public void actionPerformed(ActionEvent e) 
		{
			ActivateTool();
		}
		public void ActivateTool()
		{
			if(activeTool == myToolPanel)
				return;
			map.DettachActiveTool();
			ToolPanel.this.remove(activeTool);
			ToolPanel.this.add(myToolPanel, BorderLayout.CENTER);
			ToolPanel.this.updateUI();
			activeTool = myToolPanel;
			map.AttachTool(theTool);
		}
	}
}