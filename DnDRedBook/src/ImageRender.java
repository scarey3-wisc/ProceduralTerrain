import java.awt.image.BufferedImage;

public class ImageRender extends BufferedImage
{
	private boolean renderReady;
	public ImageRender(int width, int height, int imageType) {
		super(width, height, imageType);
		renderReady = false;
	}
	public boolean IsReadyToRender()
	{
		return renderReady;
	}
	public void FinishRendering()
	{
		renderReady = true;
	}
}