import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;


public class ImageArchive
{
	public ImageCache[] caches;
	public ImageArchive(int num, int[] pixelMins, int[] pixelMaxes, long[] pixelCapacities, String[] names)
	{
		caches = new ImageCache[num];
		for(int i = 0; i < num; i++)
		{
			caches[i] = new ImageCache(pixelMins[i], pixelMaxes[i], pixelCapacities[i], names[i]);
		}
	}
	public ImageArchive(long capacity)
	{
		caches = new ImageCache[1];
		caches[0] = new ImageCache(0, Integer.MAX_VALUE, capacity, "Default");
	}
	public ArrayList<ImageRender> GetAllWithPrefix(String prefix)
	{
		ArrayList<ImageRender> result = new ArrayList<ImageRender>();
		for(ImageCache i : caches)
			result.addAll(i.GetAllWithPrefix(prefix));
		return result;
	}
	public void ClearPrefix(String prefix)
	{
		for(ImageCache i : caches)
		{
			i.RemoveAllWithPrefix(prefix);
		}
	}
	public void Clear(String name)
	{
		for(ImageCache i : caches)
		{
			i.Remove(name);
		}
	}
	public ImageRender Query(String name)
	{
		for(ImageCache i : caches)
		{
			ImageRender candidate = i.Get(name);
			if(candidate != null)
				return candidate;
		}
		return null;
	}
	public void Insert(String name, ImageRender ins)
	{
		long pixels = ins.getWidth() * ins.getHeight();
		for(ImageCache i : caches)
		{
			if(pixels >= i.minPixelSize && pixels <= i.maxPixelSize)
			{
				i.Put(name, ins);
				return;
			}
		}
		return;
	}
	
	public class ImageCache
	{
		private LinkedHashMap<String, ImageRender> images;
		private HashMap<String, HashSet<String>> prefixLookup;
		
		private int minPixelSize;
		private int maxPixelSize;
		private long pixelStorageMax = 10000000l;
		private long storedPixels;
		private int storedImages;
		private String name;
		
		public ImageCache(int minPixels, int maxPixels, long storageMax, String name)
		{
			minPixelSize = minPixels;
			maxPixelSize = maxPixels;
			pixelStorageMax = storageMax;
			this.name = name;
			ResetAll();
		}
		public String GetLibraryName()
		{
			return name;
		}
		public long PixelsStorageMax()
		{
			return pixelStorageMax;
		}
		public int NumImages()
		{
			return storedImages;
		}
		public long NumPixels()
		{
			return storedPixels;
		}
		public synchronized boolean Contains(String name)
		{
			return images.containsKey(name);
		}
		/*public ImageRender Request(String filepath)
		{
			if(Contains(filepath))
				return Get(filepath);
			
			try {
				ImageRender found = ImageIO.read(new File(filepath));
				Put(filepath, found);
				return found;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}*/
		public synchronized ArrayList<ImageRender> GetAllWithPrefix(String prefix)
		{
			ArrayList<ImageRender> result = new ArrayList<ImageRender>();
			HashSet<String> alias = prefixLookup.get(prefix);
			if(alias == null)
				return result;
			String[] contents = new String[alias.size()];
			int i = 0;
			for(String s : alias)
			{
				contents[i] = s;
				i++;
			}
			for(String s : contents)
			{
				ImageRender bi = Get(s);
				if(bi != null)
					result.add(bi);
			}
			return result;
		}
		public synchronized void RemoveAllWithPrefix(String prefix)
		{
			HashSet<String> alias = prefixLookup.get(prefix);
			if(alias == null)
				return;
			String[] contents = new String[alias.size()];
			int i = 0;
			for(String s : alias)
			{
				contents[i] = s;
				i++;
			}
			for(String s : contents)
				Remove(s);
		}
		public synchronized void ResetAll()
		{
			storedPixels = 0;
			storedImages = 0;
			images = new LinkedHashMap<String, ImageRender>();
			prefixLookup = new HashMap<String, HashSet<String>>();
		}
		public synchronized ImageRender Get(String name)
		{
			ImageRender stored = images.get(name);
			if(stored == null)
				return null;
			images.remove(name);
			images.put(name, stored);
			return stored;
		}
		public synchronized void Put(String name, ImageRender store)
		{
			if(images.containsKey(name))
				Remove(name);
			long pixels = store.getWidth() * store.getHeight();
			while(storedPixels + pixels > pixelStorageMax && images.size() > 0)
				RemoveOldest();
			images.put(name, store);
			storedPixels += pixels;
			storedImages++;
			
			if(name.contains(PREFIX_MARKER))
			{
				String prefix = name.substring(0, name.indexOf(PREFIX_MARKER));
				HashSet<String> aliasSet = prefixLookup.get(prefix);
				if(aliasSet == null)
				{
					aliasSet = new HashSet<String>();
					prefixLookup.put(prefix, aliasSet);
				}
				aliasSet.add(name);
			}
		}
		private synchronized void RemoveOldest()
		{
			if(images.size() == 0)
				return;
			String oldestName = images.entrySet().iterator().next().getKey();
			Remove(oldestName);
		}
		public synchronized void Remove(String name)
		{
			if(!images.containsKey(name))
				return;
			ImageRender stored = images.get(name);
			images.remove(name);
			storedPixels -= stored.getHeight() * stored.getWidth();
			storedImages--;
			
			if(name.contains(PREFIX_MARKER))
			{
				String prefix = name.substring(0, name.indexOf(PREFIX_MARKER));
				HashSet<String> aliasSet = prefixLookup.get(prefix);
				if(aliasSet != null)
				{
					aliasSet.remove(name);
					if(aliasSet.size() == 0)
						prefixLookup.remove(prefix);
				}
			}
		}
		
	}
	public static final String PREFIX_MARKER = ":!:";

}
