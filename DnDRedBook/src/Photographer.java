import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.JPanel;


public class Photographer extends JPanel implements RedBook.RenderPanel
{
	private HashSet<Integer> keysDown;
	private WorldMap map;
	private LocalMap area;
	private double lX;
	private double lY;
	private double altitude;
	private double theta = 0;
	private double phi = 0;
	private double fov = 30;
	private Vec3 viewDirection;
	TriangleMesh areaMesh;
	private Mover myMove;
	private long renderMovingAverage;
	
	private static final long serialVersionUID = 1L;
	public Photographer(WorldMap m)
	{
		map = m;
		CalcViewDirection();
		this.addKeyListener(new ViewAdjuster());
		keysDown = new HashSet<Integer>();
		renderMovingAverage = 100;
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
		this.lX = 0.5;
		this.lY = 0.5;
		
		area.PrepareForEditing(true, true, true);
		areaMesh = new TriangleMesh(area);
		area.CompleteEditing(true, true, true, true);
		this.altitude = this.area.GetHeight(lX, lY) + 50;
	}
	public ArrayList<TriangleMesh.Triangle> PrepTriangles(double ratio, Mat4 viewMat, Mat4 projMat)
	{
		areaMesh.TransformToClipSpace(viewMat, projMat);
		ArrayList<TriangleMesh.Triangle> activeTris = new ArrayList<TriangleMesh.Triangle>();
		for(TriangleMesh.Triangle t : areaMesh.tris)
		{
			if(t.GetP0().k < 0 || t.GetP1().k < 0 || t.GetP2().k < 0)
				continue;
			
			if(t.GetP0().x < -1 && t.GetP1().x < -1 && t.GetP2().x < -1)
				continue;
			if(t.GetP0().x > 1 && t.GetP1().x > 1 && t.GetP2().x > 1)
				continue;
			if(t.GetP0().y < -1 && t.GetP1().y < -1 && t.GetP2().y < -1)
				continue;
			if(t.GetP0().y > 1 && t.GetP1().y > 1 && t.GetP2().y > 1)
				continue;

			if(t.GetP0().z < 0 || t.GetP0().z > 1)
				continue;
			
			if(t.GetP1().z < 0 || t.GetP1().z > 1)
				continue;
			
			if(t.GetP2().z < 0 || t.GetP2().z > 1)
				continue;
			t.CalculateBoundingBox();
			activeTris.add(t);
		}
		return activeTris;
	}
	public void Render()
	{
		long startTime = System.currentTimeMillis();
		int screenWidth = getWidth();
		int screenHeight = getHeight();
		if(screenWidth == 0 || screenHeight == 0)
			return;
		BufferedImage nova = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
		Graphics ng = nova.getGraphics();
		ng.setColor(Color.black);
		ng.fillRect(0, 0, screenWidth, screenHeight);
		final int[] pixels = ((DataBufferInt) nova.getRaster().getDataBuffer()).getData();
		double[] zDepth = new double[pixels.length];
		for(int i = 0; i < zDepth.length; i++)
			zDepth[i] = Integer.MAX_VALUE;
		
		Vec3 cameraPosition = new Vec3(lX * LocalMap.METER_DIM, lY * LocalMap.METER_DIM, altitude);
		Vec3 upwards = new Vec3(0, 0, 1);
		Mat4 viewMat = Mat4.ViewMatrix(cameraPosition, viewDirection, upwards);
		Mat4 projMat = Mat4.ProjectionMatrix(fov, 1.0 * screenWidth / screenHeight, 1, 100000);
		
		Vec3 normalBuffer = new Vec3(0, 0, 0);
		Vec4 sunDirection = new Vec4(.354, .354, .866, 0);
		sunDirection.Normalize();
		Vec3 sun = new Vec3(sunDirection.x, sunDirection.y, sunDirection.z);
		projMat.postMultiply(sunDirection, sunDirection);
		ArrayList<TriangleMesh.Triangle> activeTriangles = PrepTriangles(1.0 * screenWidth / screenHeight, viewMat, projMat);
		for(TriangleMesh.Triangle t : activeTriangles)
		{
			int xS = (int) (t.xS * screenWidth / 2 + screenWidth / 2);
			int xE = (int) (t.xE * screenWidth / 2 + screenWidth / 2);
			int yE = (int) (screenHeight / 2 - t.yS * screenHeight / 2);
			int yS = (int) (screenHeight / 2 - t.yE * screenHeight / 2);
			
			double ax = (t.GetP0().x * screenWidth / 2 + screenWidth / 2);
			double bx = (t.GetP1().x * screenWidth / 2 + screenWidth / 2);
			double cx = (t.GetP2().x * screenWidth / 2 + screenWidth / 2);
			
			double ay = (screenHeight / 2 - t.GetP0().y * screenHeight / 2);
			double by = (screenHeight / 2 - t.GetP1().y * screenHeight / 2);
			double cy = (screenHeight / 2 - t.GetP2().y * screenHeight / 2);
			
			double az = t.GetP0().z;
			double bz = t.GetP0().z;
			double cz = t.GetP0().z;
			
			double abc = MathToolkit.SignedTriangleArea(ax, ay, bx, by, cx, cy);
			if(abc < 0)
				continue;
			
			
			int iEnd = Math.min(screenWidth - 1, xE);
			int jEnd = Math.min(screenHeight - 1, yE);
			for(int i = Math.max(0, xS); i <= iEnd; i++)
			{
				for(int j = Math.max(0, yS); j <= jEnd; j++)
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
					
					//double z = az * percA + bz * percB + cz * percC;
					//if(z > zDepth[j * screenWidth + i])
					//	continue;
					//zDepth[j * screenWidth + i] = z;
					
					int color = 255; //t.InterpolateColor(percA, percB, percC);
					/*t.InterpolateNormal(percA, percB, percC, normalBuffer);
					double dot = sun.Dot(normalBuffer);
					dot = Math.max(0, dot);
					int baseColor = MathToolkit.SmoothColorLerp(0, color, 0.4);
					color = MathToolkit.SmoothColorLerp(baseColor, color, dot);*/
					pixels[j * screenWidth + i] = color;
				}
			}
		}
		
		
		long deltaTime = System.currentTimeMillis() - startTime;
		double deltaAverageSmoothness = 0.1;
		renderMovingAverage = (long) (1.0 * (1.0 - deltaAverageSmoothness) * renderMovingAverage + deltaAverageSmoothness * deltaTime);
		int fps = (int) (1000. / renderMovingAverage);
		ng.setColor(Color.black);
		ng.fillRect(0, 0, 30, 10);
		ng.setColor(Color.white);
		String msg = fps + " fps";
		ng.drawString(msg, 0, 10);
		
		Graphics g = this.getGraphics();
		if(g == null)
			return;
		g.drawImage(nova, 0, 0, null);

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
		if(keysDown.contains(KeyEvent.VK_A))
			theta += 2;

		if(keysDown.contains(KeyEvent.VK_D))
			theta -= 2;
		
		if(keysDown.contains(KeyEvent.VK_W))
			phi -= 2;
		
		if(keysDown.contains(KeyEvent.VK_S))
			phi += 2;
		
		if(phi > 80)
			phi = 80;
		if(phi < -80)
			phi = -80;
		if(theta < 0)
			theta += 360;
		if(theta > 360)
			theta -= 360;
		
		CalcViewDirection();
		
		if(keysDown.contains(KeyEvent.VK_UP))
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
		if(keysDown.contains(KeyEvent.VK_DOWN))
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
		if(keysDown.contains(KeyEvent.VK_RIGHT))
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
		if(keysDown.contains(KeyEvent.VK_LEFT))
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
		double minHeight = area.GetHeight(lX, lY) + 50;
		if(altitude < minHeight)
			altitude = minHeight;
	}
	private class ViewAdjuster implements KeyListener
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
		
	}
}