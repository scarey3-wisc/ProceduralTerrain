import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;

/*
 * A WorldMap is the outermost layer of maps, representing
 * the entire world in a grid of RegionalMaps. Each Regional
 * map represents a 200km x 200km square. A WorldMap
 * may therefore contain only a handful of RegionalMaps, or
 * it may contain a fair number - but in any case a WorldMap
 * needs the ability to "Expand", tacking on more RegionalMaps
 */
public class WorldMap extends DraggableJPanel implements RedBook.RenderPanel
{
	private static final long serialVersionUID = 1L;
	private int x0, y0; //which cell in regions counts as "0,0"? This may change as we expand!
	private int w, h; //how many cells do we have?
	private RegionalMap[][] regions;
	private int tileSize;
	private WorldMapTool activeTool;
	private ZoomUpdater myZoom;
	private String worldName;
	public boolean FileAvailable;
	public WorldMap(String name)
	{
		this.worldName = name;
		tileSize = DEFAULT_TILE_SIZE;
		File topDir = new File(GetDirectory());
		if(topDir.exists() && topDir.isDirectory())
		{
			FileAvailable = true;
		}
		else
		{
			FileAvailable = false;
			topDir.mkdir();
			InitNewWorld();
		}
	}
	public String GetDirectory()
	{
		return K_SAVE_FOLDER_NAME + File.separator + worldName;
	}
	public void DeleteWorld()
	{
		try (var dirStream = Files.walk(Paths.get(GetDirectory()))) {
		    dirStream
		        .map(Path::toFile)
		        .sorted(Comparator.reverseOrder())
		        .forEach(File::delete);
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileAvailable = false;
	}
	public void LoadWorld()
	{
		LoadPerlinData();
		LoadRegionGridInfo();
		LoadAllSamplePoints();
	}
	public void EnableAllRegionalRenderings()
	{
		for(int i = 0; i < regions.length; i++)
		{
			for(int j = 0; j < regions[i].length; j++)
			{
				if(regions[i][j] != null)
					regions[i][j].EnableRendering();
			}
		}
	}
	private void InitNewWorld()
	{
		regions = new RegionalMap[1][1];
		w = 1;
		h = 1;
		x0 = 0;
		y0 = 0;
		RegionalMap.ScrollOriginOffsetForOptimalCoastliness();
		regions[0][0] = new RegionalMap(x0, y0, this);
		InitPoissonDiscSample(regions[0][0]);
		SavePerlinData();
		SaveRegionGridInfo();
	}
	private boolean LoadAllSamplePoints()
	{
		boolean hadProblem = false;
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(regions[i][j] == null)
					continue;
				if(!regions[i][j].LoadSampleList())
					hadProblem = true;
			}
		}
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(regions[i][j] == null)
					continue;
				if(!regions[i][j].LoadSampleAdjacencies())
					hadProblem = true;
			}
		}
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(regions[i][j] == null)
					continue;
				if(!regions[i][j].LoadRiverFile())
					hadProblem = true;
			}
		}
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(regions[i][j] == null)
					continue;
				if(!regions[i][j].LoadMidpointTree())
					hadProblem = true;
			}
		}
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(regions[i][j] == null)
					continue;
				regions[i][j].EnableRendering();
			}
		}
		return !hadProblem;
	}
	private String GetRegionsDescName()
	{
		return GetDirectory() + File.separator + "RegionsDesc.txt";
	}
	private boolean SaveRegionGridInfo()
	{
		File regionData = new File(GetRegionsDescName());
		if(regionData.exists())
		{
			if(!regionData.delete())
				return false;
		}
		try {
			BufferedWriter wr = new BufferedWriter(new FileWriter(regionData));
			wr.write(Integer.toString(x0));
			wr.write(" ");
			wr.write(Integer.toString(y0));
			wr.newLine();
			wr.write(Integer.toString(w));
			wr.write(" ");
			wr.write(Integer.toString(h));
			wr.newLine();
			for(int i = 0; i < w; i++)
			{
				for(int j = 0; j < h; j++)
				{
					if(regions[i][j] == null)
						wr.write(K_EMPTY_REGION_NAME);
					else
						wr.write(regions[i][j].GetDirectoryName());
					wr.newLine();
				}
			}
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadRegionGridInfo()
	{
		File regionData = new File(GetRegionsDescName());
		if(!regionData.exists() || !regionData.isFile())
			return false;
		try {
			Scanner std = new Scanner(regionData);
			x0 = std.nextInt();
			y0 = std.nextInt();
			std.nextLine();
			w = std.nextInt();
			h = std.nextInt();
			std.nextLine();
			
			regions = new RegionalMap[w][h];
			for(int i = 0; i < w; i++)
			{
				for(int j = 0; j < h; j++)
				{
					String name = std.nextLine();
					if(name.equals(K_EMPTY_REGION_NAME))
					{
						regions[i][j] = null;
						continue;
					}
					regions[i][j] = new RegionalMap(name, this);
				}
			}
			std.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private String GetPerlinDescName()
	{
		return GetDirectory() + File.separator + "PerlinDesc.txt";
	}
	private boolean SavePerlinData()
	{
		File perlinData = new File(GetPerlinDescName());
		if(perlinData.exists())
		{
			if(!perlinData.delete())
				return false;
		}
		try {
			BufferedWriter wr = new BufferedWriter(new FileWriter(perlinData));
			wr.write(Integer.toString(RegionalMap.ORIGIN_OFFSET));
			wr.newLine();
			boolean success = Perlin.SaveSeeds(wr);
			wr.close();
			return success;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadPerlinData()
	{
		File perlinData = new File(GetPerlinDescName());
		if(!perlinData.exists() || !perlinData.isFile())
			return false;
		try {
			Scanner std = new Scanner(perlinData);
			RegionalMap.ORIGIN_OFFSET = std.nextInt();
			std.nextLine();
			boolean success = Perlin.LoadSeeds(std);
			std.close();
			return success;
		} catch (IOException e) {
			return false;
		}
	}
	
	public RegionalMap GetRegion(int x, int y)
	{
		int arrX = x + x0;
		int arrY = y + y0;
		if(arrX >= w || arrY >= h || arrX < 0 || arrY < 0)
			return null;
		return regions[arrX][arrY];
	}
	
	public void InitPoissonDiscSample(RegionalMap target)
	{
		ArrayList<SamplePoint> active = new ArrayList<SamplePoint>();
		SamplePoint start = new SamplePoint(
				Math.random() + target.GetWorldX(),
				Math.random() + target.GetWorldY(),
				target);
		if(target.SetPoint(start))
			active.add(start);
		PoissonDiscSample(active);
		target.CalculateAllVoronoiAdjacencies();
		target.SaveSampleList(true);
		target.SaveSampleAdjacencies(true);
		//TODO we also need to save rivers here, and maybe something with mesh midpoints?
	}
	private ArrayList<SamplePoint> PoissonDiscSample(ArrayList<SamplePoint> active)
	{
		ArrayList<SamplePoint> created = new ArrayList<SamplePoint>();
		while(!active.isEmpty())
		{
			int index = (int) (Math.random() * active.size());
			SamplePoint sel = active.get(index);
			boolean placed = false;
			for(int i = 0; i < 30; i++)
			{
				double theta = 2 * Math.PI * Math.random();
				double dm2 = RegionalMap.MIN_VORONOI_DIST * RegionalMap.MIN_VORONOI_DIST;
				double rad = 0;
				if(Switches.POISSON_DENSE)
					rad = (1 + 0.1 * Math.random()) * RegionalMap.MIN_VORONOI_DIST;
				else if(Switches.POISSON_BIASED)
					rad = Math.random() * RegionalMap.MIN_VORONOI_DIST + RegionalMap.MIN_VORONOI_DIST;
				else
					rad = Math.sqrt(Math.random() * (3 * dm2) + dm2);
				
				double x = sel.x + rad * Math.cos(theta);
				double y = sel.y + rad * Math.sin(theta);
				int indeX = (int) x - RegionalMap.ORIGIN_OFFSET;
				int indeY = (int) y - RegionalMap.ORIGIN_OFFSET;
				RegionalMap target = GetRegion(indeX, indeY);
				if(target == null)
					continue;
				SamplePoint cand = new SamplePoint(x, y, target);
				if(target.ExistingPointNearby(cand))
					continue;
				if(target.SetPoint(cand))
				{
					created.add(cand);
					placed = true;
					active.add(cand);
					break;
				}
			}
			if(!placed)
			{
				SamplePoint last = active.get(active.size() - 1);
				active.set(index, last);
				active.remove(active.size() - 1);
			}
		}
		return created;
	}
	public void FillAllContinents(int regionX, int regionY, ArrayList<SamplePoint> allFound)
	{
		LinkedList<SamplePoint> continentalQueue = new LinkedList<SamplePoint>();
		RegionalMap target = RequestRegion(regionX, regionY);
		if(target == null)
			return;
		SamplePoint.StartNewSearch();
		for(SamplePoint p : target.GetAllPoints())
		{
			if(p.type.IsTerrainOfType(TerrainTemplate.OCEAN))
				continue;
			continentalQueue.add(p);
			p.MarkAsReached();
		}
		while(!continentalQueue.isEmpty())
		{
			SamplePoint v = continentalQueue.removeFirst();
			allFound.add(v);
			if(v.NearNorthEdge() && v.GetRegionalMap().GetNorth() == null)
			{
				RequestRegion(v.GetRegionalMap().GetOriginX(), v.GetRegionalMap().GetOriginY() - 1);
			}
			if(v.NearSouthEdge() && v.GetRegionalMap().GetSouth() == null)
			{
				RequestRegion(v.GetRegionalMap().GetOriginX(), v.GetRegionalMap().GetOriginY() + 1);
			}
			if(v.NearWestEdge() && v.GetRegionalMap().GetWest() == null)
			{
				RequestRegion(v.GetRegionalMap().GetOriginX() - 1, v.GetRegionalMap().GetOriginY());
			}
			if(v.NearEastEdge() && v.GetRegionalMap().GetEast() == null)
			{
				RequestRegion(v.GetRegionalMap().GetOriginX() + 1, v.GetRegionalMap().GetOriginY());
			}
			for(SamplePoint adj : v.GetAdjacentSamples())
			{
				if(adj.Reached())
					continue;
				if(adj.type.IsTerrainOfType(TerrainTemplate.OCEAN))
					continue;
				adj.MarkAsReached();
				continentalQueue.add(adj);
			}
		}
	}
	public RegionalMap RequestRegion(int x, int y)
	{
		int arrX = x + x0;
		int arrY = y + y0;
		if(arrX < w && arrY < h && arrX >= 0 && arrY >= 0)
		{
			if(regions[arrX][arrY] != null)
				return regions[arrX][arrY];
			CreateRegion(x, y);
			return GetRegion(x, y);
		}
		int posXInc = arrX - w + 1;
		if(posXInc < 0)
			posXInc = 0;
		int negXInc = arrX * -1;
		if(negXInc < 0)
			negXInc = 0;
		int posYInc = arrY - h + 1;
		if(posYInc < 0)
			posYInc = 0;
		int negYInc = arrY * -1;
		if(negYInc < 0)
			negYInc = 0;
		ExpandEmptySpace(posXInc, negXInc, posYInc, negYInc);
		CreateRegion(x, y);
		return GetRegion(x, y);
	}
	public boolean CreateRegion(int x, int y)
	{
		int arrX = x + x0;
		int arrY = y + y0;
		if(arrX >= w || arrY >= h || arrX < 0 || arrY < 0)
			return false;
		if(regions[arrX][arrY] != null)
			return false;
		
		regions[arrX][arrY] = new RegionalMap(x, y, this);
		ArrayList<SamplePoint> newlyActive = new ArrayList<SamplePoint>();
		RegionalMap north = GetRegion(x, y - 1);
		if(north != null)
		{
			for(int ii = 0; ii < RegionalMap.VORONOI_DIM; ii++)
			{
				SamplePoint p1 = north.GetAt(ii, RegionalMap.VORONOI_DIM - 1);
				SamplePoint p2 = north.GetAt(ii, RegionalMap.VORONOI_DIM - 2);
				if(p1 != null)
					newlyActive.add(p1);
				if(p2 != null)
					newlyActive.add(p2);
			}
		}
		RegionalMap south = GetRegion(x, y + 1);
		if(south != null)
		{
			for(int ii = 0; ii < RegionalMap.VORONOI_DIM; ii++)
			{
				SamplePoint p1 = south.GetAt(ii, 0);
				SamplePoint p2 = south.GetAt(ii, 1);
				if(p1 != null)
					newlyActive.add(p1);
				if(p2 != null)
					newlyActive.add(p2);
			}
		}
		RegionalMap west = GetRegion(x - 1, y);
		if(west != null)
		{
			for(int jj = 0; jj < RegionalMap.VORONOI_DIM; jj++)
			{
				SamplePoint p1 = west.GetAt(RegionalMap.VORONOI_DIM - 1, jj);
				SamplePoint p2 = west.GetAt(RegionalMap.VORONOI_DIM - 2, jj);
				if(p1 != null)
					newlyActive.add(p1);
				if(p2 != null)
					newlyActive.add(p2);
			}
		}
		RegionalMap east = GetRegion(x + 1, y);
		if(east != null)
		{
			for(int jj = 0; jj < RegionalMap.VORONOI_DIM; jj++)
			{
				SamplePoint p1 = east.GetAt(0, jj);
				SamplePoint p2 = east.GetAt(1, jj);
				if(p1 != null)
					newlyActive.add(p1);
				if(p2 != null)
					newlyActive.add(p2);
			}
		}
		if(newlyActive.size() == 0) 
		{
			InitPoissonDiscSample(regions[arrX][arrY]);
		}
		else 
		{
			ArrayList<SamplePoint> newV = PoissonDiscSample(newlyActive);
			HashSet<SamplePoint> newlyCreated = new HashSet<SamplePoint>(newV);
			HashSet<SamplePoint> corrupted = new HashSet<SamplePoint>();
			for(SamplePoint v : newV)
			{
				v.CalculateAdjacencies();
			}
			for(SamplePoint v : newV)
			{
				for(SamplePoint vv : v.GetAdjacentSamples())
				{
					if(!newlyCreated.contains(vv))
						corrupted.add(vv);
				}
			}
			for(SamplePoint v : corrupted)
				v.ResetAdjacencies();
			for(SamplePoint v : corrupted)
				v.CalculateAdjacencies();
			
			SaveSampleFiles(newV, true, true, false, false);
			SaveSampleFiles(corrupted, false, true, false, false);
		}
		SaveRegionGridInfo();
		return true;
	}
	public void ExpandEmptySpace(int posXInc, int negXInc, int posYInc, int negYInc)
	{
		int newW = w + posXInc + negXInc;
		int newH = h + posYInc + negYInc;
		int newX0 = x0 + negXInc;
		int newY0 = y0 + negYInc;
		RegionalMap[][] newRegions = new RegionalMap[newW][newH];
		for(int i = 0; i < newW; i++)
			for(int j = 0; j < newH; j++)
			{
				int oldX = i - negXInc;
				int oldY = j - negYInc;
				if(oldX >= 0 && oldY >= 0 && oldX < w && oldY < h)
				{
					newRegions[i][j] = regions[oldX][oldY];
				}
			}
		w = newW;
		h = newH;
		x0 = newX0;
		y0 = newY0;
		regions = newRegions;
		SaveRegionGridInfo();
	}
	public void SaveSampleFiles(Collection<SamplePoint> points, 
			boolean basicData, 
			boolean adjacencies, 
			boolean midpointTrees,
			boolean rivers)
	{
		HashSet<RegionalMap> updated = new HashSet<RegionalMap>();
		for(SamplePoint v : points)
		{
			updated.add(v.GetRegionalMap());
		}
		for(RegionalMap rm : updated)
		{
			if(basicData)
				rm.SaveSampleList(true);
			if(adjacencies)
				rm.SaveSampleAdjacencies(true);
			if(midpointTrees)
				rm.SaveMidpointTree(true);
			if(rivers)
				rm.SaveRiverFile(true);
		}
	}
	public void AttachTool(WorldMapTool newTool)
	{
		DettachActiveTool();
		if(newTool != null)
		{
			if(!newTool.ZoomOkay())
				StopListeningToZoom();
			if(!newTool.DragOkay())
				StopListeningToDrag();
			newTool.Activate();
			activeTool = newTool;
			addMouseListener(activeTool);
			addMouseMotionListener(activeTool);
			addKeyListener(activeTool);
			this.requestFocusInWindow();
		}
	}
	public void DettachActiveTool()
	{
		if(activeTool != null)
		{
			activeTool.Deactivate();
			removeMouseListener(activeTool);
			removeMouseMotionListener(activeTool);
			removeKeyListener(activeTool);
			if(!activeTool.DragOkay())
				StartListeningToDrag();
			if(!activeTool.ZoomOkay())
				StartListeningToZoom();
		}
	}
	public void StartListeningToZoom()
	{
		StopListeningToZoom();
		myZoom = new ZoomUpdater();
		addMouseWheelListener(myZoom);
	}
	public void StopListeningToZoom()
	{
		if(myZoom != null)
		{
			removeMouseWheelListener(myZoom);
			myZoom = null;
		}
	}
	public RegionalMap.Coordinate GetRegionalMapAt(double x, double y)
	{
		return GetRegionalMapAt(x, y, false);
	}
	public RegionalMap.Coordinate GetRegionalMapAt(double x, double y, boolean createIfNot)
	{
		double regionDim = tileSize * RegionalMap.DIMENSION;
		x -= getWidth() / 2;
		y -= getHeight() / 2;
		x += x0 * regionDim;
		y += y0 * regionDim;
		x += 0.5 * regionDim;
		y += 0.5 * regionDim;
		x -= dX;
		y -= dY;
		
		int xIndex = (int) Math.floor(x / regionDim);
		int yIndex = (int) Math.floor(y / regionDim);
		
		RegionalMap found = null;
		if(createIfNot)
			found = RequestRegion(xIndex - x0, yIndex - y0);
		else
			found = GetRegion(xIndex - x0, yIndex - y0);
		
		if(found == null)
			return null;
		
		x -= xIndex * regionDim;
		y -= yIndex * regionDim;
		x /= regionDim;
		y /= regionDim;
		
		return found.new Coordinate(x, y);
	}
	public synchronized void ZoomIn(int mouseX, int mouseY)
	{
		double proposedZoom = 1.1 * tileSize;
		if(proposedZoom > MAXIMUM_TILE_SIZE)
			return;
		int actualZoom = (int) (proposedZoom+0.5);
		double actualRatio = 1.0 * actualZoom / tileSize;
		tileSize = actualZoom;
		AdjustDeltas(mouseX, mouseY, actualRatio);
	}
	public synchronized void ZoomOut(int mouseX, int mouseY)
	{
		double proposedZoom = tileSize / 1.1;
		if(proposedZoom < MINIMUM_TILE_SIZE)
			return;
		int actualZoom = (int) proposedZoom;
		double actualRatio = 1.0 * actualZoom / tileSize;
		tileSize = actualZoom;
		AdjustDeltas(mouseX, mouseY, actualRatio);
	}
	public double GetTileSize()
	{
		return tileSize;
	}
	private void AdjustDeltas(int mouseX, int mouseY, double ratio)
	{
		mouseX -= getWidth() / 2;
		mouseY -= getHeight() / 2;
		double newDX = dX * ratio - mouseX * (ratio - 1);
		double newDY = dY * ratio - mouseY * (ratio - 1);
		dX = newDX;
		dY = newDY;
	}
	public synchronized long Render()
	{
		BufferedImage buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = buffer.createGraphics();
		double regionDim = tileSize * RegionalMap.DIMENSION;
		g2.setColor(Color.black);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setClip(0, 0, getWidth(), getHeight());
		AffineTransform saved = g2.getTransform();
		g2.translate(getWidth()/2, getHeight()/2);
		g2.translate(-1 * x0 * regionDim, -1 * y0 * regionDim);
		g2.translate(-0.5 * regionDim, -0.5 * regionDim);
		g2.translate(dX, dY);
		for(int i = 0; i < w; i++)
		{
			for(int j = 0; j < h; j++)
			{
				if(g2.hitClip(0, 0, (int) regionDim, (int) regionDim))
				{
					RegionalMap m = regions[i][j];
					if(m != null)
						m.Render(regionDim, g2);
				}
				
				g2.translate(0, regionDim);
			}
			g2.translate(regionDim, -1 * h * regionDim);
		}
		RenderQueue.IncrementFrameID();
		g2.setTransform(saved);
		RenderScale(g2);
		Graphics g = getGraphics();
		if(g == null)
			return 10l;
		g.drawImage(buffer, 0, 0, null);
		return 10l;
	}
	private void RenderScale(Graphics2D g2)
	{
		double mPerTile = LocalMap.METER_DIM;
		double mPerPixel = mPerTile / tileSize;
		double mInTargetScale = SCALE_LABEL_TARGET_WIDTH * mPerPixel;
		double mInChosenScale = -1;
		String chosenScale = "";
		for(int i = 0; i < LABEL_DISTANCES.length; i++)
		{
			double mInScale = LABEL_DISTANCES[i];
			String scaleName = TARGET_SCALE_LABLES[i];
			if(i == 0 || (Math.abs(mInTargetScale - mInScale) < Math.abs(mInTargetScale - mInChosenScale)))
			{
				mInChosenScale = mInScale;
				chosenScale = scaleName;
			}
		}
		Font f = new Font("Calibri", Font.BOLD, 18);
		//Font f = g2.getFont().deriveFont(Font.BOLD, 16);
		g2.setFont(f);
		double barWidth = mInChosenScale / mPerPixel;
		int textWidth = g2.getFontMetrics().stringWidth(chosenScale);
		
		int h = g2.getFontMetrics().getHeight() + 15;
		int w = (int) Math.max(textWidth, barWidth) + 10;
		
		AffineTransform saved = g2.getTransform();
		g2.translate(getWidth(), getHeight());
		g2.translate(-1 * w, -1 * h);
		g2.translate(-10, -10);
		g2.translate(w/2, 0);
		g2.setColor(Color.white);
		g2.fillRect((int)(-0.5 * textWidth - 5), 0, textWidth + 10, g2.getFontMetrics().getHeight());
		g2.setColor(Color.black);
		g2.drawString(chosenScale, (int) (-0.5 * textWidth), (int) (0.75 * g2.getFontMetrics().getHeight()));
		g2.translate(0, h/2);
		g2.setColor(Color.orange);
		int lineY = (int) (h/2 - 5);
		g2.fillRect((int) (-0.5 * barWidth), lineY - 2, (int) (barWidth), 4);
		g2.fillRect((int) (-0.5 * barWidth - 1), lineY - 6, 2, 12);
		g2.fillRect(-1, lineY - 4, 2, 8);
		g2.fillRect((int) (0.5 * barWidth - 1), lineY - 6, 2, 12);
		g2.setTransform(saved);
	}
	private class ZoomUpdater implements MouseWheelListener
	{
		
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			int rots = e.getWheelRotation();
			if(rots < 0)
				for(int i = 0; i < rots * -1; i++)
					ZoomIn(e.getX(), e.getY());
			else
				for(int i = 0; i < rots; i++)
					ZoomOut(e.getX(), e.getY());
		}
	}
	
	public static final int SCALE_LABEL_TARGET_WIDTH = 200;
	public static final String[] TARGET_SCALE_LABLES = new String[]
			{
					"200 Miles",
					"100 Miles",
					"50 Miles",
					"25 Miles",
					"10 Miles",
					"5 Miles",
					"2 Miles",
					"1 Mile",
					"1/2 Mile",
					"1/3 Mile",
					"1/4 Mile",
					"1000 Feet",
					"500 Feet",
					"200 Feet",
					"100 Feet",
					"50 Feet",
					"20 Feet",
					"10 Feet"
			};
	public static final double[] LABEL_DISTANCES = new double[]
			{
					321869,
					160934,
					80467.2,
					40233.6,
					16093.4,
					8046.72,
					3218.69,
					1609.34,
					804.672,
					536.448,
					406.336,
					304.8,
					152.4,
					60.96,
					30.48,
					15.24,
					6.096,
					3.048
			};
	public static final int DEFAULT_TILE_SIZE = 10; //how many pixels does a local map get?
	public static final int MINIMUM_TILE_SIZE = 9;
	public static final int MAXIMUM_TILE_SIZE = 2047; //1023;
	public static final String K_EMPTY_REGION_NAME = "null_region";
	public static final String K_SAVE_FOLDER_NAME = "Worlds";
	public static final String K_REGIONS_FOLDER_NAME = "Regions";
}