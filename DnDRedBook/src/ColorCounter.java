import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.imageio.ImageIO;

public class ColorCounter
{
	public static void main(String[] args)
	{
		try {
			BufferedImage img = ImageIO.read(new File("Test.jpg"));
			
			
			TreeMap<Integer, Integer> colorCounter = new TreeMap<Integer, Integer>();
			
			long totalR = 0;
			long totalG = 0;
			long totalB = 0;
			long numPixels = img.getWidth() * img.getHeight();
			for(int i = 0; i < img.getWidth(); i++)
			{
				for(int j = 0; j < img.getHeight(); j++)
				{
					int rgb = img.getRGB(i, j);
					if(colorCounter.containsKey(rgb))
					{
						int currValue = colorCounter.get(rgb);
						currValue++;
						colorCounter.put(rgb, currValue);
					}
					else
						colorCounter.put(rgb, 1);
					
					Color c = new Color(rgb);
					totalR += c.getRed();
					totalG += c.getGreen();
					totalB += c.getBlue();
				}
			}
			System.out.println(colorCounter.size());
			
			int averageR = (int) (totalR / numPixels);
			int averageG = (int) (totalG / numPixels);
			int averageB = (int) (totalB / numPixels);
			
			System.out.println("The average color; Red: " + averageR + ", Green: " + averageG + ", Blue: " + averageB);
			
			TreeMap<Integer, Integer> colorCounts = new TreeMap<Integer, Integer>();
			for(int rgb : colorCounter.keySet())
			{
				int num = colorCounter.get(rgb);
				colorCounts.put(num, rgb);
			}
			for(int i = 0; i < 10; i++)
			{
				int num = colorCounts.lastKey();
				int rgb = colorCounts.get(num);
				colorCounts.remove(num);
				Color c = new Color(rgb);
				System.out.print("Item number " + (i + 1) + " appears " + num + " times; ");
				System.out.print("Red: " + c.getRed() + ", Green: " + c.getGreen() + ", Blue: " + c.getBlue());
				System.out.println();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}