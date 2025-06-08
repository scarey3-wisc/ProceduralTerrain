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

public class DataImage1Bit extends DataImage<boolean[], DataImage1Bit>
{
	private DataProvider source;
	public DataImage1Bit(
			String parentDir, 
			String dirPrefix, 
			String folderName, 
			String fullName,
			DataProvider source, 
			DataImage.DataImageManagerBase<boolean[], DataImage1Bit> mgr) 
	{
		super(parentDir, dirPrefix, folderName, fullName, source, mgr);
		this.source = source;
	}
	
	private boolean ManualPixelChangeHelper(int px, int py, boolean set)
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
		ListIterator<boolean[]> it = data.listIterator(data.size());
		int index = dimRange.length;
		while(it.hasPrevious())
		{
			index--;
			boolean[] target = it.previous();
			SetVal(target, dimRange[index] + 1, px, py, set);
			if(px % 2 != 0)
				break;
			if(py % 2 != 0)
				break;
			px /= 2;
			py /= 2;
		}
		
		return true;
	}
	public boolean ManualPixelChange(int px, int py, boolean set)
	{

		if(px < 0)
			return false;
		if(py < 0)
			return false;
		if(px > trueDim)
			return false;
		if(py > trueDim)
			return false;
		boolean check = ManualPixelChangeHelper(px, py, set);
		if(!check)
			return false;
		if(px == 0)
		{
			//We have to change the West
			DataImage1Bit alt = GetWest();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(trueDim, py, set);
			if(!check)
				return false;
			if(py == 0)
			{
				//We have to change North and NorthWest too
				alt = GetNorth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, trueDim, set);
				if(!check)
					return false;
				alt = alt.GetWest();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(trueDim, trueDim, set);
				if(!check)
					return false;
			}
			else if(py == trueDim)
			{
				//We have to change South and SouthWest too
				alt = GetSouth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, 0, set);
				if(!check)
					return false;
				alt = alt.GetWest();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(trueDim, 0, set);
				if(!check)
					return false;
			}
		}
		else if(px == trueDim)
		{
			//We have to change the East
			DataImage1Bit alt = GetEast();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(0, py, set);
			if(!check)
				return false;
			if(py == 0)
			{
				//We have to change North and NorthEast too
				alt = GetNorth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, trueDim, set);
				if(!check)
					return false;
				alt = alt.GetEast();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(0, trueDim, set);
				if(!check)
					return false;
			}
			else if(py == trueDim)
			{
				//We have to change South and SouthEast too
				alt = GetSouth();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(px, 0, set);
				if(!check)
					return false;
				alt = alt.GetEast();
				if(alt == null)
					return false;
				check = alt.ManualPixelChangeHelper(0, 0, set);
				if(!check)
					return false;
			}
		}
		else if(py == 0)
		{
			//We have to change the North
			DataImage1Bit alt = GetNorth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, trueDim, set);
			if(!check)
				return false;
		}
		else if(py == trueDim)
		{
			//We have to change the South
			DataImage1Bit alt = GetSouth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, 0, set);
			if(!check)
				return false;
		}
		return true;
	}
	public boolean Get(double x, double y)
	{
		if(x < 0)
		{
			DataImage1Bit alt = GetWest();
			if(alt == null)
				return false;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x + 1, y);
		}
		if(x > 1)
		{
			DataImage1Bit alt = GetEast();
			if(alt == null)
				return false;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x - 1, y);
		}
		if(y < 0)
		{
			DataImage1Bit alt = GetNorth();
			if(alt == null)
				return false;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x, y + 1);
		}
		if(y > 1)
		{
			DataImage1Bit alt = GetSouth();
			if(alt == null)
				return false;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x, y - 1);
		}
		int res = GetCurrentResolution();
		if(res < 0)
			return false;
		int dim = dimRange[res];
		x *= dim;
		y *= dim;
		boolean[] best = data.getLast();
		int px0 = (int) x;
		int px1 = px0 + 1;
		int py0 = (int) y;
		int py1 = py0 + 1;
		double tX = x - px0;
		double tY = y - py0;
		double val00 = GetVal(best, dim + 1, px0, py0) ? 1. : 0.;
		double val01 = GetVal(best, dim + 1, px0, py1) ? 1. : 0.;
		double val10 = GetVal(best, dim + 1, px1, py0) ? 1. : 0.;
		double val11 = GetVal(best, dim + 1, px1, py1) ? 1. : 0.;
		double valA = MathToolkit.SmoothLerp(val00, val10, tX);
		double valB = MathToolkit.SmoothLerp(val01, val11, tX);
		double result = MathToolkit.SmoothLerp(valA, valB, tY);
		
		return result > 0.5;
	}
	
	private static boolean GetVal(boolean[] target, int dim, int px, int py)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return false;
		if(px * dim + py >= target.length)
			return false;
		boolean val = target[px * dim + py];
		return val;
	}
	private static void SetVal(boolean[] target, int dim, int px, int py, boolean val)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return;
		if(px * dim + py >= target.length)
			return;
		target[px * dim + py] = val;
	}

	@Override
	protected boolean SaveImage(boolean[] img, String filename) 
	{
		File file = new File(filename);
		try 
		{
			FileOutputStream fos = new FileOutputStream(file);
			GZIPOutputStream gzip = new GZIPOutputStream(fos);
			DataOutputStream dos = new DataOutputStream(gzip);
			dos.writeInt(img.length);
			int byteValue = 0;
			int bitCount = 0;
			for(boolean value : img)
			{
				byteValue <<= 1;
				if(value)
					byteValue |= 1;
				bitCount++;
				if(bitCount == 8)
				{
					dos.writeByte(byteValue);
					byteValue = 0;
					bitCount = 0;
				}
			}
			if(bitCount > 0)
			{
				byteValue <<= (8 - bitCount);
				dos.writeByte(byteValue);
			}
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
	protected boolean[] Read(File file) 
	{
		try
		{
			FileInputStream fis = new FileInputStream(file);
			GZIPInputStream gzip = new GZIPInputStream(fis);
			DataInputStream dis = new DataInputStream(gzip);
			int length = dis.readInt();
			boolean[] data = new boolean[length];
			int index = 0;
			while(index < length)
			{
				byte value = dis.readByte();
				for(int i = 7; i >= 0 && index < length; i--)
				{
					data[index] = ((value >> i) & 1) == 1;
				}
			}
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
	protected boolean[] Create(int dim) 
	{
		final boolean[] nova = new boolean[(dim + 1) * (dim + 1)];
		if(Switches.PARALLEL_RENDERING)
		{
			IntStream.range(0, (dim + 1) * (dim + 1)).parallel().forEach(index ->{
				int j = index / (dim + 1);
				int i = index % (dim + 1);
				double x = i;
				double y = j;
				x /= dim;
				y /= dim;
				boolean val = source.GetData(x, y);
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
					boolean val = source.GetData(x, y);
					SetVal(nova, dim + 1, i, j, val);
				}
			}
		}
		return nova;
	}
	
	public static interface DataProvider extends DataImage.DataProviderBase<boolean[], DataImage1Bit>
	{
		public boolean GetData(double x, double y);
	}
	
	public static class DataImageManager extends DataImage.DataImageManagerBase<boolean[], DataImage1Bit>
	{
		public DataImageManager(int[] cap) {
			super(cap);
		}
	}
}