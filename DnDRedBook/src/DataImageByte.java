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

public class DataImageByte extends DataImage<byte[], DataImageByte>
{
	private DataProvider source;
	public DataImageByte(
			String parentDir, 
			String dirPrefix, 
			String folderName, 
			String fullName,
			DataProvider source, 
			DataImage.DataImageManagerBase<byte[], DataImageByte> mgr) 
	{
		super(parentDir, dirPrefix, folderName, fullName, source, mgr);
		this.source = source;
	}
	
	private boolean ManualPixelChangeHelper(int px, int py, byte set)
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
		ListIterator<byte[]> it = data.listIterator(data.size());
		int index = dimRange.length;
		while(it.hasPrevious())
		{
			index--;
			byte[] target = it.previous();
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
	public boolean ManualPixelChange(int px, int py, byte set)
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
			DataImageByte alt = GetWest();
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
			DataImageByte alt = GetEast();
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
			DataImageByte alt = GetNorth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, trueDim, set);
			if(!check)
				return false;
		}
		else if(py == trueDim)
		{
			//We have to change the South
			DataImageByte alt = GetSouth();
			if(alt == null)
				return false;
			check = alt.ManualPixelChangeHelper(px, 0, set);
			if(!check)
				return false;
		}
		return true;
	}
	public byte Get(int px, int py)
	{
		int res = GetCurrentResolution();
		if(res < 0)
			return 0;
		int dim = dimRange[res];
		
		if(px < 0)
		{
			DataImageByte alt = GetWest();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px + dim, py);
		}
		if(px > dim)
		{
			DataImageByte alt = GetEast();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px - dim, py);
		}
		if(py < 0)
		{
			DataImageByte alt = GetNorth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px, py + dim);
		}
		if(py > dim)
		{
			DataImageByte alt = GetSouth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(px, py - dim);
		}
		

		byte[] best = data.getLast();
		
		return GetVal(best, dim + 1, px, py);
	}
	public byte Get(double x, double y)
	{
		if(x < 0)
		{
			DataImageByte alt = GetWest();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x + 1, y);
		}
		if(x > 1)
		{
			DataImageByte alt = GetEast();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x - 1, y);
		}
		if(y < 0)
		{
			DataImageByte alt = GetNorth();
			if(alt == null)
				return 0;
			alt.DemandResolution(GetCurrentResolution());
			return alt.Get(x, y + 1);
		}
		if(y > 1)
		{
			DataImageByte alt = GetSouth();
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
		byte[] best = data.getLast();
		int px0 = (int) x;
		int px1 = px0 + 1;
		int py0 = (int) y;
		int py1 = py0 + 1;
		double tX = x - px0;
		double tY = y - py0;
		
		byte result = 0;
		for(int i = 0; i < 8; i++)
		{
			double val00 = MathToolkit.ExtractBitFromByte(GetVal(best, dim + 1, px0, py0), i) ? 1. : 0.;
			double val01 = MathToolkit.ExtractBitFromByte(GetVal(best, dim + 1, px0, py1), i) ? 1. : 0.;
			double val10 = MathToolkit.ExtractBitFromByte(GetVal(best, dim + 1, px1, py0), i) ? 1. : 0.;
			double val11 = MathToolkit.ExtractBitFromByte(GetVal(best, dim + 1, px1, py1), i) ? 1. : 0.;
			double valA = MathToolkit.SmoothLerp(val00, val10, tX);
			double valB = MathToolkit.SmoothLerp(val01, val11, tX);
			double outcome = MathToolkit.SmoothLerp(valA, valB, tY);
			if(outcome > 0.5)
				result = (byte) (result | (1 << i));
		}
		return result;
	}
	
	private static byte GetVal(byte[] target, int dim, int px, int py)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return 0;
		if(px * dim + py >= target.length)
			return 0;
		byte val = target[px * dim + py];
		return val;
	}
	private static void SetVal(byte[] target, int dim, int px, int py, byte val)
	{
		if(px < 0 || py < 0 || px >= dim || py >= dim)
			return;
		if(px * dim + py >= target.length)
			return;
		target[px * dim + py] = val;
	}

	@Override
	protected boolean SaveImage(byte[] img, String filename) 
	{
		File file = new File(filename);
		try 
		{
			FileOutputStream fos = new FileOutputStream(file);
			GZIPOutputStream gzip = new GZIPOutputStream(fos);
			DataOutputStream dos = new DataOutputStream(gzip);
			dos.writeInt(img.length);
			for(int value : img)
				dos.writeByte(value);
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
	protected byte[] Read(File file) 
	{
		try
		{
			FileInputStream fis = new FileInputStream(file);
			GZIPInputStream gzip = new GZIPInputStream(fis);
			DataInputStream dis = new DataInputStream(gzip);
			int length = dis.readInt();
			byte[] data = new byte[length];
            for (int i = 0; i < length; i++)
                data[i] = dis.readByte();
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
	protected byte[] Create(int dim) 
	{
		final byte[] nova = new byte[(dim + 1) * (dim + 1)];
		if(Switches.PARALLEL_RENDERING)
		{
			IntStream.range(0, (dim + 1) * (dim + 1)).parallel().forEach(index ->{
				int j = index / (dim + 1);
				int i = index % (dim + 1);
				double x = i;
				double y = j;
				x /= dim;
				y /= dim;
				byte val = source.GetData(x, y);
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
					byte val = source.GetData(x, y);
					SetVal(nova, dim + 1, i, j, val);
				}
			}
		}
		return nova;
	}
	
	public static interface DataProvider extends DataImage.DataProviderBase<byte[], DataImageByte>
	{
		public byte GetData(double x, double y);
	}
	
	public static class DataImageManager extends DataImage.DataImageManagerBase<byte[], DataImageByte>
	{
		public DataImageManager(int[] cap) {
			super(cap);
		}
	}
}