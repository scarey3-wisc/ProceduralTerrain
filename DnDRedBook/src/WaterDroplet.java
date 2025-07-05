import java.util.HashMap;

public class WaterDroplet
{
	//Droplets have LocalMap coordinates; ie [0, 1] for the local map that they're in.
	private Vec2 pos;
	private Vec2 dir;
	private double vel;
	private double water;
	private double sediment;
	private LocalMap parent;
	private int stepsTaken;
	private double pixelWidth;
	private double pixelMeters;
	private int dimension;
	private boolean lockAllowed;
	private HashMap<LocalMap, Boolean> activeLocalMaps;
	
	public WaterDroplet(LocalMap parent, double initWater, HashMap<LocalMap, Boolean> activeList, boolean autoLock)
	{
		lockAllowed = autoLock;
		vel = 0;
		dir = new Vec2(0, 0);
		pos = new Vec2(Math.random(), Math.random());
		this.parent = parent;
		this.sediment = 0;
		stepsTaken = 0;
		this.water = initWater;
		dimension = DataImage.trueDim;
		pixelWidth = 1.0 / dimension;
		pixelMeters = LocalMap.METER_DIM * pixelWidth;
		activeLocalMaps = activeList;
		LockOnLocalMapAndSurroundings(parent);
	}
	private void LockOnLocalMapAndSurroundings(LocalMap target)
	{
		if(!lockAllowed && !activeLocalMaps.containsKey(target))
			System.out.println("We need to lock onto this map, BUT WE CAN'T????");
		LockOnLocalMap(target);
		
		LocalMap north = target.GetNorth();
		if(north != null)
			LockOnLocalMap(north);
		
		LocalMap south = target.GetSouth();
		if(south != null)
			LockOnLocalMap(south);
		
		LocalMap east = target.GetEast();
		if(east != null)
			LockOnLocalMap(east);
		
		LocalMap west = target.GetWest();
		if(west != null)
			LockOnLocalMap(west);
		
		LocalMap ne = null;
		if(north != null)
			ne = north.GetEast();
		if(ne == null && east != null)
			ne = east.GetNorth();
		if(ne != null)
			LockOnLocalMap(ne);
		
		LocalMap nw = null;
		if(north != null)
			nw = north.GetWest();
		if(nw == null && west != null)
			nw = west.GetNorth();
		if(nw != null)
			LockOnLocalMap(nw);
		
		LocalMap se = null;
		if(south != null)
			se = south.GetEast();
		if(se == null && east != null)
			se = east.GetSouth();
		if(se != null)
			LockOnLocalMap(se);
		
		LocalMap sw = null;
		if(south != null)
			sw = south.GetWest();
		if(sw == null && west != null)
			sw = west.GetSouth();
		if(sw != null)
			LockOnLocalMap(sw);
			
	}
	private void LockOnLocalMap(LocalMap target)
	{
		if(!lockAllowed)
			return;
		if(activeLocalMaps.containsKey(target))
			return;
		boolean alreadyEditing = target.PrepareForEditing(true, false, false);
		activeLocalMaps.put(target, alreadyEditing);
	}
	public boolean OneErosionStep()
	{
		Vec2 newPos = CalcNewPos();
		LocalMap newParent = CalcParent(newPos, parent);
		if(newParent == null)
			return false;
		LockOnLocalMapAndSurroundings(newParent);
		
		double hOld = parent.GetHeight(pos.x, pos.y);
		double hNew = newParent.GetHeight(newPos.x, newPos.y);
		double delH = hNew - hOld;
		if(delH > 0)
		{
			//We've come into a pit!
			double sedimentNeeded = SedimentNeededToRaiseHeight(pos.x, pos.y, delH);
			if(sedimentNeeded > sediment)
			{
				DepositSediment(pos.x, pos.y, sediment);
				sediment = 0;
				return false; //no need to continue
			}
			else
			{
				DepositSediment(pos.x, pos.y, sedimentNeeded);
				sediment -= sedimentNeeded;
			}
		}
		else if(delH == 0)
		{
			return false;
		}
		else
		{
			double capacity = Math.max(-delH / (pixelMeters), MIN_SLOPE_EROSION) * vel * water * CAPACITY;
			if(sediment > capacity)
			{
				double surplus = sediment - capacity;
				double deposit = surplus * DEPOSITION;
				//I am skeptical of the paper's claim that this case should also use the "one cell" deposition
				sediment -= deposit;
				DepositSediment(pos.x, pos.y, deposit);
			}
			else
			{
				double deficit = capacity - sediment;
				double erodeTarget = Math.min(-delH * 10, deficit * EROSION);
				double actuallyEroded = ErodeSediment(pos.x, pos.y, erodeTarget, RADIUS);
				sediment += actuallyEroded;
			}
			vel = Math.sqrt(vel * vel - delH * GRAVITY / (pixelMeters));
		}
		pos = newPos;
		parent = newParent;
		
		/*if(InWater(pos.x, pos.y))
		{
			DepositSediment(pos.x, pos.y, sediment);
			return false;
		}*/
		
		water = water * (1 - EVAPORATION);
		stepsTaken++;
		if(stepsTaken >= MAX_PATH)
		{
			DepositSediment(pos.x, pos.y, sediment);
			return false;
		}
		return true;
	}
	private LocalMap CalcParent(Vec2 newPos, LocalMap curr)
	{
		if(curr == null)
			return null;
		while(newPos.x > 1)
		{
			curr = curr.GetEast();
			if(curr == null)
				return null;
			newPos.x = newPos.x - 1;
		}
		while(newPos.x < 0)
		{
			curr = curr.GetWest();
			if(curr == null)
				return null;
			newPos.x = newPos.x + 1;
		}
		while(newPos.y > 1)
		{
			curr = curr.GetSouth();
			if(curr == null)
				return null;
			newPos.y = newPos.y - 1;
		}
		while(newPos.y < 0)
		{
			curr = curr.GetNorth();
			if(curr == null)
				return null;
			newPos.y = newPos.y + 1;
		}
		
		return curr;
	}
	private Vec2 CalcNewPos()
	{
		dir = CalcNewDir();
		dir.Multiply(pixelWidth);
		Vec2 newPos = pos.Clone();
		newPos.Add(dir);
		return newPos;
	}
	private Vec2 CalcNewDir()
	{
		Vec2 oldDir = dir.Clone();
		oldDir.Multiply(INERTIA);
		Vec2 grad = parent.GetHeightGradient(pos.x, pos.y);
		grad.Multiply(-1);
		double gradContr = (1 - INERTIA);
		grad.Multiply(gradContr);
		Vec2 result = Vec2.Sum(oldDir, grad);
		result.Normalize();
		return result;
	}
	private boolean ChangeHeight(int px, int py, double amount)
	{
		LocalMap hmap = parent;
		while(px < 0)
		{
			hmap = hmap.GetWest();
			if(hmap == null)
				return false;
			px += dimension;
		}
		while(px > dimension)
		{
			hmap = hmap.GetEast();
			if(hmap == null)
				return false;
			px -= dimension;
		}
		while(py < 0)
		{
			hmap = hmap.GetNorth();
			if(hmap == null)
				return false;
			py += dimension;
		}
		while(py > dimension)
		{
			hmap = hmap.GetSouth();
			if(hmap == null)
				return false;
			py -= dimension;
		}
		hmap.ManualHeightChange(px, py, amount, true, true);
		return true;
	}
	private double ErodeSediment(double lX, double lY, double amount, double cellRad)
	{
		double actuallyEroded = 0;
		lX *= dimension;
		lY *= dimension;
		int pxS = (int) (lX - cellRad);
		int pxE = (int) (lX + cellRad);
		int pyS = (int) (lY - cellRad);
		int pyE = (int) (lY + cellRad);
		
		int brushWidth = pxE - pxS + 1;
		int brushHeight = pyE - pyS + 1;
		double[][] weights = new double[brushWidth][brushHeight];
		double totalWeight = 0;
		for(int i = pxS; i <= pxE; i++)
		{
			for(int j = pyS; j <= pyE; j++)
			{
				double dist = Math.sqrt((lX - i) * (lX - i) + (lY - j) * (lY - j));
				if(dist > cellRad)
					weights[i - pxS][j - pyS] = 0;
				else
				{
					
					weights[i - pxS][j - pyS] = 1.0 * cellRad - dist;
					totalWeight += 1.0 * cellRad - dist;
				}
			}
		}
		for(int i = pxS; i <= pxE; i++)
		{
			for(int j = pyS; j <= pyE; j++)
			{
				
				double weight = weights[i - pxS][j - pyS];
				double normWeight = weight / totalWeight;
				
				//double sedDep = parent.GetSedimentDepth(i, j);
				double weightMultiplier = 1;
				//if(sedDep == 0)
				//	weightMultiplier = 0.5;
				
				double erode = amount * normWeight * weightMultiplier;
				actuallyEroded += erode;
				ChangeHeight(i, j, -1 * erode);
			}
		}
		return actuallyEroded;
	}
	private double SedimentNeededToRaiseHeight(double lX, double lY, double targetDel)
	{
		lX *= dimension;
		lY *= dimension;
		int px0 = (int) lX;
		int py0 = (int) lY;
		
		double tX = lX - px0;
		double tY = lY - py0;
		
		double w00 = (1 - tX) * (1 - tY);
		double w01 = (1 - tX) * tY;
		double w10 = tX * (1 - tY);
		double w11 = tX * tY;
		
		double squareSum = w00 * w00 + w01 * w01 + w10 * w10 + w11 * w11;
		return targetDel * squareSum;
	}
	private void DepositSediment(double lX, double lY, double amount)
	{
		lX *= dimension;
		lY *= dimension;
		int px0 = (int) lX;
		int px1 = px0 + 1;
		int py0 = (int) lY;
		int py1 = py0 + 1;
		
		double tX = lX - px0;
		double tY = lY - py0;
		
		double w00 = (1 - tX) * (1 - tY);
		double w01 = (1 - tX) * tY;
		double w10 = tX * (1 - tY);
		double w11 = tX * tY;
		
		ChangeHeight(px0, py0, w00 * amount);
		ChangeHeight(px0, py1, w01 * amount);
		ChangeHeight(px1, py0, w10 * amount);
		ChangeHeight(px1, py1, w11 * amount);
	}
	
	private static final double INERTIA = 0.3;
	private static final double CAPACITY = 8;
	private static final double DEPOSITION = 0.2;
	private static final double EROSION = 0.7;
	private static final double EVAPORATION = 0.02;
	private static final double MIN_SLOPE_EROSION = 0.01;
	private static final double GRAVITY = 10;
	private static final double RADIUS = 4;
	private static final int MAX_PATH = 256;
}