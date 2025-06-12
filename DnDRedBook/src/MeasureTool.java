import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class MeasureTool extends WorldMapTool
{
	private JLabel curHeightInfo;
	private JLabel curGradInfo;
	private HashSet<Integer> keysDown;
	public MeasureTool(WorldMap myMap, JPanel pan) {
		super(true, true, myMap);
		
		keysDown = new HashSet<Integer>();
		curHeightInfo = new JLabel("Current Height: ????");
		curGradInfo = new JLabel("Current Downhill: ????");
		JLabel calcDistHint = new JLabel("Shift-Left Click to recalculate minimum elevation");
		BoxLayout myLayout = new BoxLayout(pan, BoxLayout.PAGE_AXIS);
		curHeightInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
		curGradInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
		calcDistHint.setAlignmentX(Component.CENTER_ALIGNMENT);
		curHeightInfo.setBorder(BorderFactory.createLineBorder(Color.white, 5));
		curGradInfo.setBorder(BorderFactory.createLineBorder(Color.white, 5));
		calcDistHint.setBorder(BorderFactory.createLineBorder(Color.white, 5));
		

		pan.setLayout(myLayout);
		pan.add(curHeightInfo);
		pan.add(curGradInfo);
		pan.add(calcDistHint);
	}

	@Override
	public void mouseClicked(MouseEvent e) 
	{
		map.requestFocusInWindow();
		if(e.isShiftDown() && e.getButton() == MouseEvent.BUTTON1)
		{
			RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
			if(region == null)
				return;
			LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
			if(local.GetLocalMap() == null)
				return;
			
		}
		if(e.isControlDown() && e.getButton() == MouseEvent.BUTTON1)
		{
			if(keysDown.contains(KeyEvent.VK_S))
			{
				PrintSTL(1, 0.0005, false);
			}
			else if(keysDown.contains(KeyEvent.VK_R))
			{
				RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
				System.out.println("SEND THE RAIN");
				local.GetLocalMap().SendRandomRainErosion(5000);
				System.out.println("Done raining!");
			}
			else if(keysDown.contains(KeyEvent.VK_H))
			{
				RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
				ArrayList<LocalMap> target = new ArrayList<LocalMap>();
				target.add(local.GetLocalMap());
				LocalTerrainAlgorithms.GuaranteeConsistentHydrology(target, true);
			}
			else if(keysDown.contains(KeyEvent.VK_C))
			{
				RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
				LocalMap lm = local.GetLocalMap();
				System.out.println("50 Laplacians");
				lm.LaplacianErosionIteration(50);
				ArrayList<LocalMap> targets = new ArrayList<LocalMap>();
				targets.add(lm);
				System.out.println("Rain 1");
				lm.SendRandomRainErosion(10000);
				System.out.println("Hydrology 1");
				LocalTerrainAlgorithms.GuaranteeConsistentHydrology(targets, true);
				System.out.println("Rain 2");
				lm.SendRandomRainErosion(10000);
				System.out.println("Hydrology 2");
				LocalTerrainAlgorithms.GuaranteeConsistentHydrology(targets, true);
				System.out.println("Rainflow");
				for(LocalMap t : targets)
					t.SendEvenRain();
				LocalTerrainAlgorithms.SendRainDownhill(targets, true);
			}
			else if(keysDown.contains(KeyEvent.VK_L))
			{
				RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				//region.GetRegionalMap().RunFullLaplacianErosion();
				//region.GetRegionalMap().RunFullRain();
				LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
				//System.out.println("SEND THE RAIN");
				//local.GetLocalMap().SendRain(5000);
				//System.out.println("Done raining!");
				ArrayList<LocalMap> target = new ArrayList<LocalMap>();
				target.add(local.GetLocalMap());
				//LocalMap.SolveHydrology(target);
			}
			else if(keysDown.contains(KeyEvent.VK_T))
			{
				
				/*RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
				LocalMap center = local.GetLocalMap();
				ArrayList<LocalMap> targets = new ArrayList<LocalMap>();
				targets.add(center);
				targets.add(center.GetNorth());
				targets.add(center.GetWest());
				targets.add(center.GetSouth());
				targets.add(center.GetEast());
				System.out.println("Rainflow");
				for(LocalMap t : targets)
					t.SendEvenRain();
				LocalTerrainAlgorithms.SendRainDownhill(targets, true);*/
				
				
				RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
				if(region == null)
					return;
				region.GetRegionalMap().RunFullPhasedErosion();
			}
		}
		
	}
	public void PrintSTL(int samplePixelDist, double baseThickness, boolean longForm)
	{
		System.out.println("Beginning the Print STL2 procedure");
		int pixelWidth = map.getWidth();
		int pixelHeight = map.getHeight();
		int numSquaresWide = pixelWidth / samplePixelDist + 1;
		int numSquaresHigh = pixelHeight / samplePixelDist + 1;
		int numTriangles = 2; //the base
		numTriangles += 4 * numSquaresWide; //front and back sides
		numTriangles += 4 * numSquaresHigh; //left and right sides
		numTriangles += 2 * numSquaresWide * numSquaresHigh; //the top
		System.out.println("Triangle Print Statistics:");
		System.out.println("2 for the base");
		System.out.println(4 * numSquaresWide + " on the front and back");
		System.out.println(4 * numSquaresHigh + " on the left and right");
		System.out.println(2 * numSquaresWide * numSquaresHigh + " on the top");
		System.out.println("For a total of " + numTriangles);
		
		int pxS = 0;
		int pxE = (1 + pixelWidth / samplePixelDist) * samplePixelDist;
		int pyS = 0;
		int pyE = (1 + pixelHeight / samplePixelDist) * samplePixelDist;
		if((pixelWidth / samplePixelDist) * samplePixelDist == pixelWidth)
			pxE = pixelWidth;
		if((pixelHeight / samplePixelDist) * samplePixelDist == pixelHeight)
			pyE = pixelHeight;
		
		RegionalMap.Coordinate rcSS = map.GetRegionalMapAt(pxS, pyS);
		RegionalMap.Coordinate rcSE = map.GetRegionalMapAt(pxS, pyE);
		RegionalMap.Coordinate rcES = map.GetRegionalMapAt(pxE, pyS);
		RegionalMap.Coordinate rcEE = map.GetRegionalMapAt(pxE, pyE);
		
		if(rcSS == null || rcSE == null || rcES == null || rcEE == null)
		{
			System.out.println("Error printing stl - problems with the 4 corners");
			return;
		}
		
		double wxS = rcSS.x + rcSS.GetRegionalMap().GetWorldX();
		double wxE = rcEE.x + rcEE.GetRegionalMap().GetWorldX();
		double wyS = rcSS.y + rcSS.GetRegionalMap().GetWorldY();
		double wyE = rcEE.y + rcEE.GetRegionalMap().GetWorldY();
		wxS *= RegionalMap.DIMENSION;
		wxE *= RegionalMap.DIMENSION;
		wyS *= RegionalMap.DIMENSION;
		wyE *= RegionalMap.DIMENSION;
		double mxS = (wxS - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
		double mxE = (wxE - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
		double myS = (wyS - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
		double myE = (wyE - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
		
		double widthInMeters = mxE - mxS;
		double heightInMeters = myE - myS;
		double scale = 0;
		if(widthInMeters > heightInMeters)
			scale = 0.250 / widthInMeters;
		else
			scale = 0.250 / heightInMeters;
				
		double stlxS = mxS * scale;
		double stlxE = mxE * scale;
		double stlyS = myS * scale * -1;
		double stlyE = myE * scale * -1;
		
		
		File target = new File("Map.stl");
		DataOutputStream outputStl = null;
		PrintWriter outputWrt = null;
		try {
			if(longForm)
			{
				outputWrt = new PrintWriter(target);
				outputWrt.println("solid " + "Test_Solid");
			}
			else
			{
				outputStl = new DataOutputStream(new FileOutputStream(target));
				byte[] data = new byte[80];
				outputStl.write(data);
				outputStl.writeInt(Integer.reverseBytes(numTriangles));
			}
			
			//print 2 triangle base
			PrintMeshTriangle d1 = new PrintMeshTriangle(
					stlxS, stlyS, 0,
					stlxS, stlyE, 0,
					stlxE, stlyE, 0,
					0, 0, -1);
			if(longForm)
				d1.WriteLongForm(outputWrt, 1.0);
			else
				d1.WriteCompressedForm(outputStl, 1.0);
			PrintMeshTriangle d2 = new PrintMeshTriangle(
					stlxE, stlyE, 0,
					stlxE, stlyS, 0,
					stlxS, stlyS, 0,
					0, 0, -1);
			if(longForm)
				d1.WriteLongForm(outputWrt, 1.0);
			else
				d2.WriteCompressedForm(outputStl, 1.0);
			
			for(int i = 0; i < pixelWidth; i+= samplePixelDist) {
				int myPerc = (i * 100 / pixelWidth);
				int nxPerc = ((i + 1) * 100 / pixelWidth);
				if(myPerc != nxPerc)
					System.out.println(nxPerc + "% Complete");
				for(int j = 0; j < pixelHeight; j+= samplePixelDist) {
					int px0 = i;
					int px1 = i + samplePixelDist;
					int py0 = j;
					int py1 = j + samplePixelDist;
					
					RegionalMap.Coordinate rc00 = map.GetRegionalMapAt(px0, py0);
					RegionalMap.Coordinate rc01 = map.GetRegionalMapAt(px0, py1);
					RegionalMap.Coordinate rc10 = map.GetRegionalMapAt(px1, py0);
					RegionalMap.Coordinate rc11 = map.GetRegionalMapAt(px1, py1);
					if(rc00 == null || rc01 == null || rc10 == null || rc11 == null)
					{
						if(longForm)
							outputWrt.close();
						else
							outputStl.close();
						target.delete();
						System.out.println("Error printing stl - a coordinate was null");
						return;
					}
					double wx0 = rc00.x + rc00.GetRegionalMap().GetWorldX();
					double wx1 = rc11.x + rc11.GetRegionalMap().GetWorldX();
					double wy0 = rc00.y + rc00.GetRegionalMap().GetWorldY();
					double wy1 = rc11.y + rc11.GetRegionalMap().GetWorldY();
					wx0 *= RegionalMap.DIMENSION;
					wx1 *= RegionalMap.DIMENSION;
					wy0 *= RegionalMap.DIMENSION;
					wy1 *= RegionalMap.DIMENSION;
					
					double h00 = rc00.GetRegionalMap().GetElevation(wx0, wy0);
					double h01 = rc01.GetRegionalMap().GetElevation(wx0, wy1);
					double h10 = rc10.GetRegionalMap().GetElevation(wx1, wy0);
					double h11 = rc11.GetRegionalMap().GetElevation(wx1, wy1);
					
					if(h00 < 0) h00 = 0;
					if(h01 < 0) h01 = 0;
					if(h10 < 0) h10 = 0;
					if(h11 < 0) h11 = 0;
					
					if(h00 >= Double.MAX_VALUE / 8) h00 = 0;
					if(h01 >= Double.MAX_VALUE / 8) h01 = 0;
					if(h10 >= Double.MAX_VALUE / 8) h10 = 0;
					if(h11 >= Double.MAX_VALUE / 8) h11 = 0;
					
					double mx0 = (wx0 - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
					double mx1 = (wx1 - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
					double my0 = (wy0 - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;
					double my1 = (wy1 - RegionalMap.DIMENSION * RegionalMap.ORIGIN_OFFSET) * LocalMap.METER_DIM;

					double stlH00 = h00 * scale + baseThickness;
					if(h00 > 0)
						stlH00 += 10 * baseThickness;
					double stlH01 = h01 * scale + baseThickness;
					if(h01 > 0)
						stlH01 += 10 * baseThickness;
					double stlH10 = h10 * scale + baseThickness;
					if(h10 > 0)
						stlH10 += 10 * baseThickness;
					double stlH11 = h11 * scale + baseThickness;
					if(h11 > 0)
						stlH11 += 10 * baseThickness;
					
					double stlx0 = mx0 * scale;
					double stlx1 = mx1 * scale;
					double stly0 = my0 * scale * -1;
					double stly1 = my1 * scale * -1;
					
					//print the top triangles
					PrintMeshTriangle u1 = new PrintMeshTriangle(
							stlx0, stly0, stlH00,
							stlx0, stly1, stlH01,
							stlx1, stly1, stlH11,
							0, 0, 1);
					if(longForm)
						u1.WriteLongForm(outputWrt, 1.0);
					else
						u1.WriteCompressedForm(outputStl, 1.0);
					PrintMeshTriangle u2 = new PrintMeshTriangle(
							stlx1, stly1, stlH11,
							stlx1, stly0, stlH10,
							stlx0, stly0, stlH00,
							0, 0, 1);
					if(longForm)
						u2.WriteLongForm(outputWrt, 1.0);
					else
						u2.WriteCompressedForm(outputStl, 1.0);
					
					if(px0 == pxS)
					{
						//print the left triangles
						PrintMeshTriangle l1 = new PrintMeshTriangle(
								stlx0, stly0, 0,
								stlx0, stly1, 0,
								stlx0, stly1, stlH01,
								-1, 0, 0);
						if(longForm)
							l1.WriteLongForm(outputWrt, 1.0);
						else
							l1.WriteCompressedForm(outputStl, 1.0);
						PrintMeshTriangle l2 = new PrintMeshTriangle(
								stlx0, stly0, stlH00,
								stlx0, stly1, stlH01,
								stlx0, stly0, 0,
								-1, 0, 0);
						if(longForm)
							l2.WriteLongForm(outputWrt, 1.0);
						else
							l2.WriteCompressedForm(outputStl, 1.0);
					}
					if(px1 == pxE)
					{
						//print the right triangles
						PrintMeshTriangle r1 = new PrintMeshTriangle(
								stlx1, stly0, 0,
								stlx1, stly1, 0,
								stlx1, stly1, stlH11,
								1, 0, 0);
						if(longForm)
							r1.WriteLongForm(outputWrt, 1.0);
						else
							r1.WriteCompressedForm(outputStl, 1.0);
						PrintMeshTriangle r2 = new PrintMeshTriangle(
								stlx1, stly0, stlH10,
								stlx1, stly1, stlH11,
								stlx1, stly0, 0,
								1, 0, 0);
						if(longForm)
							r2.WriteLongForm(outputWrt, 1.0);
						else
							r2.WriteCompressedForm(outputStl, 1.0);
					}
					if(py0 == pyS)
					{
						//print the "facing negative Y" triangles
						//these are the triangles at the top of the map, but they'll be rendered
						//at the front of the model; that's something to fix,
						//because it means the entire thing is flipped vertically
						PrintMeshTriangle b1 = new PrintMeshTriangle(
								stlx0, stly0, 0,
								stlx1, stly0, 0,
								stlx1, stly0, stlH10,
								0, 1, 0);
						if(longForm)
							b1.WriteLongForm(outputWrt, 1.0);
						else
							b1.WriteCompressedForm(outputStl, 1.0);
						PrintMeshTriangle b2 = new PrintMeshTriangle(
								stlx0, stly0, stlH00,
								stlx1, stly0, stlH10,
								stlx0, stly0, 0,
								0, 1, 0);
						if(longForm)
							b2.WriteLongForm(outputWrt, 1.0);
						else
							b2.WriteCompressedForm(outputStl, 1.0);
					}
					if(py1 == pyE)
					{
						//print the "facing positive Y" triangles
						PrintMeshTriangle f1 = new PrintMeshTriangle(
								stlx0, stly1, 0,
								stlx1, stly1, 0,
								stlx1, stly1, stlH11,
								0, -1, 0);
						if(longForm)
							f1.WriteLongForm(outputWrt, 1.0);
						else
							f1.WriteCompressedForm(outputStl, 1.0);
						PrintMeshTriangle f2 = new PrintMeshTriangle(
								stlx0, stly1, stlH01,
								stlx1, stly1, stlH11,
								stlx0, stly1, 0,
								0, -1, 0);
						if(longForm)
							f2.WriteLongForm(outputWrt, 1.0);
						else
							f2.WriteCompressedForm(outputStl, 1.0);
					}
				}
			}
			if(longForm)
			{
				outputWrt.println("endsolid " + "Test_Solid");
				outputWrt.close();
			}
			else
				outputStl.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("All done printing an stl!");
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
	public void mouseMoved(MouseEvent e) 
	{
		
		RegionalMap.Coordinate region = map.GetRegionalMapAt(e.getX(), e.getY());
		if(region == null)
			return;
		double wX = region.x + region.GetRegionalMap().GetWorldX();
		double wY = region.y + region.GetRegionalMap().GetWorldY();
		wX *= RegionalMap.DIMENSION;
		wY *= RegionalMap.DIMENSION;

		double meters = region.GetRegionalMap().GetElevation(wX, wY);
		int feet10 = (int) (3.2808 * meters * 10);
		int feet = (int) (feet10 / 10);
		int dec = feet10 - 10 * feet;
		curHeightInfo.setText("Current Height: " + feet + "." + dec + " feet");
		
		
		
		Vec2 grad = region.GetRegionalMap().GetElevationGradient(wX, wY);
		double theta = Math.atan2(grad.x, grad.y);
		double slope = grad.Len();
		int deg = (int) (theta * 180 / Math.PI);
		curGradInfo.setText("Uphill: " + deg + "deg with a slope of " + slope);

		
	}

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
		keysDown.add(e.getKeyCode());
	}

	@Override
	public void keyReleased(KeyEvent e) {
		keysDown.remove(e.getKeyCode());
	}
	
}