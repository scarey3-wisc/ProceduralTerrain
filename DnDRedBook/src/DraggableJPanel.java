import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;

public class DraggableJPanel extends JPanel
{
	private DragUpdater drag;
	public void StartListeningToDrag()
	{
		StopListeningToDrag();
		drag = new DragUpdater();
		addMouseListener(drag);
		addMouseMotionListener(drag);
	}
	public void StopListeningToDrag()
	{
		if(drag != null)
		{
			removeMouseListener(drag);
			removeMouseMotionListener(drag);
			drag = null;
		}
	}
	private static final long serialVersionUID = 1L;
	protected double dX, dY;
	private class DragUpdater implements MouseListener, MouseMotionListener
	{
		private double savedDX, savedDY;
		private int clickedX, clickedY;
		private boolean isDragging;
		public DragUpdater()
		{
			savedDX = 0;
			savedDY = 0;
			isDragging = false;
		}
		@Override
		public void mouseDragged(MouseEvent e) 
		{
			if(isDragging)
			{
				int deltaX = e.getX() - clickedX;
				int deltaY = e.getY() - clickedY;
				dX = savedDX + deltaX;
				dY = savedDY + deltaY;
			}
			DraggableJPanel.this.requestFocusInWindow();
		}

		@Override
		public void mouseMoved(MouseEvent e) {}

		@Override
		public void mouseClicked(MouseEvent e) {}

		@Override
		public void mousePressed(MouseEvent e) 
		{
			if(e.getButton() == MouseEvent.BUTTON1)
			{
				savedDX = dX;
				savedDY = dY;
				clickedX = e.getX();
				clickedY = e.getY();
				isDragging = true;
			}
			DraggableJPanel.this.requestFocusInWindow();
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			isDragging = false;
		}

		@Override
		public void mouseEntered(MouseEvent e) 
		{
			isDragging = (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
		}

		@Override
		public void mouseExited(MouseEvent e) 
		{
			isDragging = false;
		}
		
	}
}