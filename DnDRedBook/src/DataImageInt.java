import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ListIterator;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DataImageInt extends DataImage<int[], DataImageInt>
{
	private DataProvider source;
	private int[] deltaStorage;
	public DataImageInt(
			String parentDir, 
			String dirPrefix, 
			String folderName, 
			String fullName,
			DataProvider source, 
			DataImage.DataImageManagerBase<int[], DataImageInt> mgr) 
	{
		super(parentDir, dirPrefix, folderName, fullName, source, mgr);
		this.source = source;
	}
	
	public Vec2 GetGradient(double x, double y)
	{
		double del = GetPixelSize();
		double valuePositiveX = Get(x + del, y);
		double valueNegativeX = Get(x - del, y);
		double valuePositiveY = Get(x, y + del);
		double valueNegativeY = Get(x, y - del);
		double dx = (valuePositiveX - valueNegativeX) / (2 * del);
		double dy = (valuePositiveY - valueNegativeY) / (2 * del);
		return new Vec2(dx, dy);
	}
	public double GetLaplacian(double x, double y)
	{
		double del = GetPixelSize();
		double valueAtLoc = Get(x, y);
		double valuePositiveX = Get(x + del, y);
		double valueNegativeX = Get(x - del, y);
		double valuePositiveY = Get(x, y + del);
		double valueNegativeY = Get(x, y - del);
		
		double result = -4 * valueAtLoc
				+ 1 * valuePositiveX
				+ 1 * valuePositiveY
				+ 1 * valueNegativeX
				+ 1 * valueNegativeY;
		
		result /= (del * del);
		return result;
	}
	public void InitializePhasedDelta()
	{
		ForceEditReady(false);
		deltaStorage = new int[(trueDim * 1) * (trueDim + 1)];
	}
	public boolean PrepareDelta(int px, int py, int delta)
	{
		if(px < 0)
			return false;
		if(py < 0)
			return false;
		if(px > trueDim)
			return false;
		if(py > trueDim)
			return false;
		if(deltaStorage == null)
			return false;
		
		SetVal(deltaStorage, trueDim + 1, px, py, delta);
		
		return true;
	}
	public boolean FlushDelta()
	{
		if(deltaStorage == null)
			return false;
		
		//This is massively inefficient right now
		//TODO - make it a real algorithm!
		
		for(int i = 0; i <= trueDim; i++)
		{
			for(int j = 0; j <= trueDim; j++)
			{
				int result = GetVal(deltaStorage, trueDim + 1, i, j);
				if(!ManualPixelChange(i, j, result))
					return false;
			}
		}
		
		deltaStorage = null;
		return true;
	}
	private boolean ManualPixelChangeHelper(int px, int py, int delta)
	{
		if(px < 0)
			return false;
		if(py < 0)
			return false;
		if(px > trueDim)
			return false;
		if(py > trueDim)
			return false;
		ForceEditReady(false);
		AnnounceChangesRelativeToFile();
		int[] real = data.getLast();
		int currentVal = GetVal(real, trueDim + 1, px, py);
		int newVal = currentVal + delta;
		ListIterator<int[]> it = data.listIterator(data.size());
		int index = dimRange.length;
		while(it.hasPrevious())
		{
			index--;
			int[] target = it.previous();
			SetVal(target, dimRange[index] + 1, px, py, newVal);
			if(px % 2 != 0)
				break;
			if(py % 2 != 0)
				break;
			px /= 2;
			py /= 2;
		}
		
		return true;
	}
	public boolean ManualPixelChange(int px, int py, int delta)
	{

		if(px < 0)
			return false;
		if(py < 0)
			return false;
		if(px > trueDim)
			return false;
		if(py > trueDim)
			return false;
		boolean check = ManualPixelChangeHelper(px, py, delta);
		if(!check)
			return false;
		if(px == 0)
		{
			//We have to change the West
			DataImageInt alt = GetWest();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(trueDim, py, delta);
			if(!check)
				return false;
			if(py == 0)
			{
				//We have to change North and NorthWest too
				alt = GetNorth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, trueDim, delta);
				if(!check)
					return false;
				alt = alt.GetWest();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(trueDim, trueDim, delta);
				if(!check)
					return false;
			}
			else if(py == trueDim)
			{
				//We have to change South and SouthWest too
				alt = GetSouth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, 0, delta);
				if(!check)
					return false;
				alt = alt.GetWest();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(trueDim, 0, delta);
				if(!check)
					return false;
			}
		}
		else if(px == trueDim)
		{
			//We have to change the East
			DataImageInt alt = GetEast();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(0, py, delta);
			if(!check)
				return false;
			if(py == 0)
			{
				//We have to change North and NorthEast too
				alt = GetNorth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, trueDim, delta);
				if(!check)
					return false;
				alt = alt.GetEast();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(0, trueDim, delta);
				if(!check)
					return false;
			}
			else if(py == trueDim)
			{
				//We have to change South and SouthEast too
				alt = GetSouth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, 0, delta);
				if(!check)
					return false;
				alt = alt.GetEast();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(0, 0, delta);
				if(!check)
					return false;
			}
		}
		else if(py == 0)
		{
			//We have to change the North
			DataImageInt alt = GetNorth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, trueDim, delta);
			if(!check)
				return false;
		}
		else if(py == trueDim)
		{
			//We have to change the South
			DataImageInt alt = GetSouth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, 0, delta);
			if(!check)
				return false;
		}
		return true;
	}
	public void Set(int px, int py, int val)
	{
		int current = Get(px, py);
		int delta = val - current;
		ManualPixelChange(px, py, delta);
	}
	public int Get(int px, int py)
	{
		int res = GetCurrentResolution();
		if(res < 0)
			return 0;
		int dim = dimRange[res];
		if(px < 0)
		{
			DataImageInt alt = GetWest();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px + dim, py);
		}
		if(px > dim)
		{
			DataImageInt alt = GetEast();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px - dim, py);
		}
		if(py < 0)
		{
			DataImageInt alt = GetNorth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px, py + dim);
		}
		if(py > dim)
		{
			DataImageInt alt = GetSouth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px, py - dim);
		}
		int[] best = data.getLast();
		return GetVal(best, dim + 1, px, py);
	}
	public int Get(double x, double y)
	{
		if(x < 0)
		{
			DataImageInt alt = GetWest();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x + 1, y);
		}
		if(x > 1)
		{
			DataImageInt alt = GetEast();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x - 1, y);
		}
		if(y < 0)
		{
			DataImageInt alt = GetNorth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x, y + 1);
		}
		if(y > 1)
		{
			DataImageInt alt = GetSouth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x, y - 1);
		}
		int res = GetCurrentResolution();
		if(res < 0)
			return 0;
		int dim = dimRange[res];
		x *= dim;
		y *= dim;
		int[] best = data.getLast();
		
		if(x == Math.floor(x) && y == Math.floor(y))
			return GetVal(best, dim + 1, (int) x, (int) y);
		
		int px0 = (int) x;
		int px1 = px0 + 1;
		int py0 = (int) y;
		int py1 = py0 + 1;
		double tX = x - px0;
		double tY = y - py0;
		double val00 = GetVal(best, dim + 1, px0, py0);
		double val01 = GetVal(best, dim + 1, px0, py1);
		double val10 = GetVal(best, dim + 1, px1, py0);
		double val11 = GetVal(best, dim + 1, px1, py1);
		double valA = MathToolkit.SmoothLerp(val00, val10, tX);
		double valB = MathToolkit.SmoothLerp(val01, val11, tX);
		return (int) (MathToolkit.SmoothLerp(valA, valB, tY));
	}
	
	private static int GetVal(int[] target, int dim, int px, int py)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return 0;
		if(px * dim + py >= target.length)
			return 0;
		return target[px * dim + py];
	}
	private static void SetVal(int[] target, int dim, int px, int py, int val)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return;
		if(px * dim + py >= target.length)
			return;
		target[px * dim + py] = val;
	}

	@Override
	protected boolean SaveImage(int[] img, String filename) 
	{
		File file = new File(filename);
		try 
		{
			FileOutputStream fos = new FileOutputStream(file);
			GZIPOutputStream gzip = new GZIPOutputStream(fos);
			DataOutputStream dos = new DataOutputStream(gzip);
			dos.writeInt(img.length);
			for(int value : img)
				dos.writeInt(value);
			dos.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	protected int[] Read(File file) 
	{
		try
		{
			FileInputStream fis = new FileInputStream(file);
			GZIPInputStream gzip = new GZIPInputStream(fis);
			DataInputStream dis = new DataInputStream(gzip);
			int length = dis.readInt();
			int[] data = new int[length];
            for (int i = 0; i < length; i++)
                data[i] = dis.readInt();
			dis.close();
			return data;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected int[] Create(int dim) 
	{
		final int[] nova = new int[(dim + 1) * (dim + 1)];
		if(Switches.PARALLEL_RENDERING)
		{
			IntStream.range(0, (dim + 1) * (dim + 1)).parallel().forEach(index ->{
				int j = index / (dim + 1);
				int i = index % (dim + 1);
				double x = i;
				double y = j;
				x /= dim;
				y /= dim;
				int val = source.GetData(x, y);
				SetVal(nova, dim + 1, i, j, val);
			});
		}
		else
		{
			for(int i = 0; i <= dim; i++)
			{
				for(int j = 0; j <= dim; j++)
				{
					double x = 1.0 * i / dim;
					double y = 1.0 * j / dim;
					int val = source.GetData(x, y);
					SetVal(nova, dim + 1, i, j, val);
				}
			}
		}
		return nova;
	}
	
	public static interface DataProvider extends DataImage.DataProviderBase<int[], DataImageInt>
	{
		public int GetData(double x, double y);
	}
	
	public static class DataImageManager extends DataImage.DataImageManagerBase<int[], DataImageInt>
	{
		public DataImageManager(int[] cap) {
			super(cap);
		}
	}
}