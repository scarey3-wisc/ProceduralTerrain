import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.IntStream;


/*
 * A RegionalMap represents an area 200km x 200km. 
 * It is composed of 20x20 squares, each of which is 10km
 * x 10km large. Those tiles have a TerrainType, and can
 * be rendered according to their TerrainType alone.
 * Alternatively, those tiles can each have a Local Map which,
 * if instantiated, allows more detailed rendering. 
 * 
 * RegionalMaps also cache BufferedImages for how they are to
 * be rendered. 
 */
public class RegionalMap
{
	private int x, y; //while tile am I in the World Map?
	private LocalMap[] topography;
	private SamplePoint[] terrainCells;
	private ArrayList<SamplePoint> voronoiList;
	private WorldMap parent;
	private boolean readyToRender;
	public RegionalMap(int x, int y, WorldMap p)
	{
		this.parent = p;
		this.x = x;
		this.y = y;
		topography = new LocalMap[DIMENSION * DIMENSION];
		for(int i = 0; i < DIMENSION; i++)
			for(int j = 0; j < DIMENSION; j++)
			{
				topography[i * DIMENSION + j] = new LocalMap(i, j, this);
			}
		terrainCells = new SamplePoint[VORONOI_DIM * VORONOI_DIM];
		voronoiList = new ArrayList<SamplePoint>();
		readyToRender = false;
		EnsureBasicDirectoryStructureExists();
		SaveBasicDescription();
	}
	public RegionalMap(String directory, WorldMap parent)
	{
		this.parent = parent;
		String dir = parent.GetDirectory() + File.separator;
		dir += WorldMap.K_REGIONS_FOLDER_NAME + File.separator;
		dir += directory;
		String name = dir + File.separator + "BasicDesc.txt";
		File basicData = new File(name);
		try {
			Scanner std = new Scanner(basicData);
			this.x = std.nextInt();
			this.y = std.nextInt();
			std.nextLine();
			std.close();
		} catch (IOException e) {
			return;
		}
		
		topography = new LocalMap[DIMENSION * DIMENSION];
		for(int i = 0; i < DIMENSION; i++)
			for(int j = 0; j < DIMENSION; j++)
			{
				topography[i * DIMENSION + j] = new LocalMap(i, j, this);
			}
		terrainCells = new SamplePoint[VORONOI_DIM * VORONOI_DIM];
		voronoiList = new ArrayList<SamplePoint>();		
		EnsureBasicDirectoryStructureExists();
		readyToRender = false;
	}
	private String GetRiverFileName(boolean binary)
	{
		if(binary)
			return GetDirectory() + File.separator + "RiverDetails.bin";
		else
			return GetDirectory() + File.separator + "RiverDetails.txt";
	}
	public boolean SaveRiverFile(boolean binary)
	{
		File riverFile = new File(GetRiverFileName(binary));
		if(riverFile.exists())
		{
			if(!riverFile.delete())
				return false;
		}
		if(binary)
			return SaveRiverFileBinary(riverFile);
		else
			return SaveRiverFileVerbose(riverFile);
	}
	private boolean SaveRiverFileBinary(File file)
	{
		try {
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			dos.writeInt(voronoiList.size());
			for(SamplePoint sp : voronoiList)
			{
				dos.writeDouble(sp.GetRiverFlow());
				if(sp.RiverFlowProcessed())
				{
					dos.writeByte(1);
					sp.GetRiverOutlet().WriteGlobalIdentifier(dos);
				}
				else
					dos.writeByte(0);
			}
			dos.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean SaveRiverFileVerbose(File file)
	{
		try
		{
			BufferedWriter wr = new BufferedWriter(new FileWriter(file));
			for(SamplePoint sp : voronoiList)
			{
				String desc = Double.toString(sp.GetRiverFlow());
				desc += " ";
				if(sp.RiverFlowProcessed())
				{
					desc += "1" + " ";
					desc += sp.GetRiverOutlet().GetGlobalIdentifier();
				}
				else
				{
					desc += "0";
				}
				wr.write(desc);
				wr.newLine();
			}
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean LoadRiverFile()
	{
		boolean binary = true;
		File riverFile = new File(GetRiverFileName(binary));
		if(!riverFile.exists() || !riverFile.isFile())
			binary = false;
		riverFile = new File(GetRiverFileName(binary));
		if(!riverFile.exists() || !riverFile.isFile())
			return false;
		if(binary)
			return LoadRiverFileBinary(riverFile);
		else
			return LoadRiverFileVerbose(riverFile);
	}
	private boolean LoadRiverFileBinary(File file)
	{
		try {
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
			int num = dis.readInt();
			boolean hadIssue = false;
			for(int count = 0; count < num; count++)
			{
				SamplePoint p = voronoiList.get(count);
				double flow = dis.readDouble();
				p.ForceSetRiverFlow(flow);
				byte flag = dis.readByte();
				if(flag == 1)
				{
					SamplePoint outlet = SamplePoint.LoadFromGlobalIdentifier(dis, parent);
					p.SetRiverOutlet(outlet);
				}
			}
			dis.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadRiverFileVerbose(File file)
	{
		try {
			boolean hadIssue = false;
			Scanner std = new Scanner(file);
			int index = 0;
			while(std.hasNextLine())
			{
				SamplePoint curr = voronoiList.get(index);
				String desc = std.nextLine();
				String[] dets = desc.split(" ");
				double flow = Double.parseDouble(dets[0]);
				curr.ForceSetRiverFlow(flow);
				if(dets[1].equals("1"))
				{
					SamplePoint outlet = SamplePoint.FindFromGlobalIdentifier(dets[2], parent);
					curr.SetRiverOutlet(outlet);
				}
				index++;
			}
			std.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private String GetMidpointFileName(boolean binary)
	{
		if(binary)
			return GetDirectory() + File.separator + "MidpointTree.bin";
		else
			return GetDirectory() + File.separator + "MidpointTree.txt";
	}
	public boolean SaveMidpointTree(boolean binary)
	{
		File treeFile = new File(GetMidpointFileName(binary));
		if(treeFile.exists())
		{
			if(!treeFile.delete())
				return false;
		}
		if(binary)
			return SaveMidpointTreeBinary(treeFile);
		else
			return SaveMidpointTreeVerbose(treeFile);
	}
	private boolean SaveMidpointTreeBinary(File file)
	{
		try
		{
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			MeshPoint.StartNewSearch();
			ArrayList<ArrayList<MeshMidPoint>> detailLevels = new ArrayList<ArrayList<MeshMidPoint>>();
			ArrayList<? extends MeshPoint> currentParents = voronoiList;
			for(MeshPoint p : currentParents)
				p.MarkAsReached();
			while(true)
			{
				ArrayList<MeshMidPoint> found = new ArrayList<MeshMidPoint>();
				for(MeshPoint par : currentParents)
				{
					for(MeshPoint adj : par.GetAdjacent())
					{
						MeshConnection con = par.GetConnection(adj);
						if(con == null)
							continue;
						if(!con.MidInitialized())
							continue;
						MeshMidPoint mid = con.GetMid();
						if(mid.Reached())
							continue;
						mid.MarkAsReached();
						mid.SetContainerIndex(found.size());
						found.add(mid);
					}
				}
				if(found.size() == 0)
					break;
				currentParents = found;
				detailLevels.add(found);
			}
			boolean hadProblem = false;
			for(ArrayList<MeshMidPoint> level : detailLevels)
			{
				dos.writeInt(level.size());
				for(MeshMidPoint p : level)
				{
					if(p.GetParentA() instanceof SamplePoint)
					{
						SamplePoint a = (SamplePoint) p.GetParentA();
						dos.writeByte(0);
						hadProblem = hadProblem || !a.WriteGlobalIdentifier(dos);
					}
					else
					{
						dos.writeByte(p.GetParentA().GetDetailLevel());
						dos.writeInt(p.GetParentA().GetContainerIndex());
					}
					if(p.GetParentB() instanceof SamplePoint)
					{
						SamplePoint b = (SamplePoint) p.GetParentB();
						dos.writeByte(0);
						hadProblem = hadProblem || !b.WriteGlobalIdentifier(dos);
					}
					else
					{
						dos.writeByte(p.GetParentB().GetDetailLevel());
						dos.writeInt(p.GetParentB().GetContainerIndex());
					}
					hadProblem = hadProblem || !p.WriteDescription(dos);
				}
			}
			for(ArrayList<MeshMidPoint> level : detailLevels)
			{
				for(MeshMidPoint p : level)
					p.ResetContainerIndex();
			}
			dos.writeInt(0);
			dos.close();
			return !hadProblem;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean SaveMidpointTreeVerbose(File file)
	{
		try
		{
			BufferedWriter wr = new BufferedWriter(new FileWriter(file));
			MeshPoint.StartNewSearch();
			ArrayList<ArrayList<MeshMidPoint>> detailLevels = new ArrayList<ArrayList<MeshMidPoint>>();
			ArrayList<? extends MeshPoint> currentParents = voronoiList;
			for(MeshPoint p : currentParents)
				p.MarkAsReached();
			while(true)
			{
				ArrayList<MeshMidPoint> found = new ArrayList<MeshMidPoint>();
				for(MeshPoint par : currentParents)
				{
					for(MeshPoint adj : par.GetAdjacent())
					{
						MeshConnection con = par.GetConnection(adj);
						if(con == null)
							continue;
						if(!con.MidInitialized())
							continue;
						MeshMidPoint mid = con.GetMid();
						if(mid.Reached())
							continue;
						mid.MarkAsReached();
						mid.SetContainerIndex(found.size());
						found.add(mid);
					}
				}
				if(found.size() == 0)
					break;
				currentParents = found;
				detailLevels.add(found);
			}
			for(ArrayList<MeshMidPoint> level : detailLevels)
			{
				wr.write(Integer.toString(level.size()));
				wr.newLine();
				for(MeshMidPoint p : level)
				{
					String desc = "";
					if(p.GetParentA() instanceof SamplePoint)
					{
						SamplePoint a = (SamplePoint) p.GetParentA();
						desc += "0" + " " + a.GetGlobalIdentifier();
					}
					else
					{
						desc += Byte.toString(p.GetParentA().GetDetailLevel());
						desc += " " + p.GetParentA().GetContainerIndex();
					}
					desc += " ";
					if(p.GetParentB() instanceof SamplePoint)
					{
						SamplePoint b = (SamplePoint) p.GetParentB();
						desc += "0" + " " + b.GetGlobalIdentifier();
					}
					else
					{
						desc += Byte.toString(p.GetParentB().GetDetailLevel());
						desc += " " + p.GetParentB().GetContainerIndex();
					}
					desc += " ";
					desc += p.GetDescription();
					wr.write(desc);
					wr.newLine();
				}
			}
			for(ArrayList<MeshMidPoint> level : detailLevels)
			{
				for(MeshMidPoint p : level)
					p.ResetContainerIndex();
			}
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean LoadMidpointTree()
	{
		boolean binary = true;
		File treeFile = new File(GetMidpointFileName(binary));
		if(!treeFile.exists() || !treeFile.isFile())
			binary = false;
		treeFile = new File(GetMidpointFileName(binary));
		if(!treeFile.exists() || !treeFile.isFile())
			return false;
		if(binary)
			return LoadMidpointTreeBinary(treeFile);
		else
			return LoadMidpointTreeVerbose(treeFile);
	}
	private boolean LoadMidpointTreeBinary(File file)
	{
		try {
			boolean hadIssue = false;
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
			ArrayList<ArrayList<MeshMidPoint>> detailLevels = new ArrayList<ArrayList<MeshMidPoint>>();
			while(true)
			{
				int levelSize = dis.readInt();
				if(levelSize == 0)
					break;
				ArrayList<MeshMidPoint> myLevel = new ArrayList<MeshMidPoint>(levelSize);
				for(int i = 0; i < levelSize; i++)
				{
					int detA = dis.readByte();
					MeshPoint a = null;
					if(detA == 0)
						a = SamplePoint.LoadFromGlobalIdentifier(dis, parent);
					else
					{
						int index = dis.readInt();
						if(index != -1)
							a = detailLevels.get(detA - 1).get(index);
					}
					int detB = dis.readByte();
					MeshPoint b = null;
					if(detB == 0)
						b = SamplePoint.LoadFromGlobalIdentifier(dis, parent);
					else
					{
						int index = dis.readInt();
						if(index != -1)
							b = detailLevels.get(detB - 1).get(index);
					}
					if(a == null || b == null)
					{
						MeshMidPoint.ConsumeDescription(dis);
						myLevel.add(null);
						continue;
					}
					MeshConnection con = a.GetConnection(b);
					if(con == null)
						con = b.GetConnection(a);
					if(con == null)
					{
						MeshMidPoint.ConsumeDescription(dis);
						myLevel.add(null);
						continue;
					}
					MeshMidPoint mid = con.GetMid(dis);
					myLevel.add(mid);
				}
				detailLevels.add(myLevel);
			}
			dis.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadMidpointTreeVerbose(File file)
	{
		try {
			boolean hadIssue = false;
			Scanner std = new Scanner(file);
			ArrayList<ArrayList<MeshMidPoint>> detailLevels = new ArrayList<ArrayList<MeshMidPoint>>();
			while(true)
			{
				if(!std.hasNextInt())
					break;
				int levelSize = std.nextInt();
				std.nextLine();
				ArrayList<MeshMidPoint> myLevel = new ArrayList<MeshMidPoint>(levelSize);
				for(int i = 0; i < levelSize; i++)
				{
					String desc = std.nextLine();
					String[] dets = desc.split(" ");
					int detA = Integer.parseInt(dets[0]);
					MeshPoint a = null;
					if(detA == 0)
						a = SamplePoint.FindFromGlobalIdentifier(dets[1], parent);
					else
					{
						int index = Integer.parseInt(dets[1]);
						if(index != -1)
							a = detailLevels.get(detA - 1).get(index);
					}
					int detB = Integer.parseInt(dets[2]);
					MeshPoint b = null;
					if(detB == 0)
						b = SamplePoint.FindFromGlobalIdentifier(dets[3], parent);
					else
					{
						int index = Integer.parseInt(dets[3]);
						if(index != -1)
							b = detailLevels.get(detB - 1).get(index);
					}
					if(a == null || b == null)
					{
						myLevel.add(null);
						continue;
					}
					MeshConnection con = a.GetConnection(b);
					if(con == null)
						con = b.GetConnection(a);
					if(con == null)
					{
						myLevel.add(null);
						continue;
					}
					Iterator<String> tokenStream = Arrays.stream(Arrays.copyOfRange(dets, 4, dets.length)).iterator();
					MeshMidPoint mid = con.GetMid(tokenStream);
					myLevel.add(mid);
				}
				detailLevels.add(myLevel);
			}
			std.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private String GetSampleAdjFileName(boolean binary)
	{
		if(binary)
			return GetDirectory() + File.separator + "SampleAdjacencies.bin";
		else
			return GetDirectory() + File.separator + "SampleAdjacencies.txt";
	}
	public boolean SaveSampleAdjacencies(boolean binary)
	{
		File sampleAdjFile = new File(GetSampleAdjFileName(binary));
		if(sampleAdjFile.exists())
		{
			if(!sampleAdjFile.delete())
				return false;
		}
		if(binary)
			return SaveSampleAdjacenciesBinary(sampleAdjFile);
		else
			return SaveSampleAdjacenciesVerbose(sampleAdjFile);
	}
	private boolean SaveSampleAdjacenciesBinary(File file)
	{
		try {
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			dos.writeInt(voronoiList.size());
			for(SamplePoint sp : voronoiList)
				sp.WriteAdjacencies(dos);
			dos.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean SaveSampleAdjacenciesVerbose(File file)
	{
		try
		{
			BufferedWriter wr = new BufferedWriter(new FileWriter(file));
			for(SamplePoint sp : voronoiList)
			{
				String desc = sp.GetAdjacencyDescription();
				wr.write(desc);
				wr.newLine();
			}
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean LoadSampleAdjacencies()
	{
		boolean binary = true;
		File sampleAdjFile = new File(GetSampleAdjFileName(binary));
		if(!sampleAdjFile.exists() || !sampleAdjFile.isFile())
			binary = false;
		sampleAdjFile = new File(GetSampleAdjFileName(binary));
		if(!sampleAdjFile.exists() || !sampleAdjFile.isFile())
			return false;
		if(binary)
			return LoadSampleAdjacenciesBinary(sampleAdjFile);
		else
			return LoadSampleAdjacenciesVerbose(sampleAdjFile);
	}
	private boolean LoadSampleAdjacenciesBinary(File file)
	{
		try {
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
			int num = dis.readInt();
			boolean hadIssue = false;
			for(int count = 0; count < num; count++)
			{
				SamplePoint p = voronoiList.get(count);
				if(!p.LoadAdjacencies(dis, parent))
					hadIssue = true;
			}
			dis.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadSampleAdjacenciesVerbose(File file)
	{
		try {
			boolean hadIssue = false;
			Scanner std = new Scanner(file);
			int index = 0;
			while(std.hasNextLine())
			{
				String desc = std.nextLine();
				SamplePoint curr = voronoiList.get(index);
				curr.SetAdjacenciesFromDescription(desc, parent);
				index++;
			}
			std.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private String GetSampleListFileName(boolean binary)
	{
		if(binary)
			return GetDirectory() + File.separator + "SampleList.bin";
		else
			return GetDirectory() + File.separator + "SampleList.txt";
	}
	public boolean SaveSampleList(boolean binary)
	{
		File sampleListFile = new File(GetSampleListFileName(binary));
		if(sampleListFile.exists())
		{
			if(!sampleListFile.delete())
				return false;
		}
		if(binary)
			return SaveSampleListBinary(sampleListFile);
		else
			return SaveSampleListVerbose(sampleListFile);
		
	}
	private boolean SaveSampleListBinary(File file)
	{
		try {
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			dos.writeInt(voronoiList.size());
			for(SamplePoint sp : voronoiList)
				sp.WriteDescription(dos);
			dos.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean SaveSampleListVerbose(File file)
	{
		try
		{
			BufferedWriter wr = new BufferedWriter(new FileWriter(file));
			for(SamplePoint sp : voronoiList)
			{
				String desc = sp.GetDescription();
				wr.write(desc);
				wr.newLine();
			}
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	public boolean LoadSampleList()
	{
		boolean binary = true;
		File sampleListFile = new File(GetSampleListFileName(binary));
		if(!sampleListFile.exists() || !sampleListFile.isFile())
			binary = false;
		sampleListFile = new File(GetSampleListFileName(binary));
		if(!sampleListFile.exists() || !sampleListFile.isFile())
			return false;
		if(binary)
			return LoadSampleListBinary(sampleListFile);
		else
			return LoadSampleListVerbose(sampleListFile);
		
	}
	private boolean LoadSampleListBinary(File file)
	{
		if(voronoiList.size() > 0)
			return false;
		try {
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
			int num = dis.readInt();
			boolean hadIssue = false;
			voronoiList.ensureCapacity(num);
			for(int count = 0; count < num; count++)
			{
				SamplePoint p = new SamplePoint(dis, this);
				if(p.x - GetWorldX() < 0 || p.y - GetWorldY() < 0)
				{
					hadIssue = true;
					continue;
				}
				int i = (int) ((p.x - GetWorldX()) * VORONOI_DIM);
				int j = (int) ((p.y - GetWorldY()) * VORONOI_DIM);
				if(i >= VORONOI_DIM || j >= VORONOI_DIM)
				{
					hadIssue = true;
					continue;
				}
				if(terrainCells[i * VORONOI_DIM + j] == null)
				{
					terrainCells[i * VORONOI_DIM + j] = p;
					int index = voronoiList.size();
					voronoiList.add(p);
					p.SetContainerIndex(index);
				}
			}
			dis.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean LoadSampleListVerbose(File file)
	{
		if(voronoiList.size() > 0)
			return false;
		try {
			boolean hadIssue = false;
			Scanner std = new Scanner(file);
			while(std.hasNextLine())
			{
				String desc = std.nextLine();
				Iterator<String> tokenStream = Arrays.stream(desc.split(" ")).iterator();
				SamplePoint p = new SamplePoint(tokenStream, this);
				if(p.x - GetWorldX() < 0 || p.y - GetWorldY() < 0)
				{
					hadIssue = true;
					continue;
				}
				int i = (int) ((p.x - GetWorldX()) * VORONOI_DIM);
				int j = (int) ((p.y - GetWorldY()) * VORONOI_DIM);
				if(i >= VORONOI_DIM || j >= VORONOI_DIM)
				{
					hadIssue = true;
					continue;
				}
				if(terrainCells[i * VORONOI_DIM + j] == null)
				{
					terrainCells[i * VORONOI_DIM + j] = p;
					int index = voronoiList.size();
					voronoiList.add(p);
					p.SetContainerIndex(index);
				}
			}
			std.close();
			return !hadIssue;
		} catch (IOException e) {
			return false;
		}
	}
	private String GetBasicDescName()
	{
		return GetDirectory() + File.separator + "BasicDesc.txt";
	}
	private boolean SaveBasicDescription()
	{
		File basicData = new File(GetBasicDescName());
		if(basicData.exists())
		{
			if(!basicData.delete())
				return false;
		}
		try {
			BufferedWriter wr = new BufferedWriter(new FileWriter(basicData));
			wr.write(Integer.toString(x));
			wr.write(" ");
			wr.write(Integer.toString(y));
			wr.newLine();
			wr.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	private boolean EnsureDirectoryStructureForDataImages(String imageDirectoryPrefix)
	{
		for(int dim : DataImage.dimRange)
		{
			String folderName = GetDirectory();
			folderName += File.separator;
			folderName += imageDirectoryPrefix;
			folderName += Integer.toString(dim);
			File lmDir = new File(folderName);
			if(lmDir.exists() && !lmDir.isDirectory())
				lmDir.delete();
			if(!lmDir.exists())
				lmDir.mkdir();
			if(!lmDir.exists() || !lmDir.isDirectory())
				return false;
			if(Switches.CLEAR_IMAGE_CACHES)
			{
				for(File exist : lmDir.listFiles())
				{
					exist.delete();
				}
			}
		}
		return true;
	}
	private boolean EnsureBasicDirectoryStructureExists()
	{
		File topDir = new File(GetDirectory());
		if(topDir.exists() && !topDir.isDirectory())
			topDir.delete();
		if(!topDir.exists())
			topDir.mkdirs();
		if(!topDir.exists() || !topDir.isDirectory())
			return false;
		
		if(!EnsureDirectoryStructureForDataImages(K_HEIGHTMAP_FOLDER_NAME))
			return false;
		if(!EnsureDirectoryStructureForDataImages(K_WATERMAP_FOLDER_NAME))
			return false;
		if(!EnsureDirectoryStructureForDataImages(K_RAINFLOWMAP_FOLDER_NAME))
			return false;
		if(!EnsureDirectoryStructureForDataImages(K_SEDIMENTMAP_FOLDER_NAME))
			return false;
		
		return true;
	}
	public String GetDirectory()
	{
		String dir = parent.GetDirectory() + File.separator;
		dir += WorldMap.K_REGIONS_FOLDER_NAME + File.separator;
		dir += GetDirectoryName();
		return dir;
	}
	public String GetDirectoryName()
	{
		return "RegionalMap_" + GetWorldX() + "_" + GetWorldY();
	}
	public static void ScrollOriginOffsetForOptimalCoastliness()
	{
		int bestK = 0;
		int numLandTilesInBest = 0;
		for(int k = 0; k < 200; k++)
		{
			int modifiedOffset = ORIGIN_OFFSET + k;
			int numTiles = 0;
			
			for(int i = 0; i < DIMENSION; i++)
			{
				for(int j = 0; j < DIMENSION; j++)
				{
					double x = 1.0 * i / DIMENSION + modifiedOffset;
					double y = 1.0 * j / DIMENSION + modifiedOffset;
					if(Perlin.oceans.UnderThreshold(x, y))
						numTiles++;
				}
			}
			if(numTiles > numLandTilesInBest)
			{
				bestK = k;
				numLandTilesInBest = numTiles;
			}
		}
		ORIGIN_OFFSET = ORIGIN_OFFSET + bestK;
	}
	public SamplePoint GetSamplePoint(int index)
	{
		if(index < 0 || index >= voronoiList.size())
			return null;
		return voronoiList.get(index);
	}
	public void CalculateAllVoronoiAdjacencies()
	{
		for(int i = 0; i < voronoiList.size(); i++)
		{
			voronoiList.get(i).CalculateAdjacencies();
		}
	}
	public boolean IsRidge(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		SamplePoint vp = GetNearest(x, y);
		if(vp == null)
			return false;

		SamplePoint[] triangle = VoronoiAlgorithms.FindContainingSampleTriangle(x, y, vp);
		if(triangle == null)
			return false;
		
		for(SamplePoint a : triangle)
		{
			for(SamplePoint b : triangle)
			{
				if(a == b)
					continue;
				MeshConnection m = a.GetConnection(b);
				if(m == null)
					continue;
				if(!m.IsRidgeline())
					continue;
				
				double pToAX = (a.x - x);
				double pToAY = (a.y - y);
				double aToBX = (b.x - a.x);
				double aToBY = (b.y - a.y);
				double pToBX = (b.x - x);
				double pToBY = (b.y - y);
				double aDist = Math.sqrt(pToAX * pToAX + pToAY * pToAY);
				double bDist = Math.sqrt(pToBX * pToBX + pToBY * pToBY);
				
				double perpX = aToBY;
				double perpY = -aToBX;
				perpX /= m.GetLength();
				perpY /= m.GetLength();
				double perpDist = Math.abs(perpX * pToAX + perpY * pToAY);
				
				if(aDist < 0.0001 || bDist < 0.0001 || perpDist < 0.0001)
					return true;
			}
		}
		return false;
	}
	public double DrainageBasedRiverPercent(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		SamplePoint vp = GetNearest(x, y);
		if(vp == null)
			return 0;

		MeshPoint[] triangle = VoronoiAlgorithms.FindContainingTriangle(x, y, vp);
		if(triangle == null)
			return 0;
		double rain = 10000 * 4;
		double highestPercent = 0;
		for(MeshPoint v : triangle)
		{
			double pToVX = (v.x - x);
			double pToVY = (v.y - y);
			double pToV = Math.sqrt(pToVX * pToVX + pToVY * pToVY);
			highestPercent = Math.max(highestPercent, RiverFlowPercent(rain * v.GetDrainageArea(), pToV));
			if(v.GetDrainageSink() != null)
			{
				double vToOX = v.GetDrainageSink().x - v.x;
				double vToOY = v.GetDrainageSink().y - v.y;
				if(vToOX * pToVX + vToOY * pToVY < 0)
				{
					double perpX = vToOY;
					double perpY = -vToOX;
					perpX /= v.DistTo(v.GetDrainageSink());
					perpY /= v.DistTo(v.GetDrainageSink());
					double perpDist = Math.abs(perpX * pToVX + perpY * pToVY);
					highestPercent = Math.max(highestPercent, RiverFlowPercent(rain * v.GetDrainageArea(), perpDist));
				}
			}
			for(MeshPoint s : v.GetDrainageSources())
			{
				double pToSX = (s.x - x);
				double pToSY = (s.y - y);
				double pToS = Math.sqrt(pToSX * pToSX + pToSY * pToSY);
				highestPercent = Math.max(highestPercent, RiverFlowPercent(rain * s.GetDrainageArea(), pToS));
				
				double sToOX = v.x - s.x;
				double sToOY = v.y - s.y;
				if(sToOX * pToVX + sToOY * pToVY > 0)
				{
					double perpX = sToOY;
					double perpY = -sToOX;
					perpX /= s.DistTo(v);
					perpY /= s.DistTo(v);
					double perpDist = Math.abs(perpX * pToSX + perpY * pToSY);
					highestPercent = Math.max(highestPercent, RiverFlowPercent(rain * s.GetDrainageArea(), perpDist));
				}
			}
		}
		return highestPercent;
	}
	public double RiverPercent(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		SamplePoint vp = GetNearest(x, y);
		if(vp == null)
			return 0;

		SamplePoint[] triangle = VoronoiAlgorithms.FindContainingSampleTriangle(x, y, vp);
		if(triangle == null)
			return 0;

		double highestPercent = 0;
		for(SamplePoint v : triangle)
		{
			double pToVX = (v.x - x);
			double pToVY = (v.y - y);
			double pToV = Math.sqrt(pToVX * pToVX + pToVY * pToVY);
			highestPercent = Math.max(highestPercent, RiverFlowPercent(v.GetRiverFlow(), pToV));
			if(v.GetRiverOutlet() != null)
			{
				double vToOX = v.GetRiverOutlet().x - v.x;
				double vToOY = v.GetRiverOutlet().y - v.y;
				if(vToOX * pToVX + vToOY * pToVY < 0)
				{
					double perpX = vToOY;
					double perpY = -vToOX;
					perpX /= v.DistTo(v.GetRiverOutlet());
					perpY /= v.DistTo(v.GetRiverOutlet());
					double perpDist = Math.abs(perpX * pToVX + perpY * pToVY);
					highestPercent = Math.max(highestPercent, RiverFlowPercent(v.GetRiverFlow(), perpDist));
				}
			}
			for(SamplePoint s : v.GetRiverInlets())
			{
				double pToSX = (s.x - x);
				double pToSY = (s.y - y);
				double pToS = Math.sqrt(pToSX * pToSX + pToSY * pToSY);
				highestPercent = Math.max(highestPercent, RiverFlowPercent(s.GetRiverFlow(), pToS));
				
				double sToOX = v.x - s.x;
				double sToOY = v.y - s.y;
				if(sToOX * pToVX + sToOY * pToVY > 0)
				{
					double perpX = sToOY;
					double perpY = -sToOX;
					perpX /= s.DistTo(v);
					perpY /= s.DistTo(v);
					double perpDist = Math.abs(perpX * pToSX + perpY * pToSY);
					highestPercent = Math.max(highestPercent, RiverFlowPercent(s.GetRiverFlow(), perpDist));
				}
			}
		}
		return highestPercent;
	}
	private double RiverFlowPercent(double flow, double distanceFromFlow)
	{
		double invisibleFlowThreshold = 20;//20;
		double fullVisibleFlowThreshold = 800;//800;
		if(flow < invisibleFlowThreshold)
			return 0;
		if(distanceFromFlow > MIN_VORONOI_DIST)
			return 0;
		double distScaled = distanceFromFlow / 0.0002;
		double flowWidth = Math.pow(flow, 0.5);
		double zeroPercentDist = flowWidth - Math.pow(invisibleFlowThreshold, 0.5);
		double fullPercentDist = flowWidth - Math.pow(fullVisibleFlowThreshold, 0.5);
		if(distScaled > zeroPercentDist)
			return 0;
		if(distScaled < fullPercentDist)
			return 1;
		return (zeroPercentDist - distScaled) / (zeroPercentDist - fullPercentDist);
	}
	public HashMap<LocalMap, Boolean> PrepareForExtensiveEditing()
	{
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
		for(int i = 0; i < DIMENSION; i++)
		{
			for(int j = 0; j < DIMENSION; j++)
			{
				System.out.println("Preparing Local Map " + i + ", " + j);
				LocalMap lm = topography[i * DIMENSION + j];
				boolean alreadyActive = lm.PrepareForEditing(true, true, true);
				used.put(lm, alreadyActive);
			}
		}
		System.out.println();
		for(int i = 0; i < DIMENSION; i++)
		{
			LocalMap nEdge = topography[i * DIMENSION];
			LocalMap sEdge = topography[i * DIMENSION + DIMENSION - 1];
			LocalMap north = nEdge.GetNorth();
			LocalMap south = sEdge.GetSouth();
			if(north != null)
			{
				boolean alreadyActive = north.PrepareForEditing(true, true, true);
				used.put(north, alreadyActive);
			}
			if(south != null)
			{
				boolean alreadyActive = south.PrepareForEditing(true, true, true);
				used.put(south, alreadyActive);
			}
			if(i == 0 && north != null)
			{
				LocalMap northWest = north.GetWest();
				if(northWest != null)
				{
					boolean alreadyActive = northWest.PrepareForEditing(true, true, true);
					used.put(northWest, alreadyActive);
				}
			}
			if(i == 0 && south != null)
			{
				LocalMap southWest = south.GetWest();
				if(southWest != null)
				{
					boolean alreadyActive = southWest.PrepareForEditing(true, true, true);
					used.put(southWest, alreadyActive);
				}
			}
			if(i == DIMENSION - 1 && north != null)
			{
				LocalMap northEast = north.GetEast();
				if(northEast != null)
				{
					boolean alreadyActive = northEast.PrepareForEditing(true, true, true);
					used.put(northEast, alreadyActive);
				}
			}
			if(i == DIMENSION - 1 && south != null)
			{
				LocalMap southEast = south.GetEast();
				if(southEast != null)
				{
					boolean alreadyActive = southEast.PrepareForEditing(true, true, true);
					used.put(southEast, alreadyActive);
				}
			}
		}
		for(int j = 0; j < DIMENSION; j++)
		{
			LocalMap wEdge = topography[j];
			LocalMap eEdge = topography[(DIMENSION - 1) * DIMENSION + j];
			LocalMap west = wEdge.GetWest();
			LocalMap east = eEdge.GetEast();
			if(west != null)
			{
				boolean alreadyActive = west.PrepareForEditing(true, true, true);
				used.put(west, alreadyActive);
			}
			if(east != null)
			{
				boolean alreadyActive = east.PrepareForEditing(true, true, true);
				used.put(east, alreadyActive);
			}
		}
		return used;
	}
	public void RunFullPhasedErosion()
	{
		ArrayList<LocalMap> targets = new ArrayList<LocalMap>();
		for(int i = 0; i < DIMENSION; i++)
		{
			for(int j = 0; j < DIMENSION; j++)
			{
				LocalMap lm = topography[i * DIMENSION + j];
				targets.add(lm);
			}
		}
		HashMap<LocalMap, Boolean> used = PrepareForExtensiveEditing();
		for(int n = 0; n < 50; n++)
		{
			System.out.println("********");
			System.out.println("LAPLACE IT " + n);
			System.out.println("********\n");
			for(int i = 0; i < 2; i++)
			{
				for(int j = 0; j < 2; j++)
				{
					final int innerI = i;
					final int innerJ = j;
					int blockSize = DIMENSION / 2;
					IntStream.range(0, blockSize * blockSize).parallel().forEach(index ->{
						int myI = index / blockSize;
						int myJ = index % blockSize;
						int realI = innerI + myI * 2;
						int realJ = innerJ + myJ * 2;
						LocalMap target = topography[realI * DIMENSION + realJ];
						target.LaplacianErosionIteration(1);
					});
				}
			}
		}
		
		for(int i = 0; i < 4; i++)
		{
			for(int j = 0; j < 4; j++)
			{
				final int innerI = i;
				final int innerJ = j;
				System.out.println("********");
				System.out.println("RAIN 1 BLOCK " + i + ", " + j);
				System.out.println("********\n");
				int blockSize = DIMENSION / 4;
				IntStream.range(0, blockSize * blockSize).parallel().forEach(index ->{
					int myI = index / blockSize;
					int myJ = index % blockSize;
					int realI = innerI + myI * 4;
					int realJ = innerJ + myJ * 4;
					LocalMap target = topography[realI * DIMENSION + realJ];
					for(int drop = 0; drop < 10000; drop++)
					{
						WaterDroplet nova = new WaterDroplet(target, 1, used, false);
						boolean okay = true;
						while(okay)
						{
							okay = nova.OneErosionStep();
						}
					}
				});
			}
		}
		
		System.out.println("********");
		System.out.println("HYDROLOGY 1");
		System.out.println("********\n");
		LocalTerrainAlgorithms.GuaranteeConsistentHydrology(targets, true);

		for(int i = 0; i < 4; i++)
		{
			for(int j = 0; j < 4; j++)
			{
				final int innerI = i;
				final int innerJ = j;
				System.out.println("********");
				System.out.println("RAIN 2 BLOCK " + i + ", " + j);
				System.out.println("********\n");
				int blockSize = DIMENSION / 4;
				IntStream.range(0, blockSize * blockSize).parallel().forEach(index ->{
					int myI = index / blockSize;
					int myJ = index % blockSize;
					int realI = innerI + myI * 4;
					int realJ = innerJ + myJ * 4;
					LocalMap target = topography[realI * DIMENSION + realJ];
					for(int drop = 0; drop < 10000; drop++)
					{
						WaterDroplet nova = new WaterDroplet(target, 1, used, false);
						boolean okay = true;
						while(okay)
						{
							okay = nova.OneErosionStep();
						}
					}
				});
			}
		}
		
		System.out.println("********");
		System.out.println("HYDROLOGY 2");
		System.out.println("********\n");
		LocalTerrainAlgorithms.GuaranteeConsistentHydrology(targets, true);
		
		System.out.println("********");
		System.out.println("RAIN FLOW");
		System.out.println("********\n");
		for(LocalMap t : targets)
			t.SendEvenRain();
		LocalTerrainAlgorithms.SendRainDownhill(targets, true);
		
		for(Entry<LocalMap, Boolean> lm : used.entrySet())
		{
			lm.getKey().CompleteEditing(true, true, true, !lm.getValue());
		}
	}
	public void RunFullLaplacianErosion()
	{
		int numIterations = 50;
		
		HashMap<LocalMap, Boolean> used = PrepareForExtensiveEditing();
		
		for(int n = 0; n < numIterations; n++)
		{
			System.out.println("********");
			System.out.println("LPLIT " + n);
			System.out.println("********");
			for(int i = 0; i < 2; i++)
			{
				for(int j = 0; j < 2; j++)
				{
					final int innerI = i;
					final int innerJ = j;
					System.out.println("\nStarting Laplace Erosion on block " + i + ", " + j + "\n");
					int blockSize = DIMENSION / 2;
					IntStream.range(0, blockSize * blockSize).parallel().forEach(index ->{
						int myI = index / blockSize;
						int myJ = index % blockSize;
						int realI = innerI + myI * 2;
						int realJ = innerJ + myJ * 2;
						LocalMap target = topography[realI * DIMENSION + realJ];
						target.LaplacianErosionIteration(1);
					});
				}
			}
		}
		
		for(Entry<LocalMap, Boolean> lm : used.entrySet())
		{
			lm.getKey().CompleteEditing(true, true, true, !lm.getValue());
		}
	}
	public void RunFullRain()
	{
		int numDroplets = 5000;
		
		HashMap<LocalMap, Boolean> used = PrepareForExtensiveEditing();
		
		for(int i = 0; i < 4; i++)
		{
			for(int j = 0; j < 4; j++)
			{
				final int innerI = i;
				final int innerJ = j;
				System.out.println("\n\nStarting rain on block " + i + ", " + j + "\n\n");
				int blockSize = DIMENSION / 4;
				IntStream.range(0, blockSize * blockSize).parallel().forEach(index ->{
					int myI = index / blockSize;
					int myJ = index % blockSize;
					int realI = innerI + myI * 4;
					int realJ = innerJ + myJ * 4;
					System.out.println("Raining on map " + realI + ", " + realJ);
					LocalMap target = topography[realI * DIMENSION + realJ];
					for(int drop = 0; drop < numDroplets; drop++)
					{
						WaterDroplet nova = new WaterDroplet(target, 1, used, false);
						boolean okay = true;
						while(okay)
						{
							okay = nova.OneErosionStep();
						}
					}
				});
			}
		}
		for(Entry<LocalMap, Boolean> lm : used.entrySet())
		{
			lm.getKey().CompleteEditing(true, true, true, !lm.getValue());
		}
	}
	public double GetElevation(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		LocalMap.Coordinate lmc = GetLocalMapAt(x - GetWorldX(), y - GetWorldY());
		if(lmc == null)
			return 0;
		if(lmc.GetLocalMap() == null)
			return 0;
		return lmc.GetLocalMap().GetHeight(lmc.x, lmc.y);
	}
	public LocalMap.WatermapValue IsWaterPoint(double wX, double wY)
	{
		double xub = wX / RegionalMap.DIMENSION;
		double yub = wY / RegionalMap.DIMENSION;
		double xb = xub;
		double yb = yub;
		if(Switches.USE_BLURRING)
		{
			double blurredX = Perlin.blurX.Get(xub, yub);
			double blurredY = Perlin.blurY.Get(xub, yub);
			xb += Perlin.BLUR_DISTANCE * blurredX / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
			yb += Perlin.BLUR_DISTANCE * blurredY / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
		}
		
		SamplePoint vp = GetNearest(xb, yb);
		if(vp == null)
			return LocalMap.WatermapValue.Unknown;

		MeshPoint[] triangle = VoronoiAlgorithms.FindContainingTriangle(xb, yb, vp);
		if(triangle == null)
		{
			if(vp.IsOcean())
				return LocalMap.WatermapValue.Ocean;
			else if(vp.IsInlandLake())
				return LocalMap.WatermapValue.Lake;
			else
				return LocalMap.WatermapValue.Unknown;
		}
		boolean anyOcean = false;
		boolean anyLake = false;
		boolean anyLand = false;
		for(MeshPoint mp : triangle)
		{
			if(mp.IsOcean())
				anyOcean = true;
			else if(mp.IsInlandLake())
				anyLake = true;
			else
				anyLand = true;
		}
		if(anyLand)
			return LocalMap.WatermapValue.NotWater;
		else if(anyLake)
			return LocalMap.WatermapValue.Lake;
		else if(anyOcean)
			return LocalMap.WatermapValue.Ocean;
		else
			return LocalMap.WatermapValue.Unknown;
	}
	public double CalculateBaseSedimentDepth(double wX, double wY)
	{
		double xub = wX / RegionalMap.DIMENSION;
		double yub = wY / RegionalMap.DIMENSION;
		double xb = xub;
		double yb = yub;
		if(Switches.USE_BLURRING)
		{
			double blurredX = Perlin.blurX.Get(xub, yub);
			double blurredY = Perlin.blurY.Get(xub, yub);
			xb += Perlin.BLUR_DISTANCE * blurredX / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
			yb += Perlin.BLUR_DISTANCE * blurredY / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
		}
		
		SamplePoint vp = GetNearest(xb, yb);
		if(vp == null)
			return 0;
		
		double sedDep = Perlin.sedimentDepth.Get(xub, yub);
		sedDep = Math.abs(sedDep);
		//if(sedDep < 0.05)
		//	sedDep = 0;
		//sedDep = (sedDep - 0.05) * 30;
		if(sedDep > 0.2)
			return 0;
		sedDep = (0.2 - sedDep) * 150;
		
		SamplePoint[] triangle = VoronoiAlgorithms.FindContainingSampleTriangle(xb, yb, vp);
		if(triangle == null)
		{
			return vp.GetBaseSedimentDepth() + sedDep;
		}
		
		double[] lerp = VoronoiAlgorithms.BarycentricCoordinates(xb, yb, triangle);
		double lerpedVal = 0;
		for(int i = 0; i < triangle.length; i++)
		{
			lerpedVal += triangle[i].GetBaseSedimentDepth() * lerp[i];
		}
		return lerpedVal + sedDep;
	}
	public double CalculateElevation(double wX, double wY)
	{
		double xub = wX / RegionalMap.DIMENSION;
		double yub = wY / RegionalMap.DIMENSION;
		double xb = xub;
		double yb = yub;
		if(Switches.USE_BLURRING)
		{
			double blurredX = Perlin.blurX.Get(xub, yub);
			double blurredY = Perlin.blurY.Get(xub, yub);
			xb += Perlin.BLUR_DISTANCE * blurredX / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
			yb += Perlin.BLUR_DISTANCE * blurredY / (RegionalMap.DIMENSION * LocalMap.METER_DIM);
		}
		
		SamplePoint vp = GetNearest(xb, yb);
		if(vp == null)
			return 0;

		MeshPoint[] triangle = VoronoiAlgorithms.FindContainingTriangle(xb, yb, vp);
		if(triangle == null)
			return vp.GetElevation();
		
		double elev = 0;
		double[] lerp = VoronoiAlgorithms.BarycentricCoordinates(xb, yb, triangle);
		double[] perlinContribs = new double[Perlin.elevDeltas.length];
		for(int i = 0; i < triangle.length; i++)
		{
			double elevCont = triangle[i].GetElevation();
			double[] deltaContribs = triangle[i].GetPerlinElevDiffs();
			if(elevCont == Double.MAX_VALUE)
				elevCont = 0;
			elev += elevCont * lerp[i];
			for(int j = 0; j < perlinContribs.length; j++)
				perlinContribs[j] += lerp[i] * deltaContribs[j];
		}

		for(int i = 0; i < Perlin.elevDeltas.length; i++)
		{
			double del = Perlin.elevDeltas[i].Get(xub, yub);
			double ctr = perlinContribs[i];
			double amp = Perlin.elevDeltaScales[i];
			elev += amp * ctr * del;
		}
		if(elev < 0)
			return 0;
		return elev;
		/*double rockJitter = Perlin.rockyJitters.Get(xub, yub);
		if(rockJitter > 0.25)
		{
			rockJitter -= 0.25;
			rockJitter *= Perlin.rockJitterScale;
			rockJitter *= perlinContribs[1];
			//elev += rockJitter;
		}
		else if(rockJitter < -0.25)
		{
			rockJitter += 0.25;
			rockJitter *= -1;
			rockJitter *= Perlin.rockJitterScale;
			rockJitter *= perlinContribs[1];
			//elev += rockJitter;
		}*/
		
		/*LocalMap.WatermapValue isWater = IsWaterPoint(wX, wY);
		if(isWater != LocalMap.WatermapValue.NotWater)
			return elev;
		double rockJitter = Perlin.rockyJitters.Get(xub, yub);
		if(elev + rockJitter * Perlin.rockJitterScale < 0)
			return 0;
		return elev + rockJitter * Perlin.rockJitterScale;*/
	}
	public double GetElevationLaplacian(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		LocalMap.Coordinate lmc = GetLocalMapAt(x - GetWorldX(), y - GetWorldY());
		if(lmc == null)
			return 0;
		if(lmc.GetLocalMap() == null)
			return 0;
		return lmc.GetLocalMap().GetHeightLaplacian(lmc.x, lmc.y);
	}
	public Vec2 GetElevationGradient(double wX, double wY)
	{
		double x = wX / RegionalMap.DIMENSION;
		double y = wY / RegionalMap.DIMENSION;
		LocalMap.Coordinate lmc = GetLocalMapAt(x - GetWorldX(), y - GetWorldY());
		if(lmc == null)
			return new Vec2(0, 0);
		if(lmc.GetLocalMap() == null)
			return new Vec2(0, 0);
		return lmc.GetLocalMap().GetHeightGradient(lmc.x, lmc.y);
	}
	public ArrayList<SamplePoint> GetAllPoints()
	{
		return voronoiList;
	}
	public SamplePoint GetAt(int i, int j)
	{
		return terrainCells[i * VORONOI_DIM + j];
	}
	public ArrayList<SamplePoint> GetNearestN(double wX, double wY, int n)
	{
		ArrayList<SamplePoint> confirmed = new ArrayList<SamplePoint>();
		int i = (int) ((wX - GetWorldX()) * VORONOI_DIM);
		int j = (int) ((wY - GetWorldY()) * VORONOI_DIM);
		TreeMap<Double, SamplePoint> found = new TreeMap<Double, SamplePoint>();
		int d = 1;
		while(true)
		{
			for(int dI = i - d; dI <= i + d; dI++)
			{
				SamplePoint comp = GetCell(dI, j - d);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				found.put(dX * dX + dY * dY, comp);
			}
			for(int dI = i - d; dI <= i + d; dI++)
			{
				SamplePoint comp = GetCell(dI, j + d);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				found.put(dX * dX + dY * dY, comp);
			}
			for(int dJ = j - d; dJ <= j + d; dJ++)
			{
				SamplePoint comp = GetCell(i - d, dJ);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				found.put(dX * dX + dY * dY, comp);
			}
			for(int dJ = j - d; dJ <= j + d; dJ++)
			{
				SamplePoint comp = GetCell(i + d, dJ);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				found.put(dX * dX + dY * dY, comp);
			}
			//min next ring distance possible
			double mnrd = (d * 1.0) / VORONOI_DIM;
			while(!found.isEmpty() && found.firstEntry().getKey() < mnrd * mnrd)
			{
				SamplePoint value = found.pollFirstEntry().getValue();
				confirmed.add(value);
				if(confirmed.size() >= n)
					break;
			}
			if(confirmed.size() >= n)
				break;
			d++;
		}
		return confirmed;
	}
	public SamplePoint GetNearest(double wX, double wY)
	{
		int i = (int) ((wX - GetWorldX()) * VORONOI_DIM);
		int j = (int) ((wY - GetWorldY()) * VORONOI_DIM);
		
		SamplePoint best = GetCell(i, j);
		double bestDistSqr = 0;
		if(best != null)
		{
			double dX = wX - best.x;
			double dY = wY - best.y;
			bestDistSqr = dX * dX + dY * dY;
		}
		int d = 1;
		while(true)
		{
			for(int dI = i - d; dI <= i + d; dI++)
			{
				SamplePoint comp = GetCell(dI, j - d);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				if(best == null || bestDistSqr > dX * dX + dY * dY)
				{
					best = comp;
					bestDistSqr = dX * dX + dY * dY;
				}
			}
			for(int dI = i - d; dI <= i + d; dI++)
			{
				SamplePoint comp = GetCell(dI, j + d);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				if(best == null || bestDistSqr > dX * dX + dY * dY)
				{
					best = comp;
					bestDistSqr = dX * dX + dY * dY;
				}
			}
			for(int dJ = j - d; dJ <= j + d; dJ++)
			{
				SamplePoint comp = GetCell(i - d, dJ);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				if(best == null || bestDistSqr > dX * dX + dY * dY)
				{
					best = comp;
					bestDistSqr = dX * dX + dY * dY;
				}
			}
			for(int dJ = j - d; dJ <= j + d; dJ++)
			{
				SamplePoint comp = GetCell(i + d, dJ);
				if(comp == null)
					continue;
				double dX = wX - comp.x;
				double dY = wY - comp.y;
				if(best == null || bestDistSqr > dX * dX + dY * dY)
				{
					best = comp;
					bestDistSqr = dX * dX + dY * dY;
				}
			}
			//min next ring distance possible
			double mnrd = (d * 1.0) / VORONOI_DIM;
			if(best != null && bestDistSqr < mnrd * mnrd)
				break;
			if(d > 5)
				break;
			d++;
		}
		
		return best;
	}
	public boolean ExistingPointNearby(SamplePoint cand)
	{
		int i = (int) ((cand.x - GetWorldX()) * VORONOI_DIM);
		int j = (int) ((cand.y - GetWorldY()) * VORONOI_DIM);
		for(int dI = -2; dI <= 2; dI++)
			for(int dJ = -2; dJ <= 2; dJ++)
			{
				SamplePoint comp = GetCell(i + dI, j + dJ);
				if(comp == null)
					continue;
				double dX = cand.x - comp.x;
				double dY = cand.y - comp.y;
				if(dX * dX + dY * dY < MIN_VORONOI_DIST * MIN_VORONOI_DIST)
					return true;
			}
		return false;
	}
	private SamplePoint GetCell(int i, int j)
	{
		if(i < 0)
		{
			RegionalMap alt = GetWest();
			if(alt == null)
				return null;
			return alt.GetCell(i + VORONOI_DIM, j);
		}
		if(j < 0)
		{
			RegionalMap alt = GetNorth();
			if(alt == null)
				return null;
			return alt.GetCell(i, j + VORONOI_DIM);
		}
		if(i >= VORONOI_DIM)
		{
			RegionalMap alt = GetEast();
			if(alt == null)
				return null;
			return alt.GetCell(i - VORONOI_DIM, j);
		}
		if(j >= VORONOI_DIM)
		{
			RegionalMap alt = GetSouth();
			if(alt == null)
				return null;
			return alt.GetCell(i, j - VORONOI_DIM);
		}
		return terrainCells[i * VORONOI_DIM + j];
	}
	public boolean SetPoint(SamplePoint p)
	{
		if(p.x - GetWorldX() < 0 || p.y - GetWorldY() < 0)
			return false;
		int i = (int) ((p.x - GetWorldX()) * VORONOI_DIM);
		int j = (int) ((p.y - GetWorldY()) * VORONOI_DIM);
		if(i >= VORONOI_DIM || j >= VORONOI_DIM)
			return false;
		if(terrainCells[i * VORONOI_DIM + j] == null)
		{
			terrainCells[i * VORONOI_DIM + j] = p;
			int index = voronoiList.size();
			p.SetContainerIndex(index);
			voronoiList.add(p);
			p.AssignVoronoiTerrainType();
			return true;
		}
		return false;
	}
	public void EnableRendering()
	{
		readyToRender = true;
	}
	public void Render(double d, Graphics2D g2)
	{
		if(!readyToRender)
		{
			g2.setColor(new Color(50, 50, 50));
			g2.fillRect(0, 0, (int) d, (int) d);
			return;
		}
		AffineTransform saved = g2.getTransform();
		double tileD = d / DIMENSION;
		for(int i = 0; i < DIMENSION; i++)
		{
			for(int j = 0; j < DIMENSION; j++)
			{
				if(g2.hitClip(0, 0, (int) tileD, (int) tileD))
				{
					LocalMap lm = topography[i * DIMENSION + j];
					lm.Render((int) tileD, g2);
					if(Switches.OUTLINE_MAPS)
					{
				 		g2.setColor(Color.red);
				 		g2.drawRect(0, 0, (int) tileD, (int) tileD);
					}
				}
				g2.translate(0, tileD);
				
			}
			g2.translate(tileD, -1 * DIMENSION * tileD);
		}
		g2.setTransform(saved);
		if(Switches.OUTLINE_MAPS)
		{
			g2.setColor(Color.green);
			g2.drawRect(0, 0, (int) d, (int) d);
		}

		
		if(Switches.PAINT_VORONOI_CENTERS)
		{
			double mvd = MIN_VORONOI_DIST * d;
			int dotMvd = 6;
			if(mvd > 12)
			{
				for(int i = 0; i < voronoiList.size(); i++)
				{
					SamplePoint p = voronoiList.get(i);
					double x = p.x - GetWorldX();
					double y = p.y - GetWorldY();
					x *= d;
					y *= d;
					int xL = (int) (x - mvd * 0.5);
					int yL = (int) (y - mvd * 0.5);
					if(!g2.hitClip(xL, yL, (int) mvd, (int) mvd))
						continue;
					
					if(mvd > 24)
					{
						g2.setColor(Color.orange);
						g2.drawOval(xL, yL, (int) (mvd), (int) (mvd));
					}

					xL = (int) (x - dotMvd * 0.5);
					yL = (int) (y - dotMvd * 0.5);
					if(p.IsWaterPoint())
					{
						g2.setColor(Color.blue);
						g2.fillOval(xL - 1, yL - 1, (int) (dotMvd) + 2, (int) (dotMvd) + 2);
					}
					g2.setColor(Color.black);
					g2.fillOval(xL, yL, (int) (dotMvd), (int) (dotMvd));
					if(mvd < 48)
						continue;
					for(SamplePoint s : p.GetAdjacentSamples())
					{
						MeshConnection con = p.GetConnection(s);
						MeshPoint m = con.GetMid();
						if(m == null)
							continue;
						x = m.x - GetWorldX();
						y = m.y - GetWorldY();
						x *= d;
						y *= d;
						
						xL = (int) (x - dotMvd * 0.5);
						yL = (int) (y - dotMvd * 0.5);
						if(!g2.hitClip(xL, yL, (int) (dotMvd), (int) (dotMvd)))
							continue;
						
						if(m.IsWaterPoint())
						{
							g2.setColor(Color.blue);
							g2.fillOval(xL - 1, yL - 1, (int) (dotMvd) + 2, (int) (dotMvd) + 2);
						}
						g2.setColor(Color.green);
						g2.fillOval(xL, yL, (int) (dotMvd), (int) (dotMvd));
						
						if(mvd < 96)
							continue;
						for(MeshPoint mAdj : m.GetAdjacent())
						{
							MeshConnection mCon = m.GetConnection(mAdj);
							if(mCon == null)
								continue;
							MeshPoint mm = mCon.GetMid();
							if(mm == null)
								continue;
							x = mm.x - GetWorldX();
							y = mm.y - GetWorldY();
							x *= d;
							y *= d;
							
							xL = (int) (x - dotMvd * 0.5);
							yL = (int) (y - dotMvd * 0.5);
							if(!g2.hitClip(xL, yL, (int) (dotMvd), (int) (dotMvd)))
								continue;
							
							if(mm.IsWaterPoint())
							{
								g2.setColor(Color.blue);
								g2.fillOval(xL - 1, yL - 1, (int) (dotMvd) + 2, (int) (dotMvd) + 2);
							}
							g2.setColor(Color.red);
							g2.fillOval(xL, yL, (int) (dotMvd), (int) (dotMvd));
							
							if(mvd < 128)
								continue;
							for(MeshPoint mmadj : mm.GetAdjacent())
							{
								MeshConnection mmCon = mm.GetConnection(mmadj);
								if(mmCon == null)
									continue;
								MeshPoint mmm = mmCon.GetMid();
								if(mmm == null)
									continue;								
								x = mmm.x - GetWorldX();
								y = mmm.y - GetWorldY();
								x *= d;
								y *= d;
								
								xL = (int) (x - dotMvd * 0.5);
								yL = (int) (y - dotMvd * 0.5);
								if(!g2.hitClip(xL, yL, (int) (dotMvd), (int) (dotMvd)))
									continue;
								g2.setColor(Color.blue);
								g2.fillOval(xL, yL, (int) (dotMvd), (int) (dotMvd));
							}
						}
					}
				
				}
			}
			
		}
	}
	public LocalMap GetLocalMapAt(int x, int y)
	{
		if(x < 0 || y < 0)
			return null;
		if(x > DIMENSION || y > DIMENSION)
			return null;
		return topography[x * DIMENSION + y];
	}
	public int GetOriginX()
	{
		return x;
	}
	public int GetOriginY()
	{
		return y;
	}
	public int GetWorldX()
	{
		return x + ORIGIN_OFFSET;
	}
	public int GetWorldY()
	{
		return y + ORIGIN_OFFSET;
	}
	public RegionalMap GetNorth()
	{
		return parent.GetRegion(x, y-1);
	}
	public RegionalMap GetEast()
	{
		return parent.GetRegion(x+1, y);
	}
	public RegionalMap GetWest()
	{
		return parent.GetRegion(x-1, y);
	}
	public RegionalMap GetSouth()
	{
		return parent.GetRegion(x, y+1);
	}
	
	//The assumption here is that the x, y are in the [0, 1] interval
	public LocalMap.Coordinate GetLocalMapAt(double x, double y)
	{
		x *= DIMENSION;
		y *= DIMENSION;
		int xIndex = (int) x;
		int yIndex = (int) y;
		if(xIndex < 0 || xIndex >= DIMENSION)
			return null;
		if(yIndex < 0 || yIndex >= DIMENSION )
			return null;
		
		x -= xIndex;
		y -= yIndex;
		
		return topography[xIndex * DIMENSION + yIndex].new Coordinate(x, y);
	}
	public class Coordinate
	{
		public double x, y;
		public Coordinate(double x, double y)
		{
			this.x = x;
			this.y = y;
		}
		public RegionalMap GetRegionalMap()
		{
			return RegionalMap.this;
		}
	}
		
	public static final int VORONOI_DIM = 240;
	public static final double MIN_VORONOI_DIST = Math.sqrt(2) / VORONOI_DIM;
	public static final int DIMENSION = 20;
	//if a RegionalMap gets negative coordinates, bad things happen to Perlin RNG
	public static int ORIGIN_OFFSET = 500;
	public static final String K_HEIGHTMAP_FOLDER_NAME = "Local_Heights_";
	public static final String K_WATERMAP_FOLDER_NAME = "Local_Watermaps_";
	public static final String K_RAINFLOWMAP_FOLDER_NAME = "Local_Rainflowmaps_";
	public static final String K_SEDIMENTMAP_FOLDER_NAME = "Local_Sedimentmaps_";

}