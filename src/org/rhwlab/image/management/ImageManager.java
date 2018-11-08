package org.rhwlab.image.management;

import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import org.rhwlab.image.ParsingLogic.ImageNameLogic;
import java.io.File;

/**
 * @javadoc
 * ImageManager.java
 * @author Braden Katzman
 * @author bradenkatzman@gmail.com
 * Date created: 10/05/18
 *
 * This class is a top level manager that is responsible for initializing and maintaining
 * an image dataset. Functionality:
 *
 * - Manage image parameters, configurations and variables by maintaining
 *   the ImageConfig class
 *
 * - Delegate file parsing and name logic operations to ImageFileParser and ImageNameLogic
 *
 * - Handle image flipping and splitting
 *
 * - Manage the ImageWindow state by refreshing its display
 */
public class ImageManager {

    // the main configuration data for the image series
    private ImageConfig imageConfig;

    // runtime variables that are used to manage the image series data as it's viewed by the user (time, plane number, image height, etc.)
    private ImagePlus currentImage;
    private int currentImageTime;
    private int currentImagePlane;
    private int imageHeight;
    private int imageWidth;

    private static boolean setOriginalContrastValues; // not quite sure what this is used for
    private static int contrastMin1, contrastMin2, contrastMax1, contrastMax2, contrastMin3, contrastMax3;
    private static final int MAX8BIT = 255, MAX16BIT = 65535;

    public ImageManager(ImageConfig imageConfig) {
        this.imageConfig = imageConfig;

        // Original contrast percentages
        contrastMin1 = contrastMin2 = contrastMin3 = 0;
        if (imageConfig.getUseStack() == 1) {
            contrastMax1 = contrastMax2 = contrastMax3 = MAX16BIT;
        } else {
            contrastMax1 = contrastMax2 = contrastMax3 = MAX8BIT;
        }


        // avoid errors by setting some default values
        this.currentImageTime = 1;
        this.currentImagePlane = 15; // usually about the middle of the stack
        this.setOriginalContrastValues = true;
    }

    // methods to set runtime parameters
    public void setCurrImageTime(int time) {
        this.currentImageTime = time;
    }
    public int getCurrImageTime() { return this.currentImageTime; }

    public void setCurrImagePlane(int plane) {
        this.currentImagePlane = plane;
    }
    public int getCurrImagePlane() { return this.currentImagePlane; }

    public void setCurrImage(ImagePlus currImg) { this.currentImage = currImg; }
    public ImagePlus getCurrentImage() { return this.currentImage; }

    // methods for runtime updates
    public void incrementImageTimeNumber(int timeIncrement) {
        if (timeIncrement > 0
                && this.currentImageTime + timeIncrement < this.imageConfig.getEndingIndex()) {
            this.currentImagePlane += timeIncrement;
        } else if (timeIncrement < 0
                    && this.currentImageTime + timeIncrement > 1) {
            this.currentImageTime += timeIncrement;
        }
    }

    public void incrementImagePlaneNumber(int planeIncrement) {
        if (planeIncrement > 0
                && this.currentImagePlane + planeIncrement <= this.imageConfig.getPlaneEnd()) {
            this.currentImagePlane += planeIncrement;
        }
        else if (planeIncrement < 0
                    && this.currentImagePlane + planeIncrement > 1) {
            this.currentImagePlane += planeIncrement;
        }
    }

    /**
     * Called by AceTree.java to bring up the image series
     *
     * @return the first image in the series (configured by the user - not necessarily time 1)
     */
    public ImagePlus bringUpImageSeries() {

        // first thing we need to check if whether multiple image files (corresponding to different color channels) were provided in the config file
        // these two conditions are the result of the two conventions for supplying an <image> tag in the XML file. See documentation or ImageConfig.java
        if (!imageConfig.areMultipleImageChannelsGiven()) {
            // only one file was provided --> let's see if it exists
            String imageFile = imageConfig.getProvidedImageFileName();
            if(!new File(imageFile).exists()) {
                System.out.println("The image listed in the config file does not exist on the system. Checking if it's an 8bit image that no longer exists");

                // it doesn't exist. It's likely an 8bit image file name that no longer exists, so let's do a check on the
                // file type first (not completely reliable check) and if it's 8bit, we'll try and find a 16bit image
                if (ImageNameLogic.is8bitImage(imageFile)) {
                    System.out.println("The image has an 8bit file naming convention -> try and find it's 16bit corollary");
                    String newFileNameAttempt = ImageNameLogic.reconfigureImagePathFrom8bitTo16bit(imageFile);
                    if (!newFileNameAttempt.equals(imageFile)) {
                        System.out.println("A 16bit file name was generated from the 8bit image file name in the config file. Checking if it exists");
                        if (new File(newFileNameAttempt).exists()) {
                            System.out.println("16bit image file exists. Updating file in ImageConfig to: " + newFileNameAttempt);
                            this.imageConfig.setProvidedImageFileName(newFileNameAttempt);
                            this.imageConfig.setImagePrefixes();

                            // because the image series is now known to be 16bit stacks, set the use stack flag to 1
                            this.imageConfig.setUseStack(1);

                            return makeImageFromSingle16BitTIF(newFileNameAttempt);
                        } else {
                            System.out.println("16bit image file name generated from 8bit image file name does not exist on the system. Can't bring up image series.");
                            return null;
                        }
                    } else {
                        System.out.println("Attempt to generate 16bit image file name from 8bit image file name failed. Can't bring up image series");
                        return null;
                    }
                }
            } else {
                // if we've reached here, either the supplied file exists, or a 16bit corollary was found and we will now proceed with that
                if (ImageNameLogic.is8bitImage(imageFile)) {
                    // load this image as the first in the image series
                    this.imageConfig.setUseStack(0); // in case it isn't correctly set
                    return makeImageFrom8Bittif(imageFile);

                } else {
                    // we now want to check whether this image file follows the iSIM or diSPIM data hierarchy conventions. If so,
                    // we'll take advantage of that knowledge and look for other files in the series

                    // check if a second color channel can be found if we assume the iSIM data output hierarchy and format
                    String secondColorChannelFromiSIM = ImageNameLogic.findSecondiSIMColorChannel(imageFile);
                    if (!secondColorChannelFromiSIM.isEmpty()) {
                        //System.out.println("ImageManager found second channel stack by assuming iSIM data structure. Loading both channels...");
                        this.imageConfig.setUseStack(1);

                        // we need to add this second color channel to the image config so that its prefix will be maintained
                        this.imageConfig.addColorChannelImageToConfig(secondColorChannelFromiSIM);

                        // because we have the full paths in this instance, we'll call the makeImage method directly with the names. During runtime,
                        // as the user changes images, this will need to first be piped through a method to query the prefixes from ImageConfig and
                        // append the desired time
                        return makeImageFromMultiple16BitTIFs(new String[]{imageFile, secondColorChannelFromiSIM});

                    }

                    // check if a second color channel can be found is we assume the diSPIM data output hierarchy and format
                    String secondColorChannelFromdiSPIM = ImageNameLogic.findSecondDiSPIMColorChannel(imageFile);
                    if (!secondColorChannelFromdiSPIM.isEmpty()) {
                        //System.out.println("ImageManager found second channel stack by assuming diSPIM data structure. Loading both channels...");
                        this.imageConfig.setUseStack(1);

                        // add the second color channel to the image config so that its prefix will be maintained
                        this.imageConfig.addColorChannelImageToConfig(secondColorChannelFromdiSPIM);

                        // call the makeImage method directory with the image names
                        return makeImageFromMultiple16BitTIFs(new String[]{imageFile, secondColorChannelFromdiSPIM});
                    }

                    // if none of the above options produced a second image file containing the second color channel, we'll assume that the supplied image is a
                    // stack that contains all color channels in it
                    this.imageConfig.setUseStack(1);
                    return makeImageFromSingle16BitTIF(imageFile);
                }
            }
        } else {
            if (imageConfig.getNumChannels() > 3) {
                System.out.println("WARNING: More than three image channels were supplied in the .XML file. At this point," +
                        "AceTree only supports viewing 3 channels. All image file names " +
                        "will be loaded, but only the first three will be processed and displayed.");
            }

            // multiple images were provided in the config file. we need to query them slightly differently and then check if they exist
            String[] images = imageConfig.getImageChannels();
            return makeImageFromMultiple16BitTIFs(images);
        }

        System.out.println("ImageManager.bringUpImageSeries reached code end. Returning null.");
        return null;
    }

    /**
     *
     * @param tif_8bit
     * @return
     */
    private ImagePlus makeImageFrom8Bittif(String tif_8bit) {
        ImagePlus ip = new Opener().openImage(tif_8bit); // no need for other arguments, the file is just a single plane at a single timepoint
        if (ip != null) {
            this.imageWidth = ip.getWidth();
            this.imageHeight = ip.getHeight();

            ip = ImageConversionManager.convert8bittifToRGB(ip, this.imageConfig);
        } else {
            ip = new ImagePlus();
            ImageProcessor iproc = new ColorProcessor(this.imageWidth, this.imageHeight);
            ip.setProcessor(tif_8bit, iproc);
        }

        return ip;
    }

    /**
     *
     * @param TIF_16bit - may include one or more color channels
     * @return
     */
    private ImagePlus makeImageFromSingle16BitTIF(String TIF_16bit) {
        ImagePlus ip = new Opener().openImage(TIF_16bit, this.currentImagePlane);

        if (ip != null) {
            this.imageWidth = ip.getWidth();
            this.imageHeight = ip.getHeight();

            ip = ImageConversionManager.convertSingle16BitTIFToRGB(ip, this.imageConfig);
        } else {
            ip = new ImagePlus();
            ImageProcessor iproc = new ColorProcessor(this.imageWidth, this.imageHeight);
            ip.setProcessor(TIF_16bit, iproc);
        }

        return ip;
    }

    /**
     *
     * @param TIFs_16bit_names it is assumed that each TIF represents a different color channel for the image series
     * @return
     */
    private ImagePlus makeImageFromMultiple16BitTIFs(String[] TIFs_16bit_names) {
        ImagePlus[] TIFs_16bit = new ImagePlus[TIFs_16bit_names.length];

        for (int i = 0; i < TIFs_16bit_names.length; i++) {
            TIFs_16bit[i] = new Opener().openImage(TIFs_16bit_names[i], this.currentImagePlane);
            if (TIFs_16bit[i] == null) {
                System.err.println("Couldn't make image from: " + TIFs_16bit_names[i]);
                return null;
            }
        }

        this.imageWidth = TIFs_16bit[0].getWidth();
        this.imageHeight = TIFs_16bit[0].getHeight();

        return ImageConversionManager.convertMultiple16BitTIFsToRGB(TIFs_16bit, this.imageConfig);
    }

    /**
     * This is the method called from AceTree during runtime when the UI is triggered to update the images
     * e.g. when the user changes the time/plane
     *
     * @param time
     * @param plane
     * @return
     */
    public ImagePlus makeImage(int time, int plane) {
        // first check if we're dealing with 8 bit or 16 bit images
        if (this.imageConfig.getUseStack() == 0) { // 8bit
            return makeImageFrom8Bittif(ImageNameLogic.appendTimeAndPlaneTo8BittifPrefix(this.imageConfig.getImagePrefixes()[0], time, plane));

        } else if (this.imageConfig.getUseStack() == 1) { //16bit
            // check if there are multiple stacks defining the color channels of the image series, or if all channels are contained in a single stack
            if (this.imageConfig.getNumChannels() == -1) {
                // single stack with one or more color channels
                return makeImageFromSingle16BitTIF(ImageNameLogic.appendTimeToSingle16BitTIFPrefix(this.imageConfig.getImagePrefixes()[0], time));
            } else if (this.imageConfig.getNumChannels() > 1) {
                // multiple stacks containing multiple image channels for an image series
                return makeImageFromMultiple16BitTIFs(ImageNameLogic.appendTimeToMultiple16BitTifPrefixes(this.imageConfig.getImagePrefixes(), time));
            }
        }

        System.out.println("Couldn't determine if using 8bit or 16bit images in ImageManager.makeImage(), returning null. useStack: " + this.imageConfig.getUseStack());
        return null;
    }

    // accessors and mutators for static variables
    public static void setOriginContrastValuesFlag(boolean OCVF) { setOriginalContrastValues = OCVF; }
    public static void setContrastMin1(int cMin1) { contrastMin1 = cMin1; }
    public static void setContrastMin2(int cMin2) { contrastMin2 = cMin2; }
    public static void setContrastMin3(int cMin3) { contrastMin3 = cMin3; }
    public static void setContrastMax1(int cMax1) { contrastMax1 = cMax1; }
    public static void setContrastMax2(int cMax2) { contrastMax2 = cMax2; }
    public static void setContrastMax3(int cMax3) { contrastMax3 = cMax3; }
    public static boolean getOriginalContrastValuesFlag() { return setOriginalContrastValues; }
    public static int getContrastMin1() { return contrastMin1; }
    public static int getContrastMin2() { return contrastMin2; }
    public static int getContrastMin3() { return contrastMin3; }
    public static int getContrastMax1() { return contrastMax1; }
    public static int getContrastMax2() { return contrastMax2; }
    public static int getContrastMax3() { return contrastMax3; }
}