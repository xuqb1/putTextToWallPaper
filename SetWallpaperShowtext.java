
// GoogleWallpaper.java
// Andrew Davison, May 2010, ad@fivedots.coe.psu.ac.th

/* Load a series of search words from a file supplied on the
   command line, and randomly choose one to  use as a query to 
   Google's image search.

   The search results are listed (description and URL), and one is 
   randomly selected. The URL's image is downloaded and
   saved as a local BMP file (in WALL_FNM). The image may be
   scaled and cropped to better fit the screen size.

   Then JNA (https://jna.dev.java.net/) is used to update the Win32 registry's
   wallpaper information, and to refresh the desktop without requiring
   a system reboot.
*/

import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.imageio.*;
import java.util.*;
import java.net.*;
import java.io.*;

import org.json.*;    // used to examine the search results

import com.sun.jna.*;
import com.sun.jna.win32.*;
import com.sun.jna.platform.win32.*;



public class SetWallpaperShowtext 
{
  private static final String DEFAULT_SEARCH = "nature"; 
                // used as the search word if no words file is available

  private static final int MAX_TRIES = 5;     // max number of times to try download an image
  private static final String WALL_FNM = "wallpaper.png";    // the name of the wallpaper file

  private static Random rand;    // for selecting a wallpaper at random
  private static double screenWidth, screenHeight;  // for resizing the wallpaper

  static int FONTSIZE=40;
  //文本内容
  public static void main(String args[])
  {
    if (args.length == 0) {
      System.err.println("need text content");
      System.exit(1);
    }
    String text=args[0];
    Font font=new Font("微软雅黑", Font.ROMAN_BASELINE, FONTSIZE);
    int fontSize=font.getSize();
    float realWidth=getRealFontWidth(text);
    float width=fontSize*realWidth;//text.length();
    String[] tmpArr=text.split("\\\\n");
    float height=fontSize;//(fontSize+5)*tmpArr.length;
    BufferedImage img=makeImage(text, (int)width, (int)height);
    if (img != null) {
      try{
        ImageIO.write(img, "png", new File(System.getProperty("user.dir")+"\\"+WALL_FNM));
        //ImageIO.write(img, "bmp", new File(System.getProperty("user.dir")+"\\"+WALL_FNM));
      }catch(IOException e){
        e.printStackTrace();
      }
      installWallpaper(WALL_FNM);   // make WALL_FNM the new desktop wallpaper
    }
  }

  public static BufferedImage makeImage( String text, int width, int height){
    BufferedImage buffImg=new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Font font=new Font("Dialog", Font.ROMAN_BASELINE, FONTSIZE);
    String[] tmpArr=text.split("\\\\n");
    Graphics2D g2d=buffImg.createGraphics();
    buffImg=g2d.getDeviceConfiguration().createCompatibleImage(width, height*tmpArr.length, Transparency.TRANSLUCENT);
    g2d.dispose();
    g2d=buffImg.createGraphics();
    g2d.setColor(Color.WHITE);
    g2d.fillRect(0, 0, width, height*tmpArr.length);
    g2d.setColor(Color.BLACK);
    g2d.setFont(font);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    for(int i=0;i<tmpArr.length;i++){
      g2d.drawString(tmpArr[i], 5f, (float)height*(i+1)-5.0f);
    }
    g2d.dispose();
    return buffImg;
  }
  private static float getRealFontWidth(String text){
    int len=text.length();
    String[] tmpArr=text.split("\\\\n");
    float width=0f;
    for(int i=0;i<tmpArr.length;i++){
      len=tmpArr[i].length();
      float w=0f;
      for(int j=0;j<len;j++){
        if(tmpArr[i].charAt(j)<256){
          w+=0.5f;
        }else{
          w+=1.0f;
        }
      }
      if(w>width){
        width=w;
      }
    }
    return width;
  }
  
  private static String selectSearchWord(String wordsFnm){
    ArrayList<String> words = loadSearchWords(wordsFnm);
    if (words.size() == 0)
      return DEFAULT_SEARCH;

    int idx = rand.nextInt(words.size());
    return words.get(idx);
  }  // end of selectSearchWord()

  /* The search-words file format are lines of:
        <search word/phrase>  
     and blank lines and comment lines.
     Return the words as a list.
  */
  private static ArrayList<String> loadSearchWords(String wordsFnm){ 
    ArrayList<String> words = new ArrayList<String>();
    System.out.println("Reading file: " + wordsFnm);
    try {
      BufferedReader br = new BufferedReader( new FileReader(wordsFnm));
      StringTokenizer tokens;
      String line, name, fnm;
      while((line = br.readLine()) != null) {
        line = line.toLowerCase().trim();
        if (line.length() == 0)  // blank line
          continue;
        if (line.startsWith("//"))   // comment
          continue;
        words.add(line);
      }
      br.close();
    } 
    catch (IOException e) 
    { System.out.println("Error reading file: " + wordsFnm); }

    return words;
  }  // end of loadSearchWords()

  /* Query Google's image search for "phrase" (a word or words), getting back a 
     list of image URLs in JSON format.

     The query uses Google's AJAX Search API and its REST-based interface. 
     For details, see
        http://code.google.com/apis/ajaxsearch/documentation/reference.html#_intro_fonje
  */
  private static JSONObject imageSearch(String phrase){
    System.out.println("Searching Google Image for \"" + phrase + "\"");
    try {
      // Convert spaces to +, etc. to make a valid URL
      String uePhrase = URLEncoder.encode("\"" + phrase + "\"", "UTF-8"); 
      String urlStr = "http://ajax.googleapis.com/ajax/services/search/images?v=1.0" +
                        "&q=" + uePhrase +
                        "&rsz=large" +      // at most 8 results
                        "&imgtype=photo" +  // want photo images
                        "&imgc=color" +     // in colour
                        "&safe=images" +    // Use moderate filtering
                        "&imgsz=l" ;        // request large images

      String jsonStr = WebUtils.webGetString(urlStr);   // get response as a JSON string
      return new JSONObject(jsonStr);
    }catch (Exception e){ 
      System.out.println(e);  
      System.exit(1);
    }
    return null;
  }
  /* list the image URLs returned by Google, then try to download one
     returning it as a BufferedImage */
  private static BufferedImage selectImage(JSONObject json){
    try {
      // WebUtils.saveString("temp.json", json.toString(2) );    // save, and indent the string
               // useful for debugging

      System.out.println("\nTotal no. of possible results: " +
                      json.getJSONObject("responseData")
                          .getJSONObject("cursor")
                          .getString("estimatedResultCount") + "\n");
                          
      // list the search results and then download one of their images
      JSONArray jaResults = json.getJSONObject("responseData").getJSONArray("results");
      showResults(jaResults);
      if (jaResults.length() > 0)
        return tryDownloadingImage(jaResults);
    }
    catch (JSONException e) 
    { System.out.println(e);  
      System.exit(1);
    }

    return null;
  } // end of selectImage()

  // for each result, list its contents title and URL
  private static void showResults(JSONArray jaResults) throws JSONException{
    for (int i = 0; i < jaResults.length(); i++) {
      System.out.print((i+1) + ". ");
      JSONObject j = jaResults.getJSONObject(i);
      String content = j.getString("contentNoFormatting");
      String cleanContent = content.replaceAll("[^a-zA-Z0-9]", " ").
                                    replaceAll("\\s+", " ").
                                    trim();
           // replace non-alphanumerics with spaces; remove multiple spaces
      System.out.println("Content: " + cleanContent);   
      System.out.println("       URL: "+ j.getString("url") + "\n");
    }
  }  // end of showResults()


  /* Download an image chosen at random from the list returned by Google.
     This is complicated by the possibility that a URL is unavailable. In that
     case the method tries again, hopefully with a different image, 
     for up to MAX_TRIES time.
  */
  private static BufferedImage tryDownloadingImage(JSONArray jaResults) throws JSONException{
    BufferedImage im = null;
    for(int i=0; i < MAX_TRIES; i++) {
      int idx = rand.nextInt(jaResults.length());    // select an image index at random
      System.out.println("Randomly selected no. " + (idx+1));
      String imUrlStr = jaResults.getJSONObject(idx).getString("url");   // get its URL
      im = getURLImage(imUrlStr);    // download the URL (maybe)
      if (im != null)
        return im;
    }

    // should not get here unless there's a problem
    System.out.println("No suitable image found");
    return im;
  }  // end of tryDownloadingImage
  

  // download the image at urlStr
  private static BufferedImage getURLImage(String urlStr){
    System.out.println("Downloading image at:\n\t" + urlStr);
    BufferedImage image = null;
    try {
      image = ImageIO.read( new URL(urlStr) );
    } 
    catch (IOException e) 
    {  System.out.println("Problem downloading");  }
    
    return image;
  }  // end of getURLImage()


  /* Scale the image either horizontally or vertically
     depending on which screen-dimension/image-dimension ratio is larger, so the image
     becomes as large as the screen in one dimension and maybe bigger in the other
     dimension.
  */
  private static BufferedImage scaleImage(BufferedImage im){
    int imWidth = im.getWidth();
    int imHeight = im.getHeight();
    // calculate screen-dimension/image-dimension for width and height
    double widthRatio = screenWidth/(double)imWidth;
    double heightRatio = screenHeight/(double)imHeight;

    double scale = (widthRatio > heightRatio) ? widthRatio : heightRatio;
         // scale is the largest screen-dimension/image-dimension

    // calculate new image dimensions which fit the screen (or makes the image bigger)
    int scWidth = (int)(imWidth*scale);
    int scHeight = (int)(imHeight*scale);

    // resize the image
    BufferedImage scaledImage = new BufferedImage(scWidth, scHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = scaledImage.createGraphics();
    AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
    g2d.setRenderingHint( RenderingHints.KEY_INTERPOLATION,
                          RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2d.drawImage(im, at, null);
    g2d.dispose();

    return scaledImage;
  }  // end of scaleImage()



  /* Check which image dimension (width or height) is bigger than the
     screen, and crop it. Only one dimension, or none, will be too big.
  */
  private static BufferedImage cropImage(BufferedImage scIm){
    int imWidth = scIm.getWidth();
    int imHeight = scIm.getHeight();

    BufferedImage croppedImage;
    if (imWidth > screenWidth) {     // image width is bigger than screen width
      // System.out.println("Cropping the width");
      croppedImage = new BufferedImage((int)screenWidth, imHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2d = croppedImage.createGraphics();
      g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
      int x = ((int)screenWidth - imWidth)/2;    // crop so image center remains in the center
      g2d.drawImage(scIm, x, 0, null);
      g2d.dispose();
    }
    else if (imHeight > screenHeight) {  // image height is bigger than screen height
      // System.out.println("Cropping the height");
      croppedImage = new BufferedImage(imWidth, (int)screenHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2d = croppedImage.createGraphics();
      g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
      int y = ((int)screenHeight - imHeight)/2;     // crop so image center remains in the center
      g2d.drawImage(scIm, 0, y, null);
      g2d.dispose();
    }else   // do nothing
      croppedImage = scIm;

    // System.out.println("Cropped Image (w, h): (" + croppedImage.getWidth() + ", " +
    //                                               croppedImage.getHeight() + ")");
    return croppedImage;
  }  // end of cropImage()



  // save the image as a BMP in <fnm>
  private static void saveBMP(String fnm, BufferedImage im){
    System.out.println("Saving image to " + fnm);
    try {
      ImageIO.write(im, "bmp", new File(fnm));
    }
    catch (IOException e)
    {  System.out.println("Could not save file");  }
  }  // end of saveBMP()




  private static void installWallpaper(String fnm)
  /* Wallpaper installation requires three changes to thw Win32 
     registry, and a desktop refresh. The basic idea (using Visual C# and VB)
     is explained in "Setting Wallpaper" by Sean Campbell:
         http://blogs.msdn.com/coding4fun/archive/2006/10/31/912569.aspx
  */
  {
    try {
      String fullFnm = new File(".").getCanonicalPath() + "\\" + fnm;
      System.out.println("Full fnm: " + fullFnm);

      /* 3 registry key changes to HKEY_CURRENT_USER\Control Panel\Desktop
         These three keys (and many others) are explained at
            http://www.virtualplastic.net/html/desk_reg.html

         List of registry functions at MSDN:
                http://msdn.microsoft.com/en-us/library/ms724875(v=VS.85).aspx
      */
      Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, 
                                  "Control Panel\\Desktop", "Wallpaper", fullFnm);
      //Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, 
      Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, 
                                  "Control Panel\\Desktop", "WallpaperStyle", "0");  // no stretching
      //Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, 
      Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, 
                                  "Control Panel\\Desktop", "TileWallpaper", "0");   // no tiling

      // refresh the desktop using User32.SystemParametersInfo(), so avoiding an OS reboot
      int SPI_SETDESKWALLPAPER = 0x14;
      int SPIF_UPDATEINIFILE = 0x01;
      int SPIF_SENDWININICHANGE = 0x02;

      boolean result = MyUser32.INSTANCE.SystemParametersInfoA(SPI_SETDESKWALLPAPER, 0, 
                                fullFnm, SPIF_UPDATEINIFILE | SPIF_SENDWININICHANGE );
      System.out.println("Refresh desktop result: " + result);
    }
    catch(IOException e) 
    {  System.out.println("Could not find directory path");  }
  }  // end of installWallpaper()


 // ---------------------------------------------


  private interface MyUser32 extends StdCallLibrary 
  /* JNA Win32 extensions includes a User32 class, but it doesn't contain
     SystemParametersInfo(), so it must be defined here.

     MSDN libary docs on SystemParametersInfo() are at:
           http://msdn.microsoft.com/en-us/library/ms724947(VS.85).aspx

      BOOL WINAPI SystemParametersInfo(
          __in     UINT uiAction,
          __in     UINT uiParam,
          __inout  PVOID pvParam,
          __in     UINT fWinIni );

     When uiAction == SPI_SETDESKWALLPAPER, SystemParametersInfo() sets the desktop wallpaper. 
     The value of the pvParam parameter determines the new wallpaper. 
  */
  {
     MyUser32 INSTANCE = (MyUser32) Native.loadLibrary("user32", MyUser32.class);

     boolean SystemParametersInfoA(int uiAction, int uiParam, String fnm, int fWinIni);
                // SystemParametersInfoA() is the ANSI name used in User32.dll
  }  // end of MyUser32 interface



 } // end of GoogleWallpaper class

