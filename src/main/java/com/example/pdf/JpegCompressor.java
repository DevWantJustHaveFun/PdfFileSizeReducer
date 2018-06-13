package com.example.pdf;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSource;
import org.apache.sanselan.common.byteSources.ByteSourceArray;
import org.apache.sanselan.formats.jpeg.JpegImageParser;
import org.apache.sanselan.formats.jpeg.segments.UnknownSegment;

public final class JpegCompressor
{
    private static final String JPG_EXT = "jpg";

    private JpegCompressor()
    {

    }

    public static ByteArrayOutputStream compress(final byte[] inputBytes, final ICC_ColorSpace defaultCmykCS, final float scaleFactor,
            final float jpegCompressionFactor)
            throws IOException, ImageReadException
    {
        final BufferedImage bi = readImage(inputBytes, defaultCmykCS);

        if (bi == null) {
            return null;
        }

        final int width = (int) (bi.getWidth() * scaleFactor);
        final int height = (int) (bi.getHeight() * scaleFactor);

        if (width <= 0 || height <= 0) {
            return null;
        }

        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // scaling image
        final AffineTransform at = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
        final Graphics2D g = img.createGraphics();
        g.drawRenderedImage(bi, at);

        // jpeg compression
        final JPEGImageWriteParam jpegWriteParams = new JPEGImageWriteParam(null);
        jpegWriteParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegWriteParams.setCompressionQuality(jpegCompressionFactor);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            final ImageWriter writer = ImageIO.getImageWritersByFormatName(JPG_EXT).next();
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(img, null, null), jpegWriteParams);
        }

        return output;
    }

    private static BufferedImage readImage(final byte[] inputBytes, final ICC_ColorSpace defaultCmykCS) throws IOException, ImageReadException
    {
        try (MemoryCacheImageInputStream is = new MemoryCacheImageInputStream(new ByteArrayInputStream(inputBytes))) {

            final ImageReader reader = ImageIO.getImageReadersByFormatName(JPG_EXT).next();
            reader.setInput(is);

            if (isCmyk(reader)) {
                return readCmyk(inputBytes, defaultCmykCS, reader);
            }

            return reader.read(0);
        }
    }

    private static BufferedImage readCmyk(final byte[] inputBytes, final ICC_ColorSpace defaultCmykCS, final ImageReader reader) throws ImageReadException, IOException
    {
        final ICC_Profile profile = Sanselan.getICCProfile(new ByteArrayInputStream(inputBytes), "");

        final WritableRaster raster = (WritableRaster) reader.readRaster(0, null);

        if (isApp14Ycck(inputBytes)) {
            convertYcckToCmyk(raster);
        }

        return convertCmykToRgb(raster, profile, defaultCmykCS);
    }

    private static boolean isCmyk(final ImageReader reader) throws IOException
    {
        final Iterator<ImageTypeSpecifier> iter = reader.getImageTypes(0);
        while (iter.hasNext()) {
            final ImageTypeSpecifier type = iter.next();
            if (type.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_CMYK) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApp14Ycck(final byte[] bytes) throws IOException, ImageReadException
    {
        final JpegImageParser parser = new JpegImageParser();
        final ByteSource byteSource = new ByteSourceArray(bytes);

        final List<?> segments = parser.readSegments(byteSource, new int[] { 0xffee }, true);

        if (segments != null && !segments.isEmpty()) {
            final UnknownSegment app14Segment = (UnknownSegment) segments.get(0);
            final byte[] data = app14Segment.bytes;
            if (data.length >= 12 && data[0] == 'A' && data[1] == 'd' && data[2] == 'o' && data[3] == 'b' && data[4] == 'e') {
                final int transform = app14Segment.bytes[11] & 0xff;
                if (transform == 2) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void convertYcckToCmyk(final WritableRaster raster)
    {
        final int height = raster.getHeight();
        final int width = raster.getWidth();
        final int stride = width * 4;
        final int[] pixelRow = new int[stride];

        for (int h = 0; h < height; h++) {
            raster.getPixels(0, h, width, 1, pixelRow);

            for (int x = 0; x < stride; x += 4) {
                int y = pixelRow[x];
                final int cb = pixelRow[x + 1];
                final int cr = pixelRow[x + 2];

                int c = (int) (y + 1.402 * cr - 178.956);
                int m = (int) (y - 0.34414 * cb - 0.71414 * cr + 135.95984);
                y = (int) (y + 1.772 * cb - 226.316);

                if (c < 0) {
                    c = 0;
                } else if (c > 255) {
                    c = 255;
                }
                if (m < 0) {
                    m = 0;
                } else if (m > 255) {
                    m = 255;
                }
                if (y < 0) {
                    y = 0;
                } else if (y > 255) {
                    y = 255;
                }

                pixelRow[x] = 255 - c;
                pixelRow[x + 1] = 255 - m;
                pixelRow[x + 2] = 255 - y;
            }

            raster.setPixels(0, h, width, 1, pixelRow);
        }
    }

    private static BufferedImage convertCmykToRgb(final Raster cmykRaster, final ICC_Profile cmykProfile, final ICC_ColorSpace defaultCmykCS)
    {
        final ICC_ColorSpace cmykCS = cmykProfile != null ? new ICC_ColorSpace(cmykProfile) : defaultCmykCS;

        final BufferedImage rgbImage = new BufferedImage(cmykRaster.getWidth(), cmykRaster.getHeight(), BufferedImage.TYPE_INT_RGB);
        final WritableRaster rgbRaster = rgbImage.getRaster();
        final ColorSpace rgbCS = rgbImage.getColorModel().getColorSpace();
        final ColorConvertOp cmykToRgb = new ColorConvertOp(cmykCS, rgbCS, null);
        cmykToRgb.filter(cmykRaster, rgbRaster);

        return rgbImage;
    }
}
