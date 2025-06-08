import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

public class LocalTerrainAlgorithms
{
	private static ArrayList<LocalMap.Pixel> BeginPixelOperation(
			HashMap<LocalMap, Boolean> used, 
			ArrayList<LocalMap> targets, 
			boolean lowToHigh,
			boolean heights,
			boolean waterValues,
			boolean rainflowValues)
	{
    	ArrayList<LocalMap.Pixel> pixelsToProcess = new ArrayList<LocalMap.Pixel>();
		int dim = DataImage.trueDim;
    	for(LocalMap lm : targets)
		{
    		boolean alreadyEditing = lm.PrepareForEditing(heights, waterValues, rainflowValues);
    		used.put(lm, alreadyEditing);
			lm.InitializeDrainRecord();
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
					pixelsToProcess.add(lm.new Pixel(i, j));
				}
			}
		}
		pixelsToProcess.sort((a, b) -> 
		{
			double d = lowToHigh ? (a.GetHeight() - b.GetHeight()) : (b.GetHeight() - a.GetHeight());
			if(d < 0)
				return -1;
			if(d > 0)
				return 1;
			return 0;
		});
		
		return pixelsToProcess;
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
				adj.GetParent().ManualHeightSet(adj.x, adj.y, currentHeight + Math.pow(2, -15));
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
	public static void SendRainDownhill(ArrayList<LocalMap> targets, boolean cleanDrainRecord)
	{
		GuaranteeConsistentHydrology(targets, false);
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
		ArrayList<LocalMap.Pixel> allPixels = BeginPixelOperation(used, targets, false, false, false, true);
		for(LocalMap.Pixel p : allPixels)
		{
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
					System.out.println("WE WERE SUPPOSED TO GUARANTEE DOWNHILL");
					continue;
				}
				LocalMap.Pixel towards = p.GetPixelInDir(down);
				if(towards == null)
					continue;
				towards.GetParent().ChangeRainflow(towards.x, towards.y, p.GetCurrentRainflow());
			}
		}
		
		for(Entry<LocalMap, Boolean> map : used.entrySet())
		{
			if(cleanDrainRecord)
				map.getKey().DestroyDrainRecord();
			map.getKey().ResetActivityFlag();
			map.getKey().CompleteEditing(false, false, true, !map.getValue());
		}
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
	public static void GuaranteeConsistentHydrology(ArrayList<LocalMap> targets, boolean cleanDrainRecord)
    {
		HashMap<LocalMap, Boolean> used = new HashMap<LocalMap, Boolean>();
    	ArrayList<LocalMap.Pixel> allPixels = BeginPixelOperation(used, targets, true, true, true, false);
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
		
		for(Entry<LocalMap, Boolean> map : used.entrySet())
		{
			if(cleanDrainRecord)
				map.getKey().DestroyDrainRecord();
			map.getKey().ResetActivityFlag();
			map.getKey().CompleteEditing(true, true, false, !map.getValue());
		}
    }
}