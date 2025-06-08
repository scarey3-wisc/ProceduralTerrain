import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class RenderQueue
{
	private static int frameID = 0;
	private static EmptyQueue qe;
	private static PriorityQueue<RenderRequest> renderQueue = new PriorityQueue<RenderRequest>();
	private static HashMap<RenderRequester, TreeMap<RenderRequest, RenderRequest>> renderSet = 
			new HashMap<RenderRequester, TreeMap<RenderRequest, RenderRequest>>();
	public static void IncrementFrameID()
	{
		frameID++;
	}
	public synchronized static void QueueRender(RenderRequester requests, int dim, String name, boolean withdrawable)
	{
		RenderRequest next = new RenderRequest(requests, dim, name, withdrawable);
		if(renderSet.get(requests) == null)
			renderSet.put(requests, new TreeMap<RenderRequest, RenderRequest>());
		TreeMap<RenderRequest, RenderRequest> active = renderSet.get(requests);
		
		RenderRequest prev = active.get(next);
		if(prev != null)
		{
			prev.UpdateIssueID();
			return;
		}
		active.put(next, next);
		
		renderQueue.offer(next);
		if(qe == null)
		{
			qe = new EmptyQueue();
			Thread empty = new Thread(qe);
			empty.start();
		}
	}
	public synchronized static RenderRequest DequeueRender()
	{
		return renderQueue.poll();
	}
	public synchronized static boolean QueueEmpty()
	{
		return renderQueue.isEmpty();
	}
	private synchronized static void CompleteRender(RenderRequest de)
	{
		TreeMap<RenderRequest, RenderRequest> rec = renderSet.get(de.callback);
		if(rec == null)
			return;
		rec.remove(de);
		if(rec.size() == 0)
			renderSet.remove(de.callback);
	}
	public static int NumItemsQueued()
	{
		return renderQueue.size();
	}
	private static class EmptyQueue implements Runnable
	{

		@Override
		public void run() 
		{
			while(!QueueEmpty())
			{
				RenderRequest de = DequeueRender();
				if(de.ShouldBeWithdrawn())
				{
					CompleteRender(de);
				}
				else 
				{
					de.callback.RunFullRender(de.dim, de.renderName);
					CompleteRender(de);
				}
			}
			qe = null;
		}
		
	}
	private static class RenderRequest implements Comparable<RenderRequest>
	{
		private RenderRequester callback;
		private int dim;
		private String renderName;
		private boolean withdrawOkay;
		private int issueID;
		
		public RenderRequest(RenderRequester callback, int dim, String name, boolean withdrawOkay)
		{
			this.callback = callback;
			this.dim = dim;
			this.renderName = name;
			this.withdrawOkay = withdrawOkay;
			this.issueID = frameID;
		}
		public void UpdateIssueID()
		{
			this.issueID = frameID;
		}
		public boolean ShouldBeWithdrawn()
		{
			if(!withdrawOkay)
				return false;
			if(issueID == frameID)
				return false;
			if(issueID + 1 == frameID)
				return false;
			return true;
		}
		@Override
	    public boolean equals(Object o) {
	        if (this == o) return true;
	        if (!(o instanceof RenderRequest)) return false;
	        RenderRequest rr = (RenderRequest) o;
	        return rr.callback == callback && rr.dim == dim && rr.renderName == renderName;
	    }
		@Override
		public int compareTo(RenderQueue.RenderRequest o) {
			if(dim < o.dim)
				return -1;
			if(dim > o.dim)
				return 1;
			if(renderName.compareTo(o.renderName) != 0)
				return renderName.compareTo(o.renderName);
			return 0;
		}
	}
	public static interface RenderRequester
	{
		void RunFullRender(int dimension, String name);
	}
}