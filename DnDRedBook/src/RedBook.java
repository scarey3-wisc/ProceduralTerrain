import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.MatteBorder;

public class RedBook
{
	private JFrame topFrame;
	private JPanel topPanel;
	private InfoPanel info;
	private ToolPanel tools;
	private WorldMap myMap;
	public RedBook()
	{
		//Perlin.HackSaveSeeds();
		
		topFrame = new JFrame("Red Book of Westmarch D&D");
		topFrame.setSize(Toolkit.getDefaultToolkit().getScreenSize());
		topFrame.setLocation(0, 0);
		topFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		topFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		topPanel = new JPanel(new BorderLayout());
		topPanel.setBackground(Color.black);
		info = new InfoPanel();
		JScrollPane infoScroll = new JScrollPane(info, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		myMap = new WorldMap("Nerean Sea");
		tools = new ToolPanel(myMap);
		myMap.setBackground(Color.white);
		topPanel.add(myMap, BorderLayout.CENTER);
		topPanel.add(infoScroll, BorderLayout.WEST);
		topPanel.add(tools, BorderLayout.EAST);
		topFrame.add(topPanel);
		topPanel.updateUI();

	}
	public void Init()
	{
		tile_renderings = new ImageArchive(
			TILE_RENDERING_CACHES,
			TILE_RENDERING_MINS,
			TILE_RENDERING_MAXES,
			TILE_RENDERING_CAP,
			TILE_RENDERING_NAMES);
		tile_contents = new ImageArchive(TILE_CONTENT_CAP);

		topFrame.setVisible(true);
		myMap.StartListeningToDrag();
		myMap.StartListeningToZoom();
		sleep(500);
		myMap.InitiateRendering();
		info.SetAllText();
		Thread infoUpdater = new Thread(info);
		infoUpdater.start();

		if(myMap.FileAvailable)
			myMap.LoadWorld();
		else if(Switches.CURR_TERRAIN_ALGO == Switches.TERRAIN_GEN_ALGO.FUSED_TECTONIC_AND_DISTANCE)
		{
			ArrayList<SamplePoint> vp = new ArrayList<SamplePoint>();
			myMap.FillAllContinents(0, 0, vp);
			ArrayList<SamplePoint> coastal = VoronoiAlgorithms.FindBoundaryPoints(vp, TerrainTemplate.OCEAN);
			VoronoiAlgorithms.ConvertSeasToLakes(coastal, Switches.MAX_SAMPLE_POINTS_IN_LAKE);
			VoronoiAlgorithms.ConvertCoastalLakeToOcean(vp);
			//VoronoiAlgorithms.AssignHeights(coastal);
			ArrayList<SamplePoint> continent = VoronoiAlgorithms.FindAllWithinBoundary(vp, TerrainTemplate.OCEAN);
			ContinentGenAlgorithms.BlurUpliftForTectonicAlgorithm(continent, 25, 5);
						
			ArrayList<SamplePoint> rough = VoronoiAlgorithms.FindAllOfType(continent, TerrainTemplate.ROUGH);
			ArrayList<SamplePoint> mountains = VoronoiAlgorithms.FindAllOfType(continent, TerrainTemplate.MOUNTAINS);
			
			//paper recommends 2.5 * 10^5 and 5.611 * 10^-7 for erosion
			System.out.println("Tectonic Uplift Algo: Detail-0");
			//ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(continent, 2.5 * 100000, 5.611 * 0.0000001, 300, 0.0002);
			System.out.println();
			
			for(SamplePoint sp : rough)
			{
				if(Math.random() < 0.99)
					continue;
				sp.MakeLake();
			}
			
			VoronoiAlgorithms.IncreaseFractureLevel(rough);
			System.out.println("Tectonic Uplift Algo: Detail-1");
			//ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(rough, 2.5 * 100000, 5.611 * 0.0000001, 300, 0.0002);
			System.out.println();
			
			for(SamplePoint sp : mountains)
			{
				if(Math.random() < 0.96)
					continue;
				sp.MakeLake();
			}
			
			VoronoiAlgorithms.IncreaseFractureLevel(mountains);
			System.out.println("Tectonic Uplift Algo: Detail-2");
			//ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(mountains, 2.5 * 100000, 5.611 * 0.0000001, 300, 0.0002);
			System.out.println();
			
			
			System.out.println("Tectonic Uplift Algo: River Reset");
			ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(continent, 2.5 * 100000, 5.611 * 0.0000001, 500, 0.0002);
			System.out.println();
			
			myMap.EnableAllRegionalRenderings();
			
			myMap.SaveSampleFiles(continent, true, false, true, true);
			System.out.println("Continent Generation Complete!");
		}
		else if(Switches.CURR_TERRAIN_ALGO == Switches.TERRAIN_GEN_ALGO.COASTAL_DISTANCE_TYPE_CONSTRAINED)
		{
			ArrayList<SamplePoint> vp = new ArrayList<SamplePoint>();
			myMap.FillAllContinents(0, 0, vp);
			ArrayList<SamplePoint> coastal = VoronoiAlgorithms.FindBoundaryPoints(vp, TerrainTemplate.OCEAN);
			VoronoiAlgorithms.ConvertSeasToLakes(coastal, Switches.MAX_SAMPLE_POINTS_IN_LAKE);
			VoronoiAlgorithms.ConvertCoastalLakeToOcean(vp);
			VoronoiAlgorithms.AssignHeights(coastal);
			ArrayList<SamplePoint> continent = VoronoiAlgorithms.FindAllWithinBoundary(vp, TerrainTemplate.OCEAN);
			VoronoiAlgorithms.SetRiverFlow(continent);
			VoronoiAlgorithms.EnsureRiversFlowDownhill(continent);
			myMap.EnableAllRegionalRenderings();
			myMap.SaveSampleFiles(continent, true, false, true, true);
			System.out.println("Continent Generation Complete!");
		}
		else if(Switches.CURR_TERRAIN_ALGO == Switches.TERRAIN_GEN_ALGO.TECTONIC_UPLIFT)
		{
			ArrayList<SamplePoint> vp = new ArrayList<SamplePoint>();
			myMap.FillAllContinents(0, 0, vp);
			ArrayList<SamplePoint> coastal = VoronoiAlgorithms.FindBoundaryPoints(vp, TerrainTemplate.OCEAN);
			VoronoiAlgorithms.ConvertSeasToLakes(coastal, Switches.MAX_SAMPLE_POINTS_IN_LAKE);
			VoronoiAlgorithms.ConvertCoastalLakeToOcean(vp);
			ArrayList<SamplePoint> continent = VoronoiAlgorithms.FindAllWithinBoundary(vp, TerrainTemplate.OCEAN);
			ContinentGenAlgorithms.BlurUpliftForTectonicAlgorithm(continent, 50, 2);

			for(SamplePoint sp : continent)
				sp.SetElevation(1);
			
			//paper recommends 2.5 * 10^5 and 5.611 * 10^-7 for erosion
			System.out.println("Tectonic Uplift Algo: Detail-0");
			ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(continent, 2.5 * 100000, 5.611 * 0.0000001, 300, 0.0002);
			System.out.println();
			VoronoiAlgorithms.IncreaseFractureLevel(continent);
			System.out.println("Tectonic Uplift Algo: Detail-1");
			ContinentGenAlgorithms.RunTectonicUpliftAlgorithm(continent, 2.5 * 100000, 5.611 * 0.0000001, 300, 0.0002);
			System.out.println();
			
			myMap.EnableAllRegionalRenderings();
			myMap.SaveSampleFiles(continent, true, false, true, true);
			System.out.println("Continent Generation Complete!");
		}
		
	}
	public static void main(String[] args)
	{
		RedBook myBook = new RedBook();
		myBook.Init();
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> 
		{
			System.out.println("Saving all Heightmaps");
			heightmaps.SaveAll();
			System.out.println("Saving all Watermaps");
			watermaps.SaveAll();
			System.out.println("Saving all Rainflowmaps");
			rainflowmaps.SaveAll();
			System.out.println("Done");
		}));
	}
	public static void sleep(long millis)
	{
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	private class InfoPanel extends JPanel implements Runnable
	{
		private static final long serialVersionUID = 1L;
		private JLabel[] libraryStorage;
		private JLabel renderQueue;
		private JLabel[] smartImageStorage;
		private JLabel smartImageCount;
		
		private JButton toggleLMOutline;
		private JButton clearCache;
		private JButton voronoiCenters;
		
		private JButton paintTerrainEval;
		
		private JButton paintCurrElev;
		private JButton paintTerrainType;
		private JButton paintContour;
		private JButton paintElevGradient;

		private JButton paintPerlinGenerator;
		private JButton paintVoronoiCells;
		private JButton paintVoronoiLerp;
		private JButton paintVoronoiTri;
		
		private JPanel information;
		private JLabel informationTitle;
		private JPanel options;
		private JLabel optionsTitle;
		private JPanel visualPaints;
		private JLabel visualPaintsTitle;
		private JPanel usefulPaints;
		private JLabel usefulPaintTitle;
		private JPanel stupidPaints;
		private JLabel stupidPaintTitle;

		public InfoPanel()
		{
			//super(new GridLayout(0, 1));
			super(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			this.setBackground(Color.white);
			
			//The information panel
			information = new JPanel(new GridLayout(0, 1));
			information.setBorder(new MatteBorder(15, 10, 15, 10, Color.white));
			information.setBackground(Color.white);
			c.gridx = 0;
			c.gridy = 0;
			add(information, c);
			informationTitle = new JLabel("Render Info");
			information.add(informationTitle);
			libraryStorage = new JLabel[TILE_RENDERING_CACHES];
			for(int i = 0; i < TILE_RENDERING_CACHES; i++)
			{
				libraryStorage[i] = new JLabel();
				information.add(libraryStorage[i]);
			}
			renderQueue = new JLabel();
			information.add(renderQueue);
			information.add(new JLabel());
			smartImageStorage = new JLabel[DataImage.dimRange.length];
			for(int i = 0; i < DataImage.dimRange.length; i++)
			{
				smartImageStorage[i] = new JLabel();
				information.add(smartImageStorage[i]);
			}
			smartImageCount = new JLabel();
			information.add(smartImageCount);
			
			//The options panel
			options = new JPanel(new GridLayout(0, 1));
			options.setBorder(new MatteBorder(15, 10, 15, 10, Color.white));
			options.setBackground(Color.white);
			c.gridy = 1;
			add(options, c);
			optionsTitle = new JLabel("Render Options");
			options.add(optionsTitle);
			
			toggleLMOutline = new JButton("Toggle Outlines");
			toggleLMOutline.addActionListener(new ToggleLM());
			options.add(toggleLMOutline);
			
			clearCache = new JButton("Clear Image Cache");
			clearCache.addActionListener(new ClearImageCache());
			options.add(clearCache);
			
			voronoiCenters = new JButton("Toggle Sample Points");
			voronoiCenters.addActionListener(new ToggleVoronoiCenters());
			options.add(voronoiCenters);
			
			//Display Paint Choices
			visualPaints = new JPanel(new GridLayout(0, 1));
			visualPaints.setBorder(new MatteBorder(15, 10, 15, 10, Color.white));
			visualPaints.setBackground(Color.white);
			c.gridy = 2;
			add(visualPaints, c);
			visualPaintsTitle = new JLabel("Display Paint Modes");
			visualPaints.add(visualPaintsTitle);
			
			paintTerrainEval = new JButton("Paint Terrain");
			paintTerrainEval.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.TERRAIN_EVAL));
			visualPaints.add(paintTerrainEval);
			
			//Frequent Paint Choices
			usefulPaints = new JPanel(new GridLayout(0, 1));
			usefulPaints.setBorder(new MatteBorder(15, 10, 15, 10, Color.white));
			usefulPaints.setBackground(Color.white);
			c.gridy = 3;
			add(usefulPaints, c);
			usefulPaintTitle = new JLabel("Frequent Paint Modes");
			usefulPaints.add(usefulPaintTitle);
			
			paintCurrElev = new JButton("Paint Heightmap");
			paintCurrElev.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.ELEVATION_CURR));
			usefulPaints.add(paintCurrElev);
			
			paintTerrainType = new JButton("Paint Terrain Type");
			paintTerrainType.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.TERRAIN));
			usefulPaints.add(paintTerrainType);
			
			paintContour = new JButton("Paint Contour Map");
			paintContour.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.CONTOUR));
			usefulPaints.add(paintContour);
			
			paintElevGradient = new JButton("Paint Topography");
			paintElevGradient.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.ELEV_GRADIENT));
			usefulPaints.add(paintElevGradient);
			
			//Infrequent Paint Choices
			stupidPaints = new JPanel(new GridLayout(0, 1));
			stupidPaints.setBorder(new MatteBorder(15, 10, 15, 10, Color.white));
			stupidPaints.setBackground(Color.white);
			c.gridy = 4;
			add(stupidPaints, c);
			stupidPaintTitle = new JLabel("Infrequent Paint Modes");
			stupidPaints.add(stupidPaintTitle);
			
			paintPerlinGenerator = new JButton("Paint Perlin Noise");
			paintPerlinGenerator.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.MIN_MAX_SELECTOR_DISPLAY));
			stupidPaints.add(paintPerlinGenerator);

			paintVoronoiCells = new JButton("Paint Voronoi Cells");
			paintVoronoiCells.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.VORONOI_PURE));
			stupidPaints.add(paintVoronoiCells);
			
			paintVoronoiLerp = new JButton("Paint Voronoi Interpolation");
			paintVoronoiLerp.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.VORONOI_INTERPOLATED));
			stupidPaints.add(paintVoronoiLerp);
			
			paintVoronoiTri = new JButton("Paint Voronoi Triangles");
			paintVoronoiTri.addActionListener(new SetPaintMode(Switches.PAINT_TYPE.VORONOI_TRIANGLES));
			stupidPaints.add(paintVoronoiTri);
		}

		@Override
		public void run() {
			while(true)
			{
				SetAllText();
				sleep(50);
			}
		}
		public void SetAllText()
		{
			SetLibraryText();
			SetQueueText();
			SetSmartImageText();
		}
		private void SetLibraryText()
		{
			for(int i = 0; i < libraryStorage.length; i++)
			{
				String name = tile_renderings.caches[i].GetLibraryName();
				long curr = tile_renderings.caches[i].NumPixels();
				long max = tile_renderings.caches[i].PixelsStorageMax();
				int percent = (int) (curr * 100 / max);
				int numImages = tile_renderings.caches[i].NumImages();
				libraryStorage[i].setText(name + ": " + percent + "% (" + numImages + ")");
			}

		}
		private void SetQueueText()
		{
			int num = RenderQueue.NumItemsQueued();
			renderQueue.setText(num + " queued");
		}
		private void SetSmartImageText()
		{
			int totalNumImages = 0;
			for(int i = 0; i < DataImage.dimRange.length; i++)
			{
				int dim = DataImage.dimRange[i];
				String text = Integer.toString(dim) + "px: ";
				int numImages = heightmaps.GetCurrentSize(i);
				totalNumImages += numImages;
				text += Integer.toString(numImages) + " / ";
				text += Integer.toString(heightmaps.GetCapacity(i));
				smartImageStorage[i].setText(text);
				
			}
			String countText = "Smart Images: " + Integer.toString(totalNumImages);
			smartImageCount.setText(countText);
		}
		private class ClearImageCache implements ActionListener
		{
			@Override
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < TILE_RENDERING_CACHES; i++)
					tile_renderings.caches[i].ResetAll();
			}
		}
		private class ToggleVoronoiCenters implements ActionListener
		{

			@Override
			public void actionPerformed(ActionEvent e) {
				Switches.PAINT_VORONOI_CENTERS = !Switches.PAINT_VORONOI_CENTERS;
				for(int i = 0; i < TILE_RENDERING_CACHES; i++)
					tile_renderings.caches[i].ResetAll();
			}
			
		}
		private class SetPaintMode implements ActionListener
		{
			Switches.PAINT_TYPE myType;
			public SetPaintMode(Switches.PAINT_TYPE type)
			{
				myType = type;
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				Switches.CURR_PAINT_TYPE = myType;
				for(int i = 0; i < TILE_RENDERING_CACHES; i++)
					tile_renderings.caches[i].ResetAll();
			}
			
		}
		private class ToggleLM implements ActionListener
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				Switches.OUTLINE_MAPS = !Switches.OUTLINE_MAPS;
			}
		}
	}
	public static final int TILE_RENDERING_CACHES = 6;
	private static final int[] TILE_RENDERING_MINS = new int[] {
		0, 401, 1601, 6401, 25601, 102401
	};
	private static final int[] TILE_RENDERING_MAXES = new int[] {
		400, 1600, 6400, 25600, 102400, Integer.MAX_VALUE	
	};
	private static final long[] TILE_RENDERING_CAP = new long[] {
		20000000l, 5000000l, 5000000l, 5000000l, 10000000l, 40000000l
	};
	private static final String[] TILE_RENDERING_NAMES = new String[] {
		"Tiny", "Small", "Medium", "Large", "Massive", "Huge"	
	};
	private static final long TILE_CONTENT_CAP = 50000000l;
	public static ImageArchive tile_contents;
	public static ImageArchive tile_renderings;
	public static DataImage32Decimal.DataImageManager heightmaps = new DataImage32Decimal.DataImageManager(new int[] {50000, 10000, 4000, 1000, 500});
	public static DataImageByte.DataImageManager watermaps = new DataImageByte.DataImageManager(new int[] {50000, 10000, 4000, 1000, 500});
	public static DataImageInt.DataImageManager rainflowmaps = new DataImageInt.DataImageManager(new int[] {50000, 10000, 4000, 1000, 500});
	public static DataImage32Decimal.DataImageManager sedimentmaps = new DataImage32Decimal.DataImageManager(new int[] {50000, 10000, 4000, 1000, 500});


}