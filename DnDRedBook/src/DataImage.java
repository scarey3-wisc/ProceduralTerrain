import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class DataImage<T, K extends DataImage<T, K>>
{
	private final String parentDir;
	private final String dirPrefix;
	private final String folderName;
	private final String fullName;
	private DataImageManagerBase<T, K> manager;
	private boolean changesRelativeToFile;
	protected LinkedList<T> data;
	private DataProviderBase<T, K> source;
	private final ReentrantReadWriteLock imgLock = new ReentrantReadWriteLock();
	public DataImage(
			String parentDir, 
			String dirPrefix, 
			String folderName, 
			String fullName, 
			DataProviderBase<T, K> source,
			DataImageManagerBase<T, K> mgr)
	{
		this.manager = mgr;
		this.folderName = folderName;
		this.parentDir = parentDir;
		this.dirPrefix = dirPrefix;
		this.fullName = fullName;
		data = new LinkedList<T>();
		changesRelativeToFile = false;
		this.source = source;
	}
	public String GetFullName()
	{
		return fullName;
	}
	public double GetPixelSize()
	{
		if(GetCurrentResolution() == -1)
			return 0;
		int bestDim = dimRange[GetCurrentResolution()];
		if(bestDim == -1)
			return -1;
		double width = 1.0 / bestDim;
		return width;
	}
	
	public void AnnounceChangesRelativeToFile()
	{
		changesRelativeToFile = true;
	}
	public void RemoveFromManagement()
	{
		if(manager == null)
			return;
		manager.FullKick(this);
		manager = null;
	}
	public void GiveToManagement(DataImageManagerBase<T, K> newManager)
	{
		//we're already under this management!
		if(manager == newManager)
			return;
		
		//We need to get out of the previous management
		if(manager != null)
			RemoveFromManagement();
		
		newManager.RequestCachePresenceAtCurrentResolution(this);
		manager = newManager;
	}
	public void ForceEditReady(boolean verbose)
	{
		if(verbose)
			System.out.println("DataImage " + fullName + " getting ready to edit");
		DemandResolutionLevel(dimRange.length - 1);
	}
	private boolean KickTopResolution()
	{
		if(changesRelativeToFile)
			SaveTopResolution();
		
		imgLock.writeLock().lock();
		RemoveBestResolution();
		imgLock.writeLock().unlock();
		return true;
	}
	protected abstract boolean SaveImage(T img, String filename);
	public boolean SaveAllResolutions(boolean force)
	{
		if(!force && !changesRelativeToFile)
			return true;
		int index = 0;
		for(T img : data)
		{
			String fileName = GetFileName(index);
			if(!SaveImage(img, fileName))
				return false;
			index++;
		}
		changesRelativeToFile = false;
		return true;
	}
	private boolean SaveTopResolution()
	{
		T top = data.getLast();
		String fileName = GetFileName(data.size() - 1);
		return SaveImage(top, fileName);
	}
	protected void RemoveBestResolution()
	{
		data.removeLast();
	}
	public int GetCurrentResolution()
	{
		return data.size() - 1;
	}
	public void DemandResolution(int dim)
	{
		int targetResolution = 0;
		while(dimRange[targetResolution] < dim)
		{
			targetResolution++;
			if(targetResolution == dimRange.length)
			{
				targetResolution--;
				break;
			}
		}
		DemandResolutionLevel(targetResolution);
	}
	
	protected abstract T Read(File file);
	protected abstract T Create(int dim);
	
	//res is an index in dimRange; so resolution 0 is likely 16x16 pixels
	private void DemandResolutionLevel(int res)
	{
		//We're at or above that resolution, we're fine
		if(GetCurrentResolution() >= res)
			return;
		imgLock.writeLock().lock();
		while(GetCurrentResolution() < res)
		{
			int indexToAdd = GetCurrentResolution() + 1;
			//You've asked for a resolution level that we don't do; we're done here
			if(indexToAdd >= dimRange.length)
				break;
			
			//First, check if we have this next resolution on disk
			String fileName = GetFileName(indexToAdd);
			File betterFile = new File(fileName);
			if(betterFile.exists() && betterFile.isFile())
			{
				T read = Read(betterFile);
				if(read != null)
				{
					data.addLast(read);
					if(manager != null)
						manager.RequestCachePresenceAtCurrentResolution(this);
				}
				continue;
			}
			
			//Second: okay, so we'll have to ask the data provider.
			int betterDim = dimRange[indexToAdd];
			T better = Create(betterDim);
			data.addLast(better);
			if(manager != null)
				manager.RequestCachePresenceAtCurrentResolution(this);
			SaveTopResolution();
		}
		imgLock.writeLock().unlock();
	}
	private String GetFileName(int dimIndex)
	{
		if(dimIndex < 0 || dimIndex >= dimRange.length)
			return "";
		int dim = dimRange[dimIndex];
		String file = parentDir + File.separator + dirPrefix;
		file += Integer.toString(dim);
		file += File.separator + folderName + ".rsdat";
		return file;
	}
	public K GetNorth()
	{
		return source.GetNorth();
	}
	public K GetSouth()
	{
		return source.GetSouth();
	}
	public K GetWest()
	{
		return source.GetWest();
	}
	public K GetEast()
	{
		return source.GetEast();
	}
	
	public final static int[] dimRange = new int[] { 16, 32, 64, 128, 256 };
	public final static int trueDim = dimRange[dimRange.length - 1];
	/*
	 * Here's the idea behind this interface:
	 * 1. The DataImage wants to render a pixel for some resolution
	 * 2. It locates that pixel as an x, y in [0, 1] * [0, 1]
	 * 3. It tells its data provider "excuse me, I want data at this x, y"
	 * 4. Data provider gives the float value for that location
	 * 5. DataImage saves that float as an RGB to the correct pixel
	 */
	protected static interface DataProviderBase<B, A extends DataImage<B, A>>
	{
		public A GetNorth(); //meaning y < 0; we want to query the north image with y+1
		public A GetSouth(); //meaning y > 1; we want to query the south image with y-1
		public A GetWest(); //meaning x < 0
		public A GetEast(); //meaning x > 1
	}
	
	protected static abstract class DataImageManagerBase<B, A extends DataImage<B, A>>
	{
		private ArrayList<LinkedHashSet<DataImage<B, A>>> caches;
		private int numCaches = DataImage.dimRange.length;
		private int[] capacities = new int[numCaches];
		private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		public DataImageManagerBase(int[] cap)
		{
			caches = new ArrayList<LinkedHashSet<DataImage<B, A>>>();
			for(int i = 0; i < numCaches; i++)
			{
				if(i < cap.length)
					this.capacities[i] = cap[i];
				else
					this.capacities[i] = 0;
				caches.add(new LinkedHashSet<DataImage<B, A>>());
			}
		}
		public void SaveAll()
		{
			for(int i = 0; i < numCaches; i++)
			{
				for(DataImage<B, A> di : caches.get(i))
					di.SaveAllResolutions(false);
			}
		}
		public int GetCurrentSize(int index)
		{
			if(index >= 0 && index < caches.size())
				return caches.get(index).size();
			return 0;
		}
		public int GetCapacity(int index)
		{
			if(index >= 0 && index < capacities.length)
				return capacities[index];
			return 0;
		}
		private void FullKick(DataImage<B, A> requester)
		{
			lock.writeLock().lock();
			for(LinkedHashSet<DataImage<B, A>> cache : caches)
			{
				cache.remove(requester);
			}
			lock.writeLock().unlock();
		}
		private void RequestCachePresenceAtCurrentResolution(DataImage<B, A> requester)
		{
			lock.readLock().lock();
			int targetIndex = requester.GetCurrentResolution();
			//We have the image where it should be, we're done
			if(caches.get(targetIndex).contains(requester))
			{
				lock.readLock().unlock();
				return;
			}
			int currentIndex = -1;
			for(int i = numCaches - 1; i >= 0; i--)
			{
				if(caches.get(i).contains(requester))
				{
					currentIndex = i;
					break;
				}
			}
			//We have the image at better than it needs to be, we're done
			if(currentIndex > targetIndex)
			{
				lock.readLock().unlock();
				return;	
			}
			lock.readLock().unlock();
			
			ArrayList<DataImage<B, A>> kicked = new ArrayList<DataImage<B, A>>();
			lock.writeLock().lock();
			//We need to remove it from its current spot before adding it somewhere else
			if(currentIndex != -1)
			{
				caches.get(currentIndex).remove(requester);
			}
			KickingInsert(requester, targetIndex, kicked);
			lock.writeLock().unlock();
			for(DataImage<B, A> di : kicked)
				di.KickTopResolution();
		}
		private void KickingInsert(DataImage<B, A> add, int index, ArrayList<DataImage<B, A>> kickRecord)
		{
			if(index == -1)
				return;
			caches.get(index).add(add);
			if(caches.get(index).size() > capacities[index])
			{
				DataImage<B, A> kicked = caches.get(index).iterator().next();
				caches.get(index).remove(kicked);
				kickRecord.add(kicked);
				KickingInsert(kicked, index - 1, kickRecord);
			}
		}
	}
}