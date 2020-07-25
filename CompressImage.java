
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import java.util.Arrays;


public class CompressImage {
    JFrame frame;
    JLabel lbIm1;
    BufferedImage imgOne;
    BufferedImage decImg;
    int width = 1920;
    int height = 1080;
    int xBlocks = width/8;
    int yBlocks = height/8;

    final double pi = 3.1415926;
    double Cu;
    double Cv;
    double sumR, sumG, sumB;


    int quantizeLevel;
    int deliveryMode;
    int latency;

    /** Read Image RGB
     *  Reads the image of given width and height at the given imgPath into the provided BufferedImage.
     */
    private void readImageRGB(int width, int height, String imgPath, BufferedImage img, int quantizeLevel, int deliveryMode, int latency)
    {
        try
        {
            int frameLength = width*height*3;

            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);

            long len = frameLength;
            byte[] bytes = new byte[(int) len];

            raf.read(bytes);

            // Store original pixels for display.
            // Store channels for manipulation.
            int[][] oriPixels = new int[width][height];
            int[][] rChan = new int[width][height];
            int[][] gChan = new int[width][height];
            int[][] bChan = new int[width][height];
            int ind = 0;
            for(int y = 0; y < height; y++)
            {
                for(int x = 0; x < width; x++)
                {
                    byte a = 0;
                    byte r = bytes[ind];
                    byte g = bytes[ind+height*width];
                    byte b = bytes[ind+height*width*2];

                    rChan[x][y] = r & 0xff;
                    gChan[x][y] = g & 0xff;
                    bChan[x][y] = b & 0xff;

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    //int pix = ((a << 24) + (r << 16) + (g << 8) + b);
                    oriPixels[x][y] = pix;
                    img.setRGB(x,y,pix);
                    ind++;
                }
            }

            FDCTandQuantize(rChan, gChan, bChan, quantizeLevel, deliveryMode, latency);
        }
        catch (FileNotFoundException e) 
        {
            e.printStackTrace();
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }

    private void FDCTandQuantize(int[][] rChan, int[][] gChan, int[][] bChan, int quantizeLevel, int deliveryMode, int latency) {
        // Divide Channels to 8x8 blocks


        int[][][][] rChanBlocks = new int[xBlocks][yBlocks][8][8];
        int[][][][] gChanBlocks = new int[xBlocks][yBlocks][8][8];
        int[][][][] bChanBlocks = new int[xBlocks][yBlocks][8][8];

        for (int by = 0; by < yBlocks; by++) {
            for (int bx = 0; bx < xBlocks; bx++) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        rChanBlocks[bx][by][x][y] = rChan[bx * 8 + x][by * 8 + y];
                        gChanBlocks[bx][by][x][y] = gChan[bx * 8 + x][by * 8 + y];
                        bChanBlocks[bx][by][x][y] = bChan[bx * 8 + x][by * 8 + y];
                    }
                }
            }
        }

        // Precalculate FDCT's cosine values for faster processing
        double[][][][] cosValFDCT= new double[8][8][8][8];
        for (int v=0; v<8; v++) {
            for (int u=0; u<8; u++) {
                for (int y=0; y<8; y++) {
                    for (int x = 0; x<8; x++) {
                        cosValFDCT[u][v][x][y] = Math.cos((2*x+1)*u*pi / 16) * Math.cos((2*y+1)*v*pi / 16);
                    }
                }
            }
        }

        // FDCT on all channels
        int[][][][] rDCT = new int[xBlocks][yBlocks][8][8];
        int[][][][] gDCT = new int[xBlocks][yBlocks][8][8];
        int[][][][] bDCT = new int[xBlocks][yBlocks][8][8];
        
        for (int by = 0; by < yBlocks; by++) {
            for (int bx = 0; bx < xBlocks; bx++) {
                for (int v = 0; v < 8; v++) {
                    for (int u = 0; u < 8; u++) {
                        Cu = 1;
                        Cv = 1;
                        if (u == 0) Cu = 1 / Math.sqrt(2);
                        if (v == 0) Cv = 1 / Math.sqrt(2);

                        sumR = 0;
                        sumG = 0;
                        sumB = 0;
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                sumR = sumR + rChanBlocks[bx][by][x][y] * cosValFDCT[u][v][x][y];
                                sumG = sumG + gChanBlocks[bx][by][x][y] * cosValFDCT[u][v][x][y];
                                sumB = sumB + bChanBlocks[bx][by][x][y] * cosValFDCT[u][v][x][y];
                            }
                        }

                        rDCT[bx][by][u][v] = (int) Math.round ( ((Cu*Cv)/4.0 * sumR) / quantizeLevel);
                        gDCT[bx][by][u][v] = (int) Math.round ( ((Cu*Cv)/4.0 * sumG) / quantizeLevel);
                        bDCT[bx][by][u][v] = (int) Math.round ( ((Cu*Cv)/4.0 * sumB) / quantizeLevel);
                        sumR = 0;
                        sumG = 0;
                        sumB = 0;
                    }
                }
            }
        }

        dequantizeAndIDCT(rDCT, gDCT, bDCT, quantizeLevel, deliveryMode, latency);

        // System.out.println(Arrays.deepToString(rChanBlocks[239][134]));
        // if (Arrays.deepEquals(invRDCT[0][0], rChanBlocks[0][0])) {
        //  System.out.println("fucking same");
        // }

    }

    private void dequantizeAndIDCT(int[][][][] rDCT, int[][][][] gDCT, int[][][][] bDCT, int quantizeLevel, int deliveryMode, int latency) {
        // Dequantize coefficients
        int[][][][] deQR = new int[xBlocks][yBlocks][8][8];
        int[][][][] deQG = new int[xBlocks][yBlocks][8][8];
        int[][][][] deQB = new int[xBlocks][yBlocks][8][8];
        for (int by = 0; by < yBlocks; by++) {
            for (int bx = 0; bx < xBlocks; bx ++) {
                for (int v = 0; v < 8; v++) {
                    for (int u = 0; u<8; u++) {
                        deQR[bx][by][u][v] = rDCT[bx][by][u][v] * quantizeLevel;
                        deQG[bx][by][u][v] = gDCT[bx][by][u][v] * quantizeLevel;
                        deQB[bx][by][u][v] = bDCT[bx][by][u][v] * quantizeLevel;
                    }
                }
            }
        }

        // Precalculate IDCT's cosine values for faster processing
        double[][][][] cosValIDCT= new double[8][8][8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                for (int v = 0; v < 8; v++) {
                    for (int u = 0; u < 8; u++) {
                        Cu = 1;
                        Cv = 1;
                        if (u == 0) Cu = 1 / Math.sqrt(2);
                        if (v == 0) Cv = 1 / Math.sqrt(2);
                        cosValIDCT[x][y][u][v] = Math.cos((2*x+1)*u*pi / 16) *
                                                 Math.cos((2*y+1)*v*pi / 16) * Cu * Cv;
                    }
                }
            }
        }

        decImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // make bufferedimage while to be more pleasing to eyes :)
        for (int y = 0; y<height; y++) {
            for (int x = 0; x<width; x++) {
                decImg.setRGB(x,y,0xffffffff);
            }
        }

        // Used in all mode 1,2,3 for storing IDCT
        int[][][][] invRDCT = new int[xBlocks][yBlocks][8][8];
        int[][][][] invGDCT = new int[xBlocks][yBlocks][8][8];
        int[][][][] invBDCT = new int[xBlocks][yBlocks][8][8];
        
        // Sequential Mode
        if (deliveryMode == 1) {
            // For all blocks
            for (int by = 0; by < yBlocks; by++) {
                for (int bx = 0; bx < xBlocks; bx++) {
                    // For all pixels
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            // sumR = 0;
                            // sumG = 0;
                            // sumB = 0;
                            // Summing up u,v
                            for (int v = 0; v < 8; v++) {
                                for (int u = 0; u < 8; u++) {
                                    sumR = sumR + deQR[bx][by][u][v] * cosValIDCT[x][y][u][v];
                                    sumG = sumG + deQG[bx][by][u][v] * cosValIDCT[x][y][u][v];
                                    sumB = sumB + deQB[bx][by][u][v] * cosValIDCT[x][y][u][v];
                                }
                            }

                            // Set pixel values
                            invRDCT[bx][by][x][y] = (int) Math.round (sumR/4.0);
                            invGDCT[bx][by][x][y] = (int) Math.round (sumG/4.0);
                            invBDCT[bx][by][x][y] = (int) Math.round (sumB/4.0);

                            // Avoiding out of RGB bound [0,255]
                            if (invRDCT[bx][by][x][y] > 255) invRDCT[bx][by][x][y] = 255;
                            if (invGDCT[bx][by][x][y] > 255) invGDCT[bx][by][x][y] = 255;
                            if (invBDCT[bx][by][x][y] > 255) invBDCT[bx][by][x][y] = 255;
                            if (invRDCT[bx][by][x][y] < 0) invRDCT[bx][by][x][y] = 0;
                            if (invGDCT[bx][by][x][y] < 0) invGDCT[bx][by][x][y] = 0;
                            if (invBDCT[bx][by][x][y] < 0) invBDCT[bx][by][x][y] = 0;

                            // Clear current sum for a pixel for next pixel to use
                            sumR = 0;
                            sumG = 0;
                            sumB = 0;

                            // Set bufferedimage for displaying
                            byte r = (byte) invRDCT[bx][by][x][y];
                            byte g = (byte) invGDCT[bx][by][x][y];
                            byte b = (byte) invBDCT[bx][by][x][y];
                            int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                            decImg.setRGB(bx * 8 + x, by * 8 + y, pix);
                        }
                    }
                    if (latency > 0) {
                        if (bx==0&&by==0) paintFrame(decImg);
                        else {
                            try {Thread.sleep(latency);} catch (InterruptedException e) {}
                            Icon icon = new ImageIcon(decImg);
                            lbIm1.setIcon(icon);
                            frame.repaint();
                        }
                    } else {
                        if (bx==0&&by==0) paintFrame(decImg);
                        Icon icon = new ImageIcon(decImg);
                        lbIm1.setIcon(icon);
                        frame.repaint();          
                    }
                }
            }
        }

        // Used for mode 2 and 3 for storing current approximation for all pixels
        double[][][][] sumRBlocks = new double[xBlocks][yBlocks][8][8];
        double[][][][] sumGBlocks = new double[xBlocks][yBlocks][8][8];
        double[][][][] sumBBlocks = new double[xBlocks][yBlocks][8][8];
        int iterCount = 0;

        // Spectral Selection Mode
        if (deliveryMode == 2) {
        	paintFrame(imgOne);
        	try {Thread.sleep(2000);} catch (InterruptedException e) {}
            // Find zigzig ordering for R,G,B channels, as well as precalculated cosine values
            int[][][][] reorderRinvDCT = zigzagIntReorder(deQR);
            int[][][][] reorderGinvDCT = zigzagIntReorder(deQG);
            int[][][][] reorderBinvDCT = zigzagIntReorder(deQB);
            double[][][][] reorderCosVal = zigzagDoubleReorder(cosValIDCT);
            
            // For all F(u,v)
            for (int v = 0; v < 8; v++) {
                for (int u = 0; u < 8; u++) {
                    // For all blocks
                    for (int by = 0; by < yBlocks; by++){
                        for (int bx = 0; bx < xBlocks; bx++) {
                            // For all pixels
                            for (int y = 0; y < 8; y++) {
                                for (int x = 0; x < 8; x++){
                                    // Approximate image using only current coefficient (i.e. sum)
                                    sumRBlocks[bx][by][x][y] = sumRBlocks[bx][by][x][y] 
                                                             + reorderRinvDCT[bx][by][u][v] 
                                                             * reorderCosVal[x][y][u][v];

                                    sumGBlocks[bx][by][x][y] = sumGBlocks[bx][by][x][y] 
                                                             + reorderGinvDCT[bx][by][u][v] 
                                                             * reorderCosVal[x][y][u][v];

                                    sumBBlocks[bx][by][x][y] = sumBBlocks[bx][by][x][y] 
                                                             + reorderBinvDCT[bx][by][u][v] 
                                                             * reorderCosVal[x][y][u][v];

                                    // Set pixel values
                                    invRDCT[bx][by][x][y] = (int) Math.round (sumRBlocks[bx][by][x][y]/4.0);
                                    invGDCT[bx][by][x][y] = (int) Math.round (sumGBlocks[bx][by][x][y]/4.0);
                                    invBDCT[bx][by][x][y] = (int) Math.round (sumBBlocks[bx][by][x][y]/4.0);

                                    // Avoiding out of RGB bound [0,255]
                                    if (invRDCT[bx][by][x][y] > 255) invRDCT[bx][by][x][y] = 255;
                                    if (invGDCT[bx][by][x][y] > 255) invGDCT[bx][by][x][y] = 255;
                                    if (invBDCT[bx][by][x][y] > 255) invBDCT[bx][by][x][y] = 255;
                                    if (invRDCT[bx][by][x][y] < 0) invRDCT[bx][by][x][y] = 0;
                                    if (invGDCT[bx][by][x][y] < 0) invGDCT[bx][by][x][y] = 0;
                                    if (invBDCT[bx][by][x][y] < 0) invBDCT[bx][by][x][y] = 0;

                                    // Set bufferedimage for displaying
                                    byte r = (byte) invRDCT[bx][by][x][y];
                                    byte g = (byte) invGDCT[bx][by][x][y];
                                    byte b = (byte) invBDCT[bx][by][x][y];
                                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                                    decImg.setRGB(bx * 8 + x, by * 8 + y, pix);
                                }
                            }
                        }
                    }
                    iterCount++;
                    System.out.println("Spectral Selection iteration "+iterCount);
                    // Display after decoding each coefficient
                    if (latency > 0) {
                        try {Thread.sleep(latency);} catch (InterruptedException e) {}
                        Icon icon = new ImageIcon(decImg);
                        lbIm1.setIcon(icon);
                        frame.repaint();
                    } else {
                        Icon icon = new ImageIcon(decImg);
                        lbIm1.setIcon(icon);
                        frame.repaint();
                    }
                }
            }
        }

        // MSB mode
        if (deliveryMode == 3) {
            int[] currRmsbUV;
            int[] currGmsbUV;
            int[] currBmsbUV;

            int[][][][] currRbitSum = new int[xBlocks][yBlocks][8][8];
            int[][][][] currGbitSum = new int[xBlocks][yBlocks][8][8];
            int[][][][] currBbitSum = new int[xBlocks][yBlocks][8][8];

            paintFrame(imgOne);
			try {Thread.sleep(2000);} catch (InterruptedException e) {}
            // For each MSB
            for (int msbIter = 0; msbIter < 8; msbIter++) {
                // For all blocks
                for (int by = 0; by < yBlocks; by++) {
                    for (int bx = 0; bx < xBlocks; bx++) {
                        // Summing up current bit's u,v
                        for (int v = 0; v < 8; v++) {
                            for (int u = 0; u < 8; u++) {
                                currRmsbUV = getMSBandNewN(deQR[bx][by][u][v]);
                                currGmsbUV = getMSBandNewN(deQG[bx][by][u][v]);
                                currBmsbUV = getMSBandNewN(deQB[bx][by][u][v]);

                                currRbitSum[bx][by][u][v] = currRbitSum[bx][by][u][v] + currRmsbUV[0];
                                deQR[bx][by][u][v] = currRmsbUV[1];

                                currGbitSum[bx][by][u][v] = currGbitSum[bx][by][u][v] + currGmsbUV[0];
                                deQG[bx][by][u][v] = currGmsbUV[1];

                                currBbitSum[bx][by][u][v] = currBbitSum[bx][by][u][v] + currBmsbUV[0];
                                deQB[bx][by][u][v] = currBmsbUV[1];

                            }
                        }

                        // For all pixels
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                // Summing up u,v to get f(x,y)
                                for (int v = 0; v < 8; v++) {
                                    for (int u = 0; u < 8; u++) {
                                        sumR = sumR + currRbitSum[bx][by][u][v]
                                                    * cosValIDCT[x][y][u][v];
                                        sumG = sumG + currGbitSum[bx][by][u][v]
                                                    * cosValIDCT[x][y][u][v];
                                        sumB = sumB + currBbitSum[bx][by][u][v]
                                                    * cosValIDCT[x][y][u][v];
                                    }
                                }

                                // Set pixel values
                                invRDCT[bx][by][x][y] = (int) Math.round (sumR/4.0);
                                invGDCT[bx][by][x][y] = (int) Math.round (sumG/4.0);
                                invBDCT[bx][by][x][y] = (int) Math.round (sumB/4.0);

                                // Avoiding out of RGB bound [0,255]
                                if (invRDCT[bx][by][x][y] > 255) invRDCT[bx][by][x][y] = 255;
                                if (invGDCT[bx][by][x][y] > 255) invGDCT[bx][by][x][y] = 255;
                                if (invBDCT[bx][by][x][y] > 255) invBDCT[bx][by][x][y] = 255;
                                if (invRDCT[bx][by][x][y] < 0) invRDCT[bx][by][x][y] = 0;
                                if (invGDCT[bx][by][x][y] < 0) invGDCT[bx][by][x][y] = 0;
                                if (invBDCT[bx][by][x][y] < 0) invBDCT[bx][by][x][y] = 0;

                                // Clear current sum for a pixel for next pixel to use
                                sumR = 0;
                                sumG = 0;
                                sumB = 0;

                                // Set bufferedimage for displaying
                                byte r = (byte) invRDCT[bx][by][x][y];
                                byte g = (byte) invGDCT[bx][by][x][y];
                                byte b = (byte) invBDCT[bx][by][x][y];
                                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                                decImg.setRGB(bx * 8 + x, by * 8 + y, pix);
                            }
                        }
                    }
                }
                iterCount++;
                System.out.println("Successive bit approximation iteration "+iterCount);
                // Display after each significant bit is processed
                if (latency > 0) {
                    try {Thread.sleep(latency);} catch (InterruptedException e) {}
                    Icon icon = new ImageIcon(decImg);
                    lbIm1.setIcon(icon);
                    frame.repaint();
                } else {
                    Icon icon = new ImageIcon(decImg);
                    lbIm1.setIcon(icon);
                    frame.repaint();
                }
            }
        }
    }


    // Returns 1. msb value as integer 2. Remaining value
    private int[] getMSBandNewN(int n) {
        int[] retVal = new int[2];
        int mask;
        int k;
        int msbValue;
        if (n<0) {
            n=-n;
            k = (int) (Math.log(n)/Math.log(2));
            msbValue = (int) Math.pow(2,k) *-1;
            retVal[0] = msbValue;
            mask = ~(-1 << k);
            retVal[1] = ((n & mask) | ((n >>> 1) & ~mask)) *-1;
            return retVal;
        } else if (n>(int)Math.pow(2,7)) {
            retVal[0] = n-n% (int) Math.pow(2,7);
            retVal[1] = n% (int) Math.pow(2,7);
            return retVal;
        } else {
            k = (int) (Math.log(n)/Math.log(2));
            msbValue = (int) Math.pow(2,k);
            retVal[0] = msbValue;
            mask = ~(-1 << k);
            retVal[1] = (n & mask) | ((n >>> 1) & ~mask);
            return retVal;
        }
    }

    // For reordering DCT coefficient in Spectral Selection mode
    private int[][][][] zigzagIntReorder(int[][][][] channel) {
        int[][][] reorderedIDCT = new int[xBlocks][yBlocks][64];
        int[][][][] ret2DIDCT = new int[xBlocks][yBlocks][8][8];
        int currX,currY;
        for (int by = 0; by < yBlocks; by++) {
            for (int bx = 0; bx < xBlocks; bx++) {
                reorderedIDCT[bx][by][0] = channel[bx][by][0][0];
                int y = 0;
                int x = 0;
                int ind = 1;
                while (ind < 64) {
                    // Move right, if can't, move down.
                    if (x+1 < 8) x++;
                    else if (y+1 < 8) y++;
                    reorderedIDCT[bx][by][ind++] = channel[bx][by][y][x];

                    // Move down along diag
                    while (x-1 >= 0 && y+1 < 8) {
                        currX = ++y;
                        currY = --x;
                        reorderedIDCT[bx][by][ind++] = channel[bx][by][currX][currY];
                    }

                    // Move down, if can't, move right
                    if (y+1 < 8) y++;
                    else if (x+1 < 8) x++;
                    reorderedIDCT[bx][by][ind++] = channel[bx][by][y][x];

                    // Move up along diag
                    while (x+1 < 8 && y-1 >= 0) {
                        currX = --y;
                        currY = ++x;
                        reorderedIDCT[bx][by][ind++] = channel[bx][by][currX][currY];
                    }
                }
                for ( int i = 0; i < 8; i++ )
                    System.arraycopy(reorderedIDCT[bx][by], (i*8), ret2DIDCT[bx][by][i], 0, 8);
            }
        }
        return ret2DIDCT;
    }

    // For reordering cosine values in Spectral Selection mode
    private double[][][][] zigzagDoubleReorder(double[][][][] channel) {
        double[][][] reorderedIDCT = new double[8][8][64];
        double[][][][] ret2DIDCT = new double[8][8][8][8];
        int currX,currY;
        for (int by = 0; by < 8; by++) {
            for (int bx = 0; bx < 8; bx++) {
                reorderedIDCT[bx][by][0] = channel[bx][by][0][0];
                int y = 0;
                int x = 0;
                int ind = 1;
                while (ind < 64) {
                    // Move right, if can't, move down.
                    if (x+1 < 8) x++;
                    else if (y+1 < 8) y++;
                    reorderedIDCT[bx][by][ind++] = channel[bx][by][y][x];

                    // Move down along diag
                    while (x-1 >= 0 && y+1 < 8) {
                        currX = ++y;
                        currY = --x;
                        reorderedIDCT[bx][by][ind++] = channel[bx][by][currX][currY];
                    }

                    // Move down, if can't, move right
                    if (y+1 < 8) y++;
                    else if (x+1 < 8) x++;
                    reorderedIDCT[bx][by][ind++] = channel[bx][by][y][x];

                    // Move up along diag
                    while (x+1 < 8 && y-1 >= 0) {
                        currX = --y;
                        currY = ++x;
                        reorderedIDCT[bx][by][ind++] = channel[bx][by][currX][currY];
                    }
                }
                for ( int i = 0; i < 8; i++ )
                    System.arraycopy(reorderedIDCT[bx][by], (i*8), ret2DIDCT[bx][by][i], 0, 8);
            }
        }
        return ret2DIDCT;
    }

    /*************************************************/
    /*      Paint Frame for decompressed Image       */
    /*************************************************/
    public void paintFrame(BufferedImage img2) {

        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        // imgTwo = scaleImg(imgOne, 960, 540);
        lbIm1 = new JLabel(new ImageIcon(img2));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

		frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void showIms(String[] args){

        // Get quantization level from second argument
        try {
            quantizeLevel = Integer.parseInt(args[1]);
            if (quantizeLevel < 0 || quantizeLevel > 7) {
                System.out.println("0 <= quantization value <= 7");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.out.println("Please input a integer quantization value");
            System.exit(1);
        }
        quantizeLevel = (int) Math.pow(2, quantizeLevel);

        // Get deleverry mode from third argument
        try {
            deliveryMode = Integer.parseInt(args[2]);
            if (deliveryMode < 1 || deliveryMode > 3) {
                System.out.println("1 <= deliveryMode value <= 3");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.out.println("Please input a integer deliveryMode mode index");
            System.exit(1);
        }

        // Get latency from fourth argument
        try {
            latency = Integer.parseInt(args[3]);
            if (latency < 0) {
                System.out.println("latency >= 0");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.out.println("Please input a integer latency");
            System.exit(1);
        }


        // Read in the specified image
        imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[0], imgOne, quantizeLevel, deliveryMode, latency);
    }

    public static void main(String[] args) {
        CompressImage ren = new CompressImage();
        ren.showIms(args);
    }
}
