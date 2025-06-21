import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

public class LocalTerrainAlgorithms
{
	public static void EndPixelOperation(
			HashMap<LocalMap, Boolean> used, 
			boolean height, boolean water, boolean rainflow,
			boolean cleanDrainRecord, boolean cleanPixelStatus)
	{
		for(Entry<LocalMap, Boolean> map : used.entrySet())
		{
			if(cleanDrainRecord)
				map.getKey().DestroyDrainRecord();
			if(cleanPixelStatus)
				map.getKey().DestroyPixelStatus();
			map.getKey().ResetActivityFlag();
			map.getKey().CompleteEditing(height, water, rainflow, !map.getValue());
		}
	}
	public static ArrayList<LocalMap.Pixel> BeginPixelOperation(
			HashMap<LocalMap, Boolean> used, 
			ArrayList<LocalMap> targets, 
			boolean heights,
			boolean waterValues,
			boolean rainflowValues,
			boolean drainRecord,
			boolean pixelStatus)
	{
    	ArrayList<LocalMap.Pixel> pixelsToProcess = new ArrayList<LocalMap.Pixel>();
		int dim = DataImage.trueDim;
    	for(LocalMap lm : targets)
		{
    		boolean alreadyEditing = lm.PrepareForEditing(heights, waterValues, rainflowValues);
    		used.put(lm, alreadyEditing);
    		if(drainRecord)
    			lm.InitializeDrainRecord();
    		if(pixelStatus)
    			lm.InitializePixelStatus();
			lm.SetActivityFlag();
			
			//Typically, we'd do <= to hit the boundary pixel
			//But presumably we'll do the adjacent Local Map sometime too,
			//So hitting the boundary pixel would double-affect the boundary.
			//Therefore, we don't do the east / south boundary.
			//(But we do the i, j = 0, which is the west / north boundary
			for(int i = 0; i < dim; i++)
			{
				for(int j = 0; j < dim; j++)
				{
					LocalMap.Pixel nova = lm.new Pixel(i, j);
					if(pixelStatus)
						nova.SetPixelActive(true);
					pixelsToProcess.add(nova);	
				}
			}
		}

		
		return pixelsToProcess;
	}
	public static void SendRainDownhill(ArrayList<LocalMap.Pixel> allPixels)
	{
		allPixels.sort((a, b) -> 
		{
			double d = b.GetHeight() - a.GetHeight();
			if(d < 0)
				return -1;
			if(d > 0)
				return 1;
			return 0;
		});
		for(LocalMap.Pixel p : allPixels)
		{
			if(p.GetWaterType() == LocalMap.WatermapValue.Ocean)
				continue;
			boolean worked = false;
			if(Switches.CURR_FLOW_MODEL == Switches.FLOW_MODEL.D_Infinity)
				worked = DInfinityFlowRouting(p);
			else if(Switches.CURR_FLOW_MODEL == Switches.FLOW_MODEL.D4_Random)
				worked = D4RandomFlowRouting(p);
			else if(Switches.CURR_FLOW_MODEL == Switches.FLOW_MODEL.D8_Random)
				worked = D8RandomFlowRouting(p);
				
			if(!worked)
			{
				p.GetCurrentRainflow();
				DrainRecord.Dir down = p.GetDirectionInDrainRecord();
				if(down == DrainRecord.Dir.None)
				{
					System.out.println("WE WERE SUPPOSED TO GUARANTEE DOWNHILL " + p.GetStatusInDrainRecord() + " " + p.GetWaterType());
					continue;
				}
				LocalMap.Pixel towards = p.GetPixelInDir(down);
				if(towards == null)
					continue;
				towards.GetParent().ChangeRainflow(towards.x, towards.y, p.GetCurrentRainflow());
			}
		}
	}
	public static void SendRainDownhill(ArrayList<LocalMap> targets, boolean cleanDrainRecord)
	{
		GuaranteeConsistentHydrology(targets, false);
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
		ArrayList<LocalMap.Pixel> allPixels = BeginPixelOperation(used, targets, false, false, true, true, false);
		SendRainDownhill(allPixels);
		EndPixelOperation(used, false, false, true, cleanDrainRecord, false);
	}
	private static boolean D4RandomFlowRouting(LocalMap.Pixel p )
	{
		double currentRain = p.GetCurrentRainflow();
		Vec2 grad = p.GetHeightGradient();
		if(grad.Len() == 0)
		{
			return false;
		}
		grad.Normalize();
		grad.Multiply(-1);
		LocalMap.Pixel to1 = null;
		double w1 = 0;
		LocalMap.Pixel to2 = null;
		double w2 = 0;
				
		if(grad.x >= 0 && grad.y >= 0)
		{
			//Between S and E
			to1 = p.GetEast();
			w1 = grad.Dot(Vec2.UnitVector(1, 0));
			to2 = p.GetSouth();
			w2 = grad.Dot(Vec2.UnitVector(0, 1));
		}
		else if(grad.x <= 0 && grad.y >= 0)
		{
			//between W and S
			to1 = p.GetSouth();
			w1 = grad.Dot(Vec2.UnitVector(0, 1));
			to2 = p.GetWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, 0));
		}
		else if(grad.x <= 0 && grad.y <= 0)
		{
			//between W and N
			to1 = p.GetWest();
			w1 = grad.Dot(Vec2.UnitVector(-1, 0));
			to2 = p.GetNorth();
			w2 = grad.Dot(Vec2.UnitVector(0, -1));
		}
		else if(grad.x >= 0 && grad.y <= 0)
		{
			//between E and N
			to1 = p.GetNorth();
			w1 = grad.Dot(Vec2.UnitVector(0, -1));
			to2 = p.GetEast();
			w2 = grad.Dot(Vec2.UnitVector(1, 0));
		}
		else
		{
			return false;
		}
		if(to1 == null && to2 == null)
		{
			return false;
		}
		else if(to2 == null)
		{
			if(to1.GetHeight() >= p.GetHeight())
				return false;
			to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
		}
		else if(to1 == null)
		{
			if(to2.GetHeight() >= p.GetHeight())
				return false;
			to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
		}
		else
		{
			if(to1.GetHeight() >= p.GetHeight() && to2.GetHeight() >= p.GetHeight())
			{
				return false;
			}
			else if(to2.GetHeight() >= p.GetHeight())
			{
				to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
			}
			else if(to1.GetHeight() >= p.GetHeight())
			{
				to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
			}
			else
			{
				double choice = Math.random();
				if(choice > w1 / (w1 + w2))
					to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) (currentRain));
				else
					to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) (currentRain));
			}
		}
		return true;
	}
	private static boolean D8RandomFlowRouting(LocalMap.Pixel p )
	{
		double currentRain = p.GetCurrentRainflow();
		Vec2 grad = p.GetHeightGradient();
		if(grad.Len() == 0)
		{
			return false;
		}
		grad.Normalize();
		grad.Multiply(-1);
		LocalMap.Pixel to1 = null;
		double w1 = 0;
		LocalMap.Pixel to2 = null;
		double w2 = 0;
		
		LocalMap.Pixel resort = null;
		
		if(grad.x >= 0 && grad.y >= 0 && grad.x >= grad.y)
		{
			//Between S and E, with an emphasis on the E
			//So actually between E and SE
			to1 = p.GetEast();
			w1 = grad.Dot(Vec2.UnitVector(1, 0));
			to2 = p.GetSouthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, 1));
			
			resort = p.GetSouth();
		}
		else if(grad.x >= 0 && grad.y >= 0 && grad.y >= grad.x)
		{
			//Between S and E, with an emphasis on the S
			//So actually between S and SE
			to1 = p.GetSouth();
			w1 = grad.Dot(Vec2.UnitVector(0, 1));
			to2 = p.GetSouthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, 1));
			
			resort = p.GetEast();
		}
		else if(grad.x <= 0 && grad.y >= 0 && grad.y >= -1 * grad.x)
		{
			//between W and S, with an emphasis on the S
			//So actually between S and SW
			to1 = p.GetSouth();
			w1 = grad.Dot(Vec2.UnitVector(0, 1));
			to2 = p.GetSouthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, 1));
			
			resort = p.GetWest();
		}
		else if(grad.x <= 0 && grad.y >= 0 && -1 * grad.x >= grad.y)
		{
			//between W and S, with an emphasis on the W
			//So actually between W and SW
			to1 = p.GetWest();
			w1 = grad.Dot(Vec2.UnitVector(-1, 0));
			to2 = p.GetSouthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, 1));
			
			resort = p.GetSouth();
		}
		else if(grad.x <= 0 && grad.y <= 0 && -1 * grad.x >= -1 * grad.y)
		{
			//between W and N, with an emphasis on the W
			//So actually between W and NW
			to1 = p.GetWest();
			w1 = grad.Dot(Vec2.UnitVector(-1, 0));
			to2 = p.GetNorthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, -1));
			
			resort = p.GetNorth();
		}
		else if(grad.x <= 0 && grad.y <= 0 && -1 * grad.y >= -1 * grad.x)
		{
			//between W and N, with an emphasis on the N
			//So actually between N and NW
			to1 = p.GetNorth();
			w1 = grad.Dot(Vec2.UnitVector(0, -1));
			to2 = p.GetNorthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, -1));
			
			resort = p.GetWest();
		}
		else if(grad.x >= 0 && grad.y <= 0 && -1 * grad.y >= grad.x)
		{
			//between E and N, with an emphasis on the N
			//So actually between N and NE
			to1 = p.GetNorth();
			w1 = grad.Dot(Vec2.UnitVector(0, -1));
			to2 = p.GetNorthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, -1));
			
			resort = p.GetEast();
		}
		else if(grad.x >= 0 && grad.y <= 0 && grad.x >= -1 * grad.y)
		{
			//between E and N, with an emphasis on the E
			//So actually between E and NE
			to1 = p.GetEast();
			w1 = grad.Dot(Vec2.UnitVector(1, 0));
			to2 = p.GetNorthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, -1));
			
			resort = p.GetNorth();
		}
		else
		{
			return false;
		}
		if(to1 == null && to2 == null)
		{
			return false;
		}
		else if(to2 == null)
		{
			if(to1.GetHeight() >= p.GetHeight())
				return false;
			to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
		}
		else if(to1 == null)
		{
			if(to2.GetHeight() >= p.GetHeight())
				return false;
			to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
		}
		else
		{
			if(to1.GetHeight() >= p.GetHeight() && to2.GetHeight() >= p.GetHeight())
			{
				if(resort == null)
					return false;
				if(resort.GetHeight() >= p.GetHeight())
					return false;
				resort.GetParent().ChangeRainflow(resort.x, resort.y, (int) currentRain);
			}
			else if(to2.GetHeight() >= p.GetHeight())
			{
				to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
			}
			else if(to1.GetHeight() >= p.GetHeight())
			{
				to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
			}
			else
			{
				double choice = Math.random();
				if(choice > w1 / (w1 + w2))
					to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) (currentRain));
				else
					to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) (currentRain));
			}
		}
		return true;
	}
	private static boolean DInfinityFlowRouting(LocalMap.Pixel p)
	{
		double currentRain = p.GetCurrentRainflow();
		Vec2 grad = p.GetHeightGradient();
		if(grad.Len() == 0)
			return false;
		grad.Normalize();
		grad.Multiply(-1);
		LocalMap.Pixel to1 = null;
		double w1 = 0;
		LocalMap.Pixel to2 = null;
		double w2 = 0;
		
		LocalMap.Pixel resort = null;
		
		if(grad.x >= 0 && grad.y >= 0 && grad.x >= grad.y)
		{
			//Between S and E, with an emphasis on the E
			//So actually between E and SE
			to1 = p.GetEast();
			w1 = grad.Dot(Vec2.UnitVector(1, 0));
			to2 = p.GetSouthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, 1));
			
			resort = p.GetSouth();
		}
		else if(grad.x >= 0 && grad.y >= 0 && grad.y >= grad.x)
		{
			//Between S and E, with an emphasis on the S
			//So actually between S and SE
			to1 = p.GetSouth();
			w1 = grad.Dot(Vec2.UnitVector(0, 1));
			to2 = p.GetSouthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, 1));
			
			resort = p.GetEast();
		}
		else if(grad.x <= 0 && grad.y >= 0 && grad.y >= -1 * grad.x)
		{
			//between W and S, with an emphasis on the S
			//So actually between S and SW
			to1 = p.GetSouth();
			w1 = grad.Dot(Vec2.UnitVector(0, 1));
			to2 = p.GetSouthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, 1));
			
			resort = p.GetWest();
		}
		else if(grad.x <= 0 && grad.y >= 0 && -1 * grad.x >= grad.y)
		{
			//between W and S, with an emphasis on the W
			//So actually between W and SW
			to1 = p.GetWest();
			w1 = grad.Dot(Vec2.UnitVector(-1, 0));
			to2 = p.GetSouthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, 1));
			
			resort = p.GetSouth();
		}
		else if(grad.x <= 0 && grad.y <= 0 && -1 * grad.x >= -1 * grad.y)
		{
			//between W and N, with an emphasis on the W
			//So actually between W and NW
			to1 = p.GetWest();
			w1 = grad.Dot(Vec2.UnitVector(-1, 0));
			to2 = p.GetNorthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, -1));
			
			resort = p.GetNorth();
		}
		else if(grad.x <= 0 && grad.y <= 0 && -1 * grad.y >= -1 * grad.x)
		{
			//between W and N, with an emphasis on the N
			//So actually between N and NW
			to1 = p.GetNorth();
			w1 = grad.Dot(Vec2.UnitVector(0, -1));
			to2 = p.GetNorthWest();
			w2 = grad.Dot(Vec2.UnitVector(-1, -1));
			
			resort = p.GetWest();
		}
		else if(grad.x >= 0 && grad.y <= 0 && -1 * grad.y >= grad.x)
		{
			//between E and N, with an emphasis on the N
			//So actually between N and NE
			to1 = p.GetNorth();
			w1 = grad.Dot(Vec2.UnitVector(0, -1));
			to2 = p.GetNorthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, -1));
			
			resort = p.GetEast();
		}
		else if(grad.x >= 0 && grad.y <= 0 && grad.x >= -1 * grad.y)
		{
			//between E and N, with an emphasis on the E
			//So actually between E and NE
			to1 = p.GetEast();
			w1 = grad.Dot(Vec2.UnitVector(1, 0));
			to2 = p.GetNorthEast();
			w2 = grad.Dot(Vec2.UnitVector(1, -1));
			
			resort = p.GetNorth();
		}
		else
		{
			return false;
		}
		if(to1 == null && to2 == null)
		{
			return false;
		}
		else if(to2 == null)
		{
			if(to1.GetHeight() >= p.GetHeight())
				return false;
			to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
		}
		else if(to1 == null)
		{
			if(to2.GetHeight() >= p.GetHeight())
				return false;
			to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
		}
		else
		{
			if(to1.GetHeight() >= p.GetHeight() && to2.GetHeight() >= p.GetHeight())
			{
				if(resort == null)
					return false;
				if(resort.GetHeight() >= p.GetHeight())
					return false;
				resort.GetParent().ChangeRainflow(resort.x, resort.y, (int) currentRain);
			}
			else if(to2.GetHeight() >= p.GetHeight())
			{
				to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) currentRain);
			}
			else if(to1.GetHeight() >= p.GetHeight())
			{
				to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) currentRain);
			}
			else
			{
				double t1Rain = currentRain * w1 / (w1 + w2);
				double t2Rain = currentRain * w2 / (w1 + w2);
				to1.GetParent().ChangeRainflow(to1.x,to1.y, (int) (t1Rain + 0.5));
				to2.GetParent().ChangeRainflow(to2.x,to2.y, (int) (t2Rain + 0.5));
			}
		}
		return true;
	}
	public static void GuaranteeConsistentHydrology(ArrayList<LocalMap.Pixel> allPixels)
	{
		allPixels.sort((a, b) -> 
		{
			double d = a.GetHeight() - b.GetHeight();
			if(d < 0)
				return -1;
			if(d > 0)
				return 1;
			return 0;
		});
		for(LocalMap.Pixel p : allPixels)
		{
			//it's already been handled, presumably by a BFS step below
			if(p.GetStatusInDrainRecord() != DrainRecord.Status.Unknown)
				continue;
			if(p.IsOcean())
			{
				p.SetStatusInDrainRecord(DrainRecord.Status.DrainsToOcean);
				continue;
			}
			DrainRecord.Dir drainDir = DrainRecord.Dir.None;
			double drainHeight = p.GetHeight();
			boolean bfsUseful = false;
			for(DrainRecord.Dir dir : DrainRecord.Dir.values())
			{
				if(dir == DrainRecord.Dir.None)
					continue;
				int dI = dir.dx();
				int dJ = dir.dy();
				double height = p.GetParent().GetHeight(p.x + dI, p.y + dJ);
				if(height == p.GetHeight())
					bfsUseful = true;
				if(height >= drainHeight)
				{
					LocalMap.WatermapValue adjWat = p.GetParent().GetWaterPresence(p.x + dI, p.y + dJ);
					if(adjWat == LocalMap.WatermapValue.Lake)
						bfsUseful = true;
					continue;
				}
				DrainRecord.Status to = p.GetParent().GetDrainStatus(p.x + dI, p.y + dJ);
				if(to == DrainRecord.Status.DrainsToPit)
				{
					bfsUseful = true;
					continue;
				}
				if(to == DrainRecord.Status.Unknown)
				{
					System.out.println("NOOO WE CAN'T DRAIN TO AN UNKNOWN!");
					continue;
				}
				drainDir = dir;
				drainHeight = height;
			}
			p.SetDirectionInDrainRecord(drainDir);
			if(drainDir == DrainRecord.Dir.None)
			{
				p.SetStatusInDrainRecord(DrainRecord.Status.DrainsToPit);
				continue;
			}
			DrainRecord.Status to = p.GetParent().GetDrainStatus(p.x + drainDir.dx(), p.y + drainDir.dy());
			if(to == DrainRecord.Status.OffMap)
				p.SetStatusInDrainRecord(DrainRecord.Status.DrainsOffMap);
			else if(to == DrainRecord.Status.DrainsToOcean)
				p.SetStatusInDrainRecord(DrainRecord.Status.DrainsToOcean);
			else if(to == DrainRecord.Status.DrainsOffMap)
				p.SetStatusInDrainRecord(DrainRecord.Status.DrainsOffMap);
			else
				System.out.println("NOOO WE DON'T KNOW WHAT WE'RE DRAINING TO???");
			
			if(!bfsUseful)
				continue;
			BFSPixelHeightCorrection(p);
		}
	}
	public static void GuaranteeConsistentHydrology(ArrayList<LocalMap> targets, boolean cleanDrainRecord)
    {
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
    	ArrayList<LocalMap.Pixel> allPixels = BeginPixelOperation(used, targets, true, true, false, true, false);
		GuaranteeConsistentHydrology(allPixels);
		
		EndPixelOperation(used, true, true, false, cleanDrainRecord, false);
    }
	private static void BFSPixelHeightCorrection(LocalMap.Pixel seed)
	{
		ArrayList<LocalMap.Pixel> found = new ArrayList<LocalMap.Pixel>();
		LinkedList<LocalMap.Pixel> frontier = new LinkedList<LocalMap.Pixel>();
		frontier.addLast(seed);
		
		while(!frontier.isEmpty())
		{
			LocalMap.Pixel current = frontier.removeFirst();
			found.add(current);
			double currentHeight = current.GetHeight();
			DrainRecord.Status currentStatus = current.GetStatusInDrainRecord();
			LocalMap.WatermapValue currentWater = current.GetWaterType();
			for(DrainRecord.Dir dir : DrainRecord.Dir.values())
			{
				if(dir == DrainRecord.Dir.None)
					continue;
				LocalMap.Pixel adj = current.GetPixelInDir(dir);
				if(adj == null)
					continue;
				if(!adj.GetParent().ActivityFlagSet())
					continue;
				double adjHeight = adj.GetHeight();
				LocalMap.WatermapValue adjWater = adj.GetWaterType();
				if(adjWater == LocalMap.WatermapValue.Ocean)
					continue;
				if(adjHeight > currentHeight && adjWater == LocalMap.WatermapValue.NotWater)
					continue;
				DrainRecord.Status adjStat = adj.GetStatusInDrainRecord();
				if(adjStat != DrainRecord.Status.DrainsToPit && adjStat != DrainRecord.Status.Unknown)
					continue;
				
				DrainRecord.Dir opp = dir.GetOpposite();
				adj.SetDirectionInDrainRecord(opp);
				adj.SetStatusInDrainRecord(currentStatus);
				double targetHeight = currentHeight + Math.pow(2, -15);
				double adjCur = adj.GetParent().GetHeight(adj.x, adj.y);
				double delta = targetHeight - adjCur;
				adj.GetParent().ManualHeightChange(adj.x, adj.y, delta, false, true);
				adj.GetParent().SetWatermapValue(adj.x, adj.y, adj.GetWaterType(), true);
				adjHeight = adj.GetHeight();
				
				if(currentWater == LocalMap.WatermapValue.Lake)
				{
					adj.GetParent().SetWatermapValue(adj.x, adj.y, LocalMap.WatermapValue.Lake, true);
				}
				else if(currentWater == LocalMap.WatermapValue.Ocean)
				{
					adj.GetParent().SetWatermapValue(adj.x, adj.y, LocalMap.WatermapValue.Ocean, true);
				}
				
				frontier.addLast(adj);
			}
		}
		
		if(found.size() >= Switches.MIN_LOCAL_MAP_PIXELS_IN_LAKE && seed.GetHeight() > 0)
		{
			for(LocalMap.Pixel f : found)
			{
				if(f == seed)
					continue;
				
				f.GetParent().SetWatermapValue(f.x, f.y, LocalMap.WatermapValue.Lake, true);
			}
		}
	}
	public static void ThermalErosion(ArrayList<LocalMap.Pixel> allPixels)
	{
		ThermalErosion myAlgo = new ThermalErosion(allPixels);
		myAlgo.RunLoop();  
	}
	public static void ThermalErosion(ArrayList<LocalMap> targets, boolean cleanPixelStatus)
	{
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
    	ArrayList<LocalMap.Pixel> allPixels = BeginPixelOperation(used, targets, true, false, false, false, true);
		ThermalErosion(allPixels);
		
		EndPixelOperation(used, true, false, false, false, cleanPixelStatus);
	}
	private static class ThermalErosion
	{
		private ArrayList<LocalMap.Pixel> allPixels;
		private PixelRingQueue queue;
    	private DrainRecord.Dir[] directions;
		private int[] senders;
		private int[] receivers;
		private int[] critSend;
		private int[] critReceive;
		private int[] adjHeights;
		private int[] adjSediment;
		private boolean[] active;
		private boolean[] adjWater;
		private boolean[] adjDumped;
		private int[] ntq;
		private int pixelCount;
		private int centHeight;
		private int centSed;
		private boolean centWater;
		
		
		private int senderHeight;
		private int receiverHeight;
		private int senderMaxDelta;
		private int receiverMaxDelta;
		private int centSafeSend;
		private int centSafeReceive;
		private int numSenders;
		private int numReceivers;
		private int numCritSenders;
		private int numCritReceivers;
		
		//This 11/32 might seem weird, but here's how it came about:
		//1. We want a 30deg slope, which occurs when change in height is half the change in distance
		//But we want diagonals to be constrained by this slope, not carinal directions, which means
		//that the cardinal directions should be constrained by sqrt(2)/2 * 0.5
		//This gives a constant of roughly 0.35, but either using 0.35 directly or using the constant
		//calculated to precision gives floating point precision errors.
		//To maintain floating point precision, we want our max delta to be a power of 2
		//11/32 gets us really close and is good enough!
		private int maxSedDelta = DataImage32Decimal.PACK((11./32) * (LocalMap.METER_DIM / DataImage.trueDim));
		
		public ThermalErosion(ArrayList<LocalMap.Pixel> allPixels)
		{
			allPixels.sort((a, b) -> 
			{
				double d = a.GetHeight() - b.GetHeight();
				if(d < 0)
					return -1;
				if(d > 0)
					return 1;
				return 0;
			});
			this.allPixels = allPixels;
			queue = new PixelRingQueue(allPixels);
			directions = new DrainRecord.Dir[] {
	    			DrainRecord.Dir.N,
	    			DrainRecord.Dir.S,
	    			DrainRecord.Dir.E,
	    			DrainRecord.Dir.W
	    	};
			senders = new int[directions.length];
			receivers = new int[directions.length];
			critSend = new int[directions.length];
			critReceive = new int[directions.length];
			adjHeights = new int[directions.length];
			adjSediment = new int[directions.length];
			active = new boolean[directions.length];
			adjWater = new boolean[directions.length];
			adjDumped = new boolean[directions.length];
			ntq = new int[directions.length];
			pixelCount = 0;
		}
		public void RunLoop()
		{
			while(!queue.IsEmpty())
			{
				pixelCount++;
	    		LocalMap.Pixel p = queue.Dequeue();
	    		centHeight = DataImage32Decimal.PACK(p.GetHeight());
	    		centSed = DataImage32Decimal.PACK(p.GetSedimentDepth());
	    		centWater = p.GetWaterType() != LocalMap.WatermapValue.NotWater;
	    		for(int dirI = 0; dirI < directions.length; dirI++)
	    		{
	    			LocalMap.Pixel adj = p.GetPixelInDir(directions[dirI]);
	    			if(adj == null)
	    			{
	    				active[dirI] = false;
	    				continue;
	    			}
	    			adjSediment[dirI] = DataImage32Decimal.PACK(adj.GetSedimentDepth());
	    			adjHeights[dirI] = DataImage32Decimal.PACK(adj.GetHeight());
	    			active[dirI] = adj.IsActive();
	    			adjWater[dirI] = adj.GetWaterType() != LocalMap.WatermapValue.NotWater;
	    			adjDumped[dirI] = false;
	    		}
	    		MakePixelDecisions(p);
	    		AdjustPixelData(p);
	    		QueueNextPixels(p);
			}
		}
		private void MakePixelDecisions(LocalMap.Pixel p)
		{
			int count = 0;
			while(true)
			{

    			count++;
    			if(count > 100)
    			{
    				System.out.println("Oh no, we can't have this many iterations " + count + " on pixel " + pixelCount);
    			}
    			senderHeight = 0;
        		receiverHeight = Integer.MAX_VALUE;
        		senderMaxDelta = Integer.MAX_VALUE;
        		receiverMaxDelta = Integer.MAX_VALUE;
        		centSafeSend = centSed;
        		centSafeReceive = Integer.MAX_VALUE;
        		numSenders = 0;
        		numReceivers = 0;
        		numCritSenders = 0;
        		numCritReceivers = 0;
        		
        		for(int dirI = 0; dirI < directions.length; dirI++)
        		{
        			ClassifyAdjacentPixel(dirI);
        		}
        		
        		if(numSenders == 0 && numReceivers == 0)
        			break;
        		else if(numReceivers == 0)
        		{
        			boolean earlyComplete = SendToCenter();
        			if(earlyComplete)
        				break;
        		}
        		else if(numSenders == 0)
        		{
        			boolean earlyComplete = ReceiveFromCenter();
        			if(earlyComplete)
        				break;
        		}
        		else
        		{
        			boolean earlyComplete = TransferAcrossCenter();
        			if(earlyComplete)
        				break;
        		}
    		
			}
		}
		private boolean SendToCenter()
		{

			boolean anyWaterReceivers = centWater;
			for(int i = 0; i < numCritReceivers; i++)
				if(adjWater[critReceive[i]])
				{
					anyWaterReceivers = true;
					adjDumped[critReceive[i]] = true;
				}
			if(anyWaterReceivers)
			{
				if(senderMaxDelta == 0)
					System.out.println("Oh no, we can't send 0 sediment to the center; there's water!");
				for(int i = 0; i < numSenders; i++)
    			{
    				adjHeights[senders[i]] -= senderMaxDelta;
    				adjSediment[senders[i]] -= senderMaxDelta;
    			}
				return false;
			}

			int sedimentToSend = numSenders * senderMaxDelta;
			int sedimentToReceive = centSafeReceive * (numCritReceivers + 1);
			int sendDelta = 0;
			int receiveDelta = 0;
			if(sedimentToReceive < sedimentToSend)
			{
				sendDelta = sedimentToReceive / numSenders;
				receiveDelta = centSafeReceive;
			}
			else if(sedimentToSend < sedimentToReceive)
			{
				sendDelta = senderMaxDelta;
				receiveDelta = sedimentToSend / (numCritReceivers + 1);
			}
			else
			{
				sendDelta = senderMaxDelta;
				receiveDelta = centSafeReceive;
			}
			if(sendDelta == 0 || receiveDelta == 0)
				return true;
			
			if(centHeight + receiveDelta + maxSedDelta > adjHeights[senders[0]] - sendDelta)
			{
				double perc = 1.0 * (adjHeights[senders[0]] - centHeight - maxSedDelta) / (receiveDelta + sendDelta);
				int sendTarget = (int) (adjHeights[senders[0]] - perc * sendDelta);
				int centTarget = sendTarget - maxSedDelta;
				
				receiveDelta = centTarget - centHeight;
				sendDelta = adjHeights[senders[0]] - sendTarget;
			}
			
			if(sendDelta == 0 || receiveDelta == 0)
				return true;
			
			for(int i = 0; i < numSenders; i++)
			{
				adjHeights[senders[i]] -= sendDelta;
				adjSediment[senders[i]] -= sendDelta;
			}
			centHeight += receiveDelta;
			centSed += receiveDelta;
			for(int i = 0; i < numCritReceivers; i++)
			{
				adjHeights[critReceive[i]] += receiveDelta;
				adjSediment[critReceive[i]] += receiveDelta;
			}
			
			return false;
		}
		private boolean ReceiveFromCenter()
		{

			boolean anyWaterReceivers = false;
			for(int i = 0; i < numReceivers; i++)
				if(adjWater[receivers[i]])
				{
					anyWaterReceivers = true;
					adjDumped[receivers[i]] = true;
				}
			if(centWater)
				return true;
			
			if(anyWaterReceivers)
			{
				if(centSafeSend == 0)
					return true;
				centHeight -= centSafeSend;
    			centSed -= centSafeSend;
    			for(int i = 0; i < numCritSenders; i++)
    			{
    				adjHeights[critSend[i]] -= centSafeSend;
    				adjSediment[critSend[i]] -= centSafeSend;
    			}
    			return false;
			}
			

			if(centSafeSend == 0)
				return true;
			
			int sedimentToSend = centSafeSend * (numCritSenders + 1);
			int sedimentToReceive = numReceivers * receiverMaxDelta;
			int sendDelta = 0;
			int receiveDelta = 0;
			
			if(sedimentToReceive < sedimentToSend)
			{
				sendDelta = sedimentToReceive / (numCritSenders + 1);
				receiveDelta = receiverMaxDelta;
			}
			else if(sedimentToSend < sedimentToReceive)
			{
				sendDelta = centSafeSend;
				receiveDelta = sedimentToSend / numReceivers;
			}
			else
			{
				sendDelta = centSafeSend;
				receiveDelta = receiverMaxDelta;
			}
			
			if(sendDelta == 0 || receiveDelta == 0)
				return true;
			
			if(centHeight - sendDelta - maxSedDelta < adjHeights[receivers[0]] + receiveDelta)
			{
				double perc = 1.0 * (centHeight - adjHeights[receivers[0]] - maxSedDelta) / (receiveDelta + sendDelta);
				int receiveTarget = (int) (adjHeights[receivers[0]] + perc * receiveDelta);
				int centTarget = receiveTarget + maxSedDelta;
				
				sendDelta = centHeight - centTarget;
				receiveDelta = receiveTarget - adjHeights[receivers[0]];
			}
			
			if(sendDelta == 0 || receiveDelta == 0)
				return true;
			
			centHeight -= sendDelta;
			centSed -= sendDelta;
			for(int i = 0; i < numCritSenders; i++)
			{
				adjHeights[critSend[i]] -= sendDelta;
				adjSediment[critSend[i]] -= sendDelta;
			}
			for(int i = 0; i < numReceivers; i++)
			{
				adjHeights[receivers[i]] += receiveDelta;
				adjSediment[receivers[i]] += receiveDelta;
			}
		
			return false;
		}
		private boolean TransferAcrossCenter()
		{

			boolean anyWaterReceivers = centWater;
			for(int i = 0; i < numReceivers; i++)
				if(adjWater[receivers[i]])
				{
					anyWaterReceivers = true;
					adjDumped[receivers[i]] = true;
				}
			if(anyWaterReceivers)
			{
				if(senderMaxDelta == 0)
					System.out.println("Oh no, we can't send 0 sediment across the center; there's water!");
				for(int i = 0; i < numSenders; i++)
    			{
    				adjHeights[senders[i]] -= senderMaxDelta;
    				adjSediment[senders[i]] -= senderMaxDelta;
    			}
				return false;
			}
			

			int sedimentToSend = numSenders * senderMaxDelta;
			int sedimentToReceive = numReceivers * receiverMaxDelta;
			int sendDelta = 0;
			int receiveDelta = 0;
			
			if(sedimentToReceive < sedimentToSend)
			{
				sendDelta = sedimentToReceive / numSenders;
				if(sedimentToReceive % numSenders != 0)
					sendDelta++;
				//round up the amount we send
				receiveDelta = receiverMaxDelta;
			}
			else if(sedimentToSend < sedimentToReceive)
			{
				sendDelta = senderMaxDelta;
				receiveDelta = sedimentToSend / numReceivers;
				//round down the amount we receive
			}
			else
			{
				sendDelta = senderMaxDelta;
				receiveDelta = receiverMaxDelta;
			}
			
			if(sendDelta == 0 || receiveDelta == 0)
				return true;
			
			for(int i = 0; i < numSenders; i++)
			{
				adjHeights[senders[i]] -= sendDelta;
				adjSediment[senders[i]] -= sendDelta;
			}
			for(int i = 0; i < numReceivers; i++)
			{
				adjHeights[receivers[i]] += receiveDelta;
				adjSediment[receivers[i]] += receiveDelta;
			}
			return false;
		}
		private void ClassifyAdjacentPixel(int dirI)
		{

			if(!active[dirI])
				return;
			if(adjHeights[dirI] > centHeight + maxSedDelta)
			{
				centSafeReceive = Math.min(centSafeReceive,
						adjHeights[dirI] - centHeight - maxSedDelta);
				if(adjSediment[dirI] == 0)
					return;
				if(adjWater[dirI])
					return;
					
				//I am a super-critical sender
				if(adjHeights[dirI] > senderHeight)
				{
					//I am *the* super-critical sender; kill all other pretenders
					senderMaxDelta = Math.min(
							adjHeights[dirI] - (centHeight + maxSedDelta),
							adjHeights[dirI] - senderHeight);
					senderHeight = adjHeights[dirI];
					numSenders = 1;
					senders[0] = dirI;
					senderMaxDelta = Math.min(
							senderMaxDelta, 
							adjSediment[dirI]);
				}
				else if(adjHeights[dirI] < senderHeight)
				{
					//There's another super-critical sender, but it can't send to lower than me
					senderMaxDelta = Math.min(
							senderMaxDelta, 
							senderHeight - adjHeights[dirI]);
				}
				else
				{
					//I am another super-critical sender, add me to the list
					senders[numSenders] = dirI;
					numSenders++;
					senderMaxDelta = Math.min(
							senderMaxDelta, 
							adjSediment[dirI]);
				}
			}
			else if(adjHeights[dirI] < centHeight - maxSedDelta)
			{
				centSafeSend = Math.min(centSafeSend,
						centHeight - adjHeights[dirI] - maxSedDelta);

				//I am a super-critical receiver
				if(adjHeights[dirI] < receiverHeight)
				{
					//I am *the* super-critical receiver; kill all other pretenders
					receiverMaxDelta = Math.min(
							(centHeight - maxSedDelta) - adjHeights[dirI], 
							receiverHeight - adjHeights[dirI]);
					receiverHeight = adjHeights[dirI];
					numReceivers = 1;
					receivers[0] = dirI;
				}
				else if(adjHeights[dirI] > receiverHeight)
				{
					//There's another super-critical receiver, but it can't receiver to higher than me
					receiverMaxDelta = Math.min(
							receiverMaxDelta, 
							adjHeights[dirI] - receiverHeight);
				}
				else
				{
					receivers[numReceivers] = dirI;
					numReceivers++;
				}
			}
			else if(adjHeights[dirI] == centHeight + maxSedDelta)
			{
				//I am a critical sender; if you send from cent, you should send from me
				centSafeReceive = Math.min(centSafeReceive, 2 * maxSedDelta);
				//wait, I don't have sediment to send - ignore me
				if(adjSediment[dirI] == 0)
					return;
				if(adjWater[dirI])
					return;
				critSend[numCritSenders] = dirI;
				numCritSenders++;
				centSafeSend = Math.min(centSafeSend, adjSediment[dirI]);
			}
			else if(adjHeights[dirI] == centHeight - maxSedDelta)
			{
				//I am a critical receiver; if you send from cent, you should send to me
				critReceive[numCritReceivers] = dirI;
				numCritReceivers++;
				centSafeSend = Math.min(centSafeSend, 2 * maxSedDelta);
			}
			else
			{
				//I am currently at a stable slope w.r.t center
				centSafeReceive = Math.min(centSafeReceive, 
						adjHeights[dirI] - centHeight + maxSedDelta);
				centSafeSend = Math.min(centSafeSend,
						centHeight - adjHeights[dirI] + maxSedDelta);
			}
		
		}
		private void AdjustPixelData(LocalMap.Pixel p)
		{
			double upCentHeight = DataImage32Decimal.UNPACK(centHeight);
    		//boolean centerChanged = upCentHeight - p.GetHeight() != 0;
    		double centerDelta = upCentHeight - p.GetHeight();
    		if(p.GetWaterType() == LocalMap.WatermapValue.NotWater)
    			p.GetParent().ManualHeightChange(p.x, p.y, centerDelta, true, true);
    		for(int dirI = 0; dirI < directions.length; dirI++)
    		{
    			ntq[dirI] = -1;
    			//if(centerChanged)
    			//	ntq[dirI] = dirI;
    			LocalMap.Pixel adj = p.GetPixelInDir(directions[dirI]);
    			if(adj == null)
    				continue;
    			if(!active[dirI])
    				continue;
    			//if(adjDumped[dirI])
    			//	ntq[dirI] = dirI;
    			//double upSed = DataImage32Decimal.UNPACK(adjSediment[dirI]);
    			//if(adj.GetSedimentDepth() != upSed)
    			//	ntq[dirI] = dirI;
    			double upHeight = DataImage32Decimal.UNPACK(adjHeights[dirI]);
    			//if(adj.GetHeight() != upHeight)
    			//	ntq[dirI] = dirI;
    			
    			
    			double delta = upHeight - adj.GetHeight();
    			if(Math.abs(delta) > 0.1)
    				ntq[dirI] = dirI;
    			
    			if(adj.GetSedimentDepth() + delta < 0)
    			{
    				System.out.println("Negative Sediment is worth looking into!");
    			}

    			if(adj.GetWaterType() == LocalMap.WatermapValue.NotWater)
    				adj.GetParent().ManualHeightChange(adj.x, adj.y, delta, true, true);
    		}
		}
		private void QueueNextPixels(LocalMap.Pixel p)
		{
    		if(ntq[0] == -1 || (ntq[1] != -1 && adjHeights[ntq[1]] > adjHeights[ntq[0]]))
    		{
    			int temp = ntq[0];
    			ntq[0] = ntq[1];
    			ntq[1] = temp;
    		}
    		if(ntq[2] == -1 || (ntq[3] != -1 && adjHeights[ntq[3]] > adjHeights[ntq[2]]))
    		{
    			int temp = ntq[2];
    			ntq[2] = ntq[3];
    			ntq[3] = temp;
    		}
    		if(ntq[0] == -1 || (ntq[2] != -1 && adjHeights[ntq[2]] > adjHeights[ntq[0]]))
    		{
    			int temp = ntq[0];
    			ntq[0] = ntq[2];
    			ntq[2] = temp;
    		}
    		if(ntq[1] == -1 || (ntq[3] != -1 && adjHeights[ntq[3]] > adjHeights[ntq[1]]))
    		{
    			int temp = ntq[1];
    			ntq[1] = ntq[3];
    			ntq[3] = temp;
    		}
    		if(ntq[1] == -1 || (ntq[2] != -1 && adjHeights[ntq[2]] > adjHeights[ntq[1]]))
    		{
    			int temp = ntq[1];
    			ntq[1] = ntq[2];
    			ntq[2] = temp;
    		}
    		for(int i = 0; i < ntq.length; i++)
    		{
    			if(ntq[i] == -1)
    				continue;
    			DrainRecord.Dir dir = directions[ntq[i]];
    			LocalMap.Pixel queueNow = p.GetPixelInDir(dir);
    			if(!queueNow.IsQueued())
    				queue.Enqueue(queueNow);
    		}
		}
	}
	private static class PixelRingQueue
	{
		private LocalMap.Pixel[] queue;
		private int ringStart;
		private int ringEnd;
		private int numPixels;
		private int currentSize;
		private int remainingInPass;
		private int pixelsInPassBatch;
		private int numPassesInBatch;
		private int totalNumPasses;
		public PixelRingQueue(ArrayList<LocalMap.Pixel> allPixels)
		{
			numPixels = allPixels.size();
	    	queue = new LocalMap.Pixel[numPixels];
	    	ringStart = 0;
	    	ringEnd = 0;
	    	currentSize = numPixels;
	    	for(int i = 0; i < numPixels; i++)
	    	{
	    		queue[i] = allPixels.get(i);
	    		allPixels.get(i).SetPixelQueued(true);
	    	}
	    	remainingInPass = currentSize;
	    	numPassesInBatch = 0;
	    	pixelsInPassBatch = 0;
	    	totalNumPasses++;
		}
		public boolean IsEmpty()
		{
			if(currentSize == 0)
				System.out.println("We're empty after " + totalNumPasses + " passes.");
			return currentSize == 0;
		}
		public LocalMap.Pixel Dequeue()
		{
			if(currentSize == 0)
				return null;
			LocalMap.Pixel p = queue[ringStart];
    		ringStart++;
    		if(ringStart >= numPixels)
    		{
    			ringStart -= numPixels;
    		}
    		p.SetPixelQueued(false);
    		currentSize--;
    		remainingInPass--;
    		if(remainingInPass == 0)
    		{
    			remainingInPass = currentSize;
    			pixelsInPassBatch += remainingInPass;
    			numPassesInBatch++;
    			totalNumPasses++;
    			if(pixelsInPassBatch > 500000)
    			{
        			System.out.println("Finished " + numPassesInBatch + "x passes; next pass: " + currentSize);
        			pixelsInPassBatch = 0;
        			numPassesInBatch = 0;
    			}
    		}
    		return p;
		}
		public boolean Enqueue(LocalMap.Pixel p)
		{
			if(currentSize == numPixels)
				return false;
			if(p.IsQueued())
				return false;
			queue[ringEnd] = p;
			ringEnd++;
			if(ringEnd >= numPixels)
				ringEnd -= numPixels;
			p.SetPixelQueued(true);
			currentSize++;
			return true;
		}
	}
}