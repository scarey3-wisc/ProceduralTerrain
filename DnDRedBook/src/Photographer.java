import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.IntStream;
import javax.swing.JPanel;
import java.util.concurrent.locks.ReentrantLock;


public class Photographer extends JPanel implements RedBook.RenderPanel
{
	private HashSet<Integer> keysDown;
	private int mouseX;
	private int mouseY;
	private WorldMap map;
	private LocalMap area;
	private double lX;
	private double lY;
	private double altitude;
	private double theta = 0;
	private double phi = 0;
	private double fov = 30;
	private Vec3 viewDirection;
	
	private int activeMeshes;
	private TerrainMesh[] meshes;
	private final int chunkRange = 1;
	private Mover myMove;
	private long renderMovingAverage;
	private final ReentrantLock chunkShiftingLock = new ReentrantLock();
	
	private static final long serialVersionUID = 1L;
	public Photographer(WorldMap m)
	{
		map = m;
		CalcViewDirection();
		ViewAdjuster look = new ViewAdjuster();
		this.addKeyListener(look);
		this.addMouseMotionListener(look);
		keysDown = new HashSet<Integer>();
		renderMovingAverage = 100;
		meshes = new TerrainMesh[(chunkRange * 2 + 1) * (chunkRange * 2 + 1)];
	}
	private void CalcViewDirection()
	{
		double z = Math.sin(Math.toRadians(phi));
		double x = Math.cos(Math.toRadians(phi)) * Math.cos(Math.toRadians(theta));
		double y = Math.cos(Math.toRadians(phi)) * Math.sin(Math.toRadians(theta));
		viewDirection = new Vec3(x, y, z);
	}
	public void PositionFromWorldMapView()
	{
		int mouseX = getWidth() / 2;
		int mouseY = getHeight() / 2;		
		RegionalMap.Coordinate region = map.GetRegionalMapAt(mouseX, mouseY);
		if(region == null)
			return;
		LocalMap.Coordinate local = region.GetRegionalMap().GetLocalMapAt(region.x, region.y);
		if(local.GetLocalMap() == null)
			return;
		this.area = local.GetLocalMap();
		
		this.lX = local.x;
		this.lY = local.y;
		this.lX = 0.5;
		this.lY = 0.99;
		this.altitude = this.area.GetHeight(lX, lY) + 50;
		this.phi = 0;
		this.theta = 90;
		CalcViewDirection();
	}
	public ArrayList<TriangleMesh.Triangle> PrepTriangles(int width, int height, Mat4 viewMat, Mat4 projMat)
	{
		activeMeshes = 0;
		ArrayList<TriangleMesh.Triangle> activeTris = new ArrayList<TriangleMesh.Triangle>();
		chunkShiftingLock.lock();
		for(TerrainMesh tm : meshes)
		{
			if(tm == null)
				continue;
			if(!tm.ReadyToRender())
				continue;
			if(!tm.VisibleInClipSpace(viewMat, projMat))
				continue;
			tm.TransformToClipSpace(viewMat, projMat);
			int size = activeTris.size();
			tm.FillTriangles(activeTris, width, height);
			if(activeTris.size() == size)
			{
				tm.VisibleInClipSpace(viewMat, projMat);
			}
			activeMeshes++;
		}
		chunkShiftingLock.unlock();
		
		return activeTris;
	}
	public long Render()
	{
		long startTime = System.currentTimeMillis();
		int screenWidth = getWidth();
		int screenHeight = getHeight();
		if(screenWidth == 0 || screenHeight == 0)
			return 10l;
		BufferedImage nova = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
		Graphics ng = nova.getGraphics();
		ng.setColor(Color.black);
		ng.fillRect(0, 0, screenWidth, screenHeight);
		
		Vec3 cameraPosition = new Vec3(lX * LocalMap.METER_DIM, lY * LocalMap.METER_DIM, altitude);
		Vec3 upwards = new Vec3(0, 0, 1);
		Mat4 viewMat = Mat4.ViewMatrix(cameraPosition, viewDirection, upwards);
		Mat4 projMat = Mat4.ProjectionMatrix(fov, 1.0 * screenWidth / screenHeight, 1, 1000);
		ArrayList<TriangleMesh.Triangle> activeTriangles = PrepTriangles(screenWidth, screenHeight, viewMat, projMat);
		
		if(Switches.PHOTOGRAPH_PARALLEL_TILED_RENDER)
		{
			int bucketSize = 64;
			int numBucketsWide = (int) Math.ceil(1.0 * screenWidth / bucketSize);
			int numBucketsHigh = (int) Math.ceil(1.0 * screenHeight / bucketSize);
			ArrayList<ArrayList<TriangleMesh.Triangle>> buckets = new ArrayList<ArrayList<TriangleMesh.Triangle>>();
			for(int i = 0; i < numBucketsWide * numBucketsHigh; i++)
				buckets.add(new ArrayList<TriangleMesh.Triangle>());
			for(TriangleMesh.Triangle t : activeTriangles)
			{
				int bucketXStart = t.GetLeftBounds() / bucketSize;
				int bucketXEnd = 1 + t.GetRightBounds() / bucketSize;  
				int bucketYStart = t.GetUpBounds() / bucketSize;
				int bucketYEnd = 1 + t.GetDownBounds() / bucketSize;
				
				if(bucketXStart < 0)
					bucketXStart = 0;
				if(bucketYStart < 0)
					bucketYStart = 0;
				if(bucketXEnd > numBucketsWide)
					bucketXEnd = numBucketsWide;
				if(bucketYEnd > numBucketsHigh)
					bucketYEnd = numBucketsHigh;
				for(int j = bucketYStart ; j < bucketYEnd; j++)
				{
					for(int i = bucketXStart; i < bucketXEnd; i++)
					{
						int bucketIndex = j * numBucketsWide + i;
						buckets.get(bucketIndex).add(t);
					}
				}
			}
			IntStream.range(0, buckets.size()).parallel().forEach(index -> {
				BufferedImage tile = new BufferedImage(bucketSize, bucketSize, BufferedImage.TYPE_INT_RGB);
				Graphics tg = tile.getGraphics();
				tg.setColor(Color.black);
				tg.fillRect(0, 0, bucketSize, bucketSize);
				final int[] bucketPixels = ((DataBufferInt) tile.getRaster().getDataBuffer()).getData();
				double[] bucketZBuffer = new double[bucketPixels.length];
				for(int i = 0; i < bucketZBuffer.length; i++)
					bucketZBuffer[i] = Integer.MAX_VALUE;
				
				int bucketX = index % numBucketsWide;
				int bucketY = index / numBucketsWide;
				int x0 = bucketX * bucketSize;
				int y0 = bucketY * bucketSize;
				for(TriangleMesh.Triangle t : buckets.get(index))
				{
					RenderTriangle(bucketPixels, bucketZBuffer, t, x0, y0, bucketSize, bucketSize);
				}
				ng.drawImage(tile, x0, y0, null);
			});
		}
		else
		{
			final int[] pixels = ((DataBufferInt) nova.getRaster().getDataBuffer()).getData();
			double[] zDepth = new double[pixels.length];
			for(int i = 0; i < zDepth.length; i++)
				zDepth[i] = Integer.MAX_VALUE;
			for(TriangleMesh.Triangle t : activeTriangles)
			{
				RenderTriangle(pixels, zDepth, t, 0, 0, screenWidth, screenHeight);
			}
		}
		
		
		
		long deltaTime = System.currentTimeMillis() - startTime;
		double deltaAverageSmoothness = 0.1;
		renderMovingAverage = (long) (1.0 * (1.0 - deltaAverageSmoothness) * renderMovingAverage + deltaAverageSmoothness * deltaTime);
		int fps = (int) (1000. / renderMovingAverage);
		ng.setColor(Color.black);
		String msg = fps + " fps for " + activeTriangles.size() + " tris and " + activeMeshes + " meshes";
		int width = ng.getFontMetrics().stringWidth(msg);
		ng.fillRect(0, 0, width, 12);
		ng.setColor(Color.white);
		ng.drawString(msg, 0, 10);
		
		ng.setColor(new Color(128, 128, 128, 128));
		ng.fillRect(screenWidth / 2 - 2, screenHeight / 2 - 20, 4, 40);
		ng.fillRect(screenWidth / 2 - 20, screenHeight / 2 - 2, 40, 4);
		
		Graphics g = this.getGraphics();
		if(g == null)
			return 10l;
		g.drawImage(nova, 0, 0, null);
		return 0l;
	}
	private void RenderTriangle(final int[] pixels, double[] zDepth, TriangleMesh.Triangle t, 
			int x0, int y0, int w, int h)
	{
		
		double az = t.GetP0().z;
		double bz = t.GetP1().z;
		double cz = t.GetP2().z;
		double ax = t.GetX0();
		double bx = t.GetX1();
		double cx = t.GetX2();
		double ay = t.GetY0();
		double by = t.GetY1();
		double cy = t.GetY2();
		
		double abc = MathToolkit.SignedTriangleArea(ax, ay, bx, by, cx, cy);
		if(abc < 0)
			return;
		
		
		int iEnd = Math.min(x0 + w - 1, t.GetRightBounds());
		int jEnd = Math.min(y0 + h - 1, t.GetDownBounds());
		for(int j = Math.max(y0, t.GetUpBounds()); j <= jEnd; j++)
		{
			for(int i = Math.max(x0, t.GetLeftBounds()); i <= iEnd; i++)
			{
				double pbc = MathToolkit.SignedTriangleArea(i, j, bx, by, cx, cy);
				if(pbc < 0)
					continue;
				double apc = MathToolkit.SignedTriangleArea(ax, ay, i, j, cx, cy);
				if(apc < 0)
					continue;
				double abp = MathToolkit.SignedTriangleArea(ax, ay, bx, by, i, j);
				if(abp < 0)
					continue;
				
				double percA = pbc / abc;
				double percB = apc / abc;
				double percC = abp / abc;
				
				double z = az * percA + bz * percB + cz * percC;
				if(z > zDepth[(j - y0) * w + (i - x0)])
					continue;
				zDepth[(j - y0) * w + (i - x0)] = z;
				
				pixels[(j - y0) * w + (i - x0)] = t.Render(percA, percB, percC);
			}
		}
	}
	public void StartMoving()
	{
		myMove = new Mover();
		Thread movingThread = new Thread(myMove);
		movingThread.start();
	}
	public void StopMoving()
	{
		myMove.Stop();
	}
	private class Mover implements Runnable
	{
		private boolean stop;
		public void Stop()
		{
			stop = true;
		}
		public Mover()
		{
			stop = false;
		}
		@Override
		public void run() {
			while(!stop)
			{
				Move();
				if(lX < 0 || lY < 0 || lX >= 1 || lY >= 1)
					AdjustParent();
				CheckChunkLoads();
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			myMove = null;;
		}
		
	}
	private void Move()
	{
		if(keysDown.contains(KeyEvent.VK_LEFT))
			theta -= 2;

		if(keysDown.contains(KeyEvent.VK_RIGHT))
			theta += 2;
		
		if(keysDown.contains(KeyEvent.VK_UP))
			phi -= 2;
		
		if(keysDown.contains(KeyEvent.VK_DOWN))
			phi += 2;
		
		int mX = mouseX - getWidth() / 2;
		int mY = mouseY - getHeight() / 2;
		int scale = 200;
		int threshold = 10;
		double exp = 2;
		if(mX > threshold)
			theta += Math.pow(1.0 * Math.abs(mX - threshold) / scale, exp);
		if(mX < -1 * threshold)
			theta -= Math.pow(1.0 * Math.abs(mX + threshold) / scale, exp);
		if(mY > threshold)
			phi += Math.pow(1.0 * Math.abs(mY - threshold) / scale, exp);
		if(mY < threshold)
			phi -= Math.pow(1.0 * Math.abs(mY + threshold) / scale, exp);
		
		if(phi > 80)
			phi = 80;
		if(phi < -80)
			phi = -80;
		if(theta < 0)
			theta += 360;
		if(theta > 360)
			theta -= 360;
		
		CalcViewDirection();
		
		if(keysDown.contains(KeyEvent.VK_W))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			loc.Add(Vec3.Scale(viewDirection, -40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		if(keysDown.contains(KeyEvent.VK_S))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			loc.Add(Vec3.Scale(viewDirection, 40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		if(keysDown.contains(KeyEvent.VK_D))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			Vec3 upwards = new Vec3(0, 0, 1);
			Vec3 rightwards = viewDirection.Cross(upwards);
			loc.Add(Vec3.Scale(rightwards, 40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		if(keysDown.contains(KeyEvent.VK_A))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			Vec3 upwards = new Vec3(0, 0, 1);
			Vec3 rightwards = viewDirection.Cross(upwards);
			loc.Add(Vec3.Scale(rightwards, -40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		if(keysDown.contains(KeyEvent.VK_SHIFT))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			Vec3 upwards = new Vec3(0, 0, 1);
			loc.Add(Vec3.Scale(upwards, 40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		if(keysDown.contains(KeyEvent.VK_CONTROL))
		{
			double x = lX * LocalMap.METER_DIM;
			double y = lY * LocalMap.METER_DIM;
			double z = altitude;
			Vec3 loc = new Vec3(x, y, z);
			Vec3 upwards = new Vec3(0, 0, 1);
			loc.Add(Vec3.Scale(upwards, -40));
			lX = loc.x / LocalMap.METER_DIM;
			lY = loc.y / LocalMap.METER_DIM;
			altitude = loc.z;
		}
		double minHeight = area.GetHeight(lX, lY) + 50;
		if(altitude < minHeight)
			altitude = minHeight;
	}
	private void AdjustParent()
	{
		chunkShiftingLock.lock();
		int meshDim = chunkRange * 2 + 1;
		while(lX < 0)
		{
			LocalMap west = area.GetWest();
			if(west == null)
				break;
			area = west;
			lX += 1;
			
			for(int j = 0; j < meshDim; j++)
			{
				for(int i = meshDim - 1; i >= 0; i--)
				{
					TerrainMesh shifted = null;
					if(i > 0)
						shifted = meshes[meshDim * j + i - 1];
					meshes[meshDim * j + i] = shifted;
					if(shifted == null)
						continue;
					shifted.SetMeshShift(shifted.GetDeltaLX() + 1, shifted.GetDeltaLY());
				}
			}
		}
		while(lX >= 1)
		{
			LocalMap east = area.GetEast();
			if(east == null)
				break;
			area = east;
			lX -= 1;
			for(int j = 0; j < meshDim; j++)
			{
				for(int i = 0; i < meshDim; i++)
				{
					TerrainMesh shifted = null;
					if(i < meshDim - 1)
						shifted = meshes[meshDim * j + i + 1];
					meshes[meshDim * j + i] = shifted;
					if(shifted == null)
						continue;
					shifted.SetMeshShift(shifted.GetDeltaLX() - 1, shifted.GetDeltaLY());
				}
			}
		}
		while(lY < 0)
		{
			LocalMap north = area.GetNorth();
			if(north == null)
				break;
			area = north;
			lY += 1;
			
			for(int i = 0; i < meshDim; i++)
			{
				for(int j = meshDim - 1; j >= 0; j--)
				{
					TerrainMesh shifted = null;
					if(j > 0)
						shifted = meshes[meshDim * (j - 1) + i];
					meshes[meshDim * j + i] = shifted;
					if(shifted == null)
						continue;
					shifted.SetMeshShift(shifted.GetDeltaLX(), shifted.GetDeltaLY() + 1);
				}
			}
		}
		while(lY >= 1)
		{
			LocalMap south = area.GetSouth();
			if(south == null)
				break;
			area = south;
			lY -= 1;
			
			for(int i = 0; i < meshDim; i++)
			{
				for(int j = 0; j < meshDim; j++)
				{
					TerrainMesh shifted = null;
					if(j < meshDim - 1)
						shifted = meshes[meshDim * (j + 1) + i];
					meshes[meshDim * j + i] = shifted;
					if(shifted == null)
						continue;
					shifted.SetMeshShift(shifted.GetDeltaLX(), shifted.GetDeltaLY() - 1);
				}
			}
		}
		chunkShiftingLock.unlock();
	}
	private void CheckChunkLoads()
	{
		int centX = chunkRange;
		int centY = chunkRange;
		UpdateChunk(centX, centY, area);
		for(int dist = 1; dist <= chunkRange; dist++)
		{
			LocalMap north = area.GetNorth();
			UpdateChunk(centX, centY - dist, north);
			LocalMap westOfNorth = north.GetWest();
			for(int d = 1; d <= chunkRange; d++)
			{
				UpdateChunk(centX - d, centY - dist, westOfNorth);
				westOfNorth = westOfNorth.GetWest();
			}
			LocalMap eastOfNorth = north.GetEast();
			for(int d = 1; d < chunkRange; d++)
			{
				UpdateChunk(centX + d, centY - dist, eastOfNorth);
				eastOfNorth = eastOfNorth.GetEast();
			}
			
			
			LocalMap west = area.GetWest();
			UpdateChunk(centX - dist, centY, west);
			LocalMap southOfWest = west.GetSouth();
			for(int d = 1; d <= chunkRange; d++)
			{
				UpdateChunk(centX - dist, centY + d, southOfWest);
				southOfWest = southOfWest.GetSouth();
			}
			LocalMap northOfWest = west.GetNorth();
			for(int d = 1; d < chunkRange; d++)
			{
				UpdateChunk(centX - dist, centY - d, northOfWest);
				northOfWest = northOfWest.GetNorth();
			}
			
			
			LocalMap south = area.GetSouth();
			UpdateChunk(centX, centY + dist, south);
			LocalMap eastOfSouth = south.GetEast();
			for(int d = 1; d <= chunkRange; d++)
			{
				UpdateChunk(centX + d, centY + dist, eastOfSouth);
				eastOfSouth = eastOfSouth.GetEast();
			}
			LocalMap westOfSouth = south.GetWest();
			for(int d = 1; d < chunkRange; d++)
			{
				UpdateChunk(centX - d, centY + dist, westOfSouth);
				westOfSouth = westOfSouth.GetWest();
			}
			
			
			LocalMap east = area.GetEast();
			UpdateChunk(centX + dist, centY, east);
			LocalMap northOfEast = east.GetNorth();
			for(int d = 1; d <= chunkRange; d++)
			{
				UpdateChunk(centX + dist, centY - d, northOfEast);
				northOfEast = northOfEast.GetNorth();
			}
			LocalMap southOfEast = east.GetSouth();
			for(int d = 1; d < chunkRange; d++)
			{
				UpdateChunk(centX + dist, centY + d, southOfEast);
				southOfEast = southOfEast.GetSouth();
			}
		}
	}
	private void UpdateChunk(int x, int y, LocalMap realOwner)
	{
		int meshDim = chunkRange * 2 + 1;
		if(meshes[y * meshDim + x] == null || meshes[y * meshDim + x].GetSource() != realOwner)
		{
			if(realOwner == null)
				meshes[y * meshDim + x] = null;
			
			TerrainMesh nova = new TerrainMesh(realOwner, x - chunkRange, y - chunkRange);
			meshes[y * meshDim + x] = nova;
			RedBook.loadingThreadPool.submit(() -> {
				nova.PopulateMesh();
			});
		}
	}
	private class ViewAdjuster implements KeyListener, MouseMotionListener
	{

		@Override
		public void keyTyped(KeyEvent e) {
			
		}

		@Override
		public void keyPressed(KeyEvent e) {
			keysDown.add(e.getKeyCode());
		}

		@Override
		public void keyReleased(KeyEvent e) {
			keysDown.remove(e.getKeyCode());
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			mouseX = e.getX();
			mouseY = e.getY();
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			mouseX = e.getX();
			mouseY = e.getY();
		}
		
	}
}